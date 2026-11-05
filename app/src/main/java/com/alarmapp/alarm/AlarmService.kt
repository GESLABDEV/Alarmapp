package com.alarmapp.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alarmapp.app
import com.alarmapp.util.LockScreen
import com.alarmapp.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la alarma que observa la pantalla de despertar. */
object AlarmController {
    enum class Phase { IDLE, LIGHT, SOUND, ESCALATED }

    data class State(
        val phase: Phase = Phase.IDLE,
        /** 0..1: intensidad del amanecer simulado en pantalla. */
        val light: Float = 0f,
        val snoozesLeft: Int = AlarmScheduler.MAX_SNOOZES,
        val sessionId: Long = -1,
        /** Se llena al apagar la alarma para abrir el reporte matutino. */
        val dismissedSessionId: Long? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun update(block: (State) -> State) = _state.update(block)
    fun consumeDismissed() = _state.update { it.copy(dismissedSessionId = null) }
}

/**
 * Despertar progresivo (RF5) y despertar garantizado (HU7):
 *  1. Luz: la pantalla sube como un amanecer unos minutos antes.
 *  2. Sonido: el volumen aumenta gradualmente durante un minuto.
 *  3. Vibración: se suma si no hay respuesta.
 *  4. Escalada: volumen al máximo del canal de alarma y vibración continua.
 * Usa el canal de alarma, por lo que suena aunque el celular esté en silencio.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sequence: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalAlarmVolume: Int? = null
    private var sessionId: Long = -1
    private var kind: AlarmStore.Kind = AlarmStore.Kind.DEADLINE
    private var soundStartedAt: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> if (sequence == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.ID_ALARM, Notifications.alarm(this, "Toca para ver la alarma"), type)
        // Pantalla apagada o bloqueada: se enciende y se abre la alarma encima del bloqueo.
        LockScreen.wakeScreen(this)
        LockScreen.launch(this, AlarmActivity::class.java)

        stopOutputs()
        sequence?.cancel()
        sessionId = intent.getLongExtra(EXTRA_SESSION, -1)
        kind = runCatching { AlarmStore.Kind.valueOf(intent.getStringExtra(EXTRA_KIND)!!) }.getOrDefault(AlarmStore.Kind.DEADLINE)
        val soundAt = intent.getLongExtra(EXTRA_SOUND_AT, System.currentTimeMillis())
        soundStartedAt = null

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmapp:alarm")
            .apply { acquire(MAX_RING_MS + 5 * 60_000L) }

        val snoozesLeft = AlarmScheduler.MAX_SNOOZES - AlarmStore.snoozeCount(this)
        AlarmController.update { AlarmController.State(phase = AlarmController.Phase.LIGHT, light = 0.05f, snoozesLeft = snoozesLeft.coerceAtLeast(0), sessionId = sessionId) }
        sequence = scope.launch { runSequence(soundAt) }
    }

    private suspend fun runSequence(soundAt: Long) {
        // 1. Luz
        val lightStart = System.currentTimeMillis()
        val lightMs = (soundAt - lightStart).coerceAtLeast(0)
        while (System.currentTimeMillis() < soundAt) {
            val p = ((System.currentTimeMillis() - lightStart).toFloat() / lightMs).coerceIn(0.05f, 1f)
            AlarmController.update { it.copy(phase = AlarmController.Phase.LIGHT, light = p) }
            delay(1_000)
        }
        // 2. Sonido gradual
        soundStartedAt = System.currentTimeMillis()
        AlarmController.update { it.copy(phase = AlarmController.Phase.SOUND, light = 1f) }
        prepareAlarmVolume()
        startPlayer()
        val escalateNow = kind == AlarmStore.Kind.GUARANTEED
        if (escalateNow) {
            escalate()
        } else {
            val steps = (RAMP_MS / 1_000).toInt()
            for (i in 0..steps) {
                val v = START_VOLUME + (1f - START_VOLUME) * i / steps
                player?.setVolume(v, v)
                delay(1_000)
            }
            // 3. Vibración si no hay respuesta
            vibrate(continuous = false)
            delay(ESCALATE_AFTER_MS - RAMP_MS)
            // 4. Despertar garantizado
            escalate()
        }
        delay(MAX_RING_MS)
        // Tras una hora sin respuesta se detiene para no agotar la batería.
        stopOutputs()
        finish(openReport = false)
    }

    private fun escalate() {
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        player?.setVolume(1f, 1f)
        vibrate(continuous = true)
        AlarmController.update { it.copy(phase = AlarmController.Phase.ESCALATED) }
    }

    private fun snooze() {
        val count = AlarmStore.snoozeCount(this)
        if (count >= AlarmScheduler.MAX_SNOOZES) return // ya no se puede posponer: sigue sonando
        AlarmStore.setSnoozeCount(this, count + 1)
        val id = sessionId
        scope.launch(Dispatchers.IO) { runCatching { app.repository.incrementSnooze(id) } }
        AlarmScheduler.scheduleSnooze(this, id)
        finish(openReport = false)
    }

    private fun dismiss() {
        val id = sessionId
        val alarmTime = soundStartedAt ?: System.currentTimeMillis()
        AlarmScheduler.cancelFire(this)
        if (kind != AlarmStore.Kind.GUARANTEED) AlarmScheduler.scheduleWakeCheck(this, id)
        AlarmStore.setSnoozeCount(this, 0)
        AlarmStore.setActiveSession(this, null, AlarmStore.soundUri(this))
        AlarmScheduler.armDailyAfterWake(this)
        // Cierra la noche y calcula el reporte (RF6). Si la base no está disponible
        // (celular bloqueado tras reinicio), el reporte se completa al abrir la app.
        val appRef = app
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { appRef.repository.finishSession(id, System.currentTimeMillis(), alarmTime) }
        }
        finish(openReport = true)
    }

    private fun finish(openReport: Boolean) {
        sequence?.cancel()
        sequence = null
        stopOutputs()
        AlarmController.update {
            AlarmController.State(dismissedSessionId = if (openReport) sessionId else null)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- salidas

    private fun prepareAlarmVolume() {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        if (originalAlarmVolume == null) originalAlarmVolume = current
        // Si el usuario dejó la alarma muy baja o en cero, se sube a un mínimo audible.
        val minimum = (max * 0.4f).toInt().coerceAtLeast(1)
        if (current < minimum) am.setStreamVolume(AudioManager.STREAM_ALARM, minimum, 0)
    }

    private fun startPlayer() {
        val candidates = listOfNotNull(
            AlarmStore.soundUri(this)?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    setVolume(START_VOLUME, START_VOLUME)
                    prepare()
                    start()
                }
            }.getOrNull()
            if (mp != null) { player = mp; return }
        }
        // Sin ningún sonido disponible, la vibración continua sigue siendo el respaldo.
        vibrate(continuous = true)
    }

    private fun vibrate(continuous: Boolean) {
        val v = vibrator ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator = v
        if (!v.hasVibrator()) return
        val pattern = if (continuous) longArrayOf(0, 1_000, 100) else longArrayOf(0, 600, 900)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        v.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun stopOutputs() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        originalAlarmVolume?.let { vol ->
            runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) }
        }
        originalAlarmVolume = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopOutputs()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.alarmapp.alarm.START"
        const val ACTION_SNOOZE = "com.alarmapp.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.alarmapp.alarm.DISMISS"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_SOUND_AT = "sound_at"
        private const val EXTRA_KIND = "kind"

        private const val START_VOLUME = 0.1f
        private const val RAMP_MS = 60_000L
        private const val ESCALATE_AFTER_MS = 120_000L
        private const val MAX_RING_MS = 60 * 60_000L

        fun start(context: Context, sessionId: Long, soundAt: Long, kind: AlarmStore.Kind) {
            val i = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_SOUND_AT, soundAt)
                .putExtra(EXTRA_KIND, kind.name)
            ContextCompat.startForegroundService(context, i)
        }

        fun command(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
