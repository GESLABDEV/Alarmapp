package com.alarmapp.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
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
    enum class Phase { IDLE, LIGHT, SOUND, ESCALATED, CHECK }

    data class State(
        val phase: Phase = Phase.IDLE,
        /** 0..1: intensidad del amanecer simulado en pantalla. */
        val light: Float = 0f,
        val snoozesLeft: Int = AlarmScheduler.MAX_SNOOZES,
        val sessionId: Long = -1,
        /** La pantalla destella si la persona no responde. */
        val flashing: Boolean = false,
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
 *  3. Vibración continua desde que empieza el sonido; destellos si no hay respuesta.
 *  4. Escalada: volumen al máximo del canal de alarma y vibración fuerte.
 * Usa el canal de alarma, por lo que suena aunque el celular esté en silencio.
 */
private interface AlarmSound {
    fun setVolume(v: Float)
    fun stop()
}

class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sequence: Job? = null
    private var sound: AlarmSound? = null
    private var deadline: Long = 0
    private var screenOffRegistered = false
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalAlarmVolume: Int? = null
    private var sessionId: Long = -1
    private var kind: AlarmStore.Kind = AlarmStore.Kind.DEADLINE
    private var soundStartedAt: Long? = null
    private var vibrationJob: Job? = null
    private var savedFilter: Int? = null
    private var savedRinger: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            ACTION_CHECK -> check(intent.getLongExtra(EXTRA_SESSION, -1))
            ACTION_CHECK_OK -> if (AlarmController.state.value.phase == AlarmController.Phase.CHECK) finish(openReport = false)
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

        getSystemService(android.app.NotificationManager::class.java).cancel(Notifications.ID_WAKE_CHECK)
        stopOutputs()
        sequence?.cancel()
        overrideSilence()
        sessionId = intent.getLongExtra(EXTRA_SESSION, -1)
        kind = runCatching { AlarmStore.Kind.valueOf(intent.getStringExtra(EXTRA_KIND)!!) }.getOrDefault(AlarmStore.Kind.DEADLINE)
        val soundAt = intent.getLongExtra(EXTRA_SOUND_AT, System.currentTimeMillis())
        deadline = intent.getLongExtra(EXTRA_DEADLINE, soundAt)
        watchScreenOff()
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
        startPlayer(START_VOLUME)
        // 3. Vibración continua desde que empieza a sonar
        startVibration(strong = false)
        val escalateNow = kind == AlarmStore.Kind.GUARANTEED
        if (escalateNow) {
            escalate()
        } else {
            val steps = (RAMP_MS / 1_000).toInt()
            for (i in 0..steps) {
                val v = START_VOLUME + (1f - START_VOLUME) * i / steps
                sound?.setVolume(v)
                delay(1_000)
            }
            // Sin respuesta tras subir el volumen: la pantalla empieza a destellar.
            AlarmController.update { it.copy(flashing = true) }
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
        sound?.setVolume(1f)
        startVibration(strong = true)
        AlarmController.update { it.copy(phase = AlarmController.Phase.ESCALATED, flashing = true) }
    }

    /**
     * HU7: "¿Sigues despierto?" con sonido y vibración (también en silencio o No molestar).
     * Si nadie responde, la alarma de verificación vuelve a disparar la alarma completa.
     */
    private fun check(id: Long) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.ID_WAKE_CHECK, Notifications.wakeCheck(this), type)
        LockScreen.wakeScreen(this)
        LockScreen.launch(this, WakeCheckActivity::class.java)
        stopOutputs()
        sequence?.cancel()
        overrideSilence()
        sessionId = id
        watchScreenOff()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmapp:check")
            .apply { acquire(AlarmScheduler.WAKE_CHECK_TIMEOUT_MS + 30_000L) }
        AlarmController.update { AlarmController.State(phase = AlarmController.Phase.CHECK, sessionId = id) }
        prepareAlarmVolume()
        startPlayer(CHECK_VOLUME)
        startVibration(strong = false)
        sequence = scope.launch {
            delay(AlarmScheduler.WAKE_CHECK_TIMEOUT_MS + 30_000L)
            finish(openReport = false) // respaldo: la alarma completa ya debió dispararse
        }
    }

    private fun snooze() {
        val count = AlarmStore.snoozeCount(this)
        if (count >= AlarmScheduler.MAX_SNOOZES) return // ya no se puede posponer: sigue sonando
        AlarmStore.setSnoozeCount(this, count + 1)
        val id = sessionId
        scope.launch(Dispatchers.IO) { runCatching { app.repository.incrementSnooze(id) } }
        AlarmScheduler.scheduleSnooze(this, id, deadline)
        finish(openReport = false)
    }

    private fun dismiss() {
        val id = sessionId
        val alarmTime = soundStartedAt ?: System.currentTimeMillis()
        AlarmScheduler.cancelFire(this)
        if (kind != AlarmStore.Kind.GUARANTEED) AlarmScheduler.scheduleWakeCheck(this, id)
        AlarmStore.setSnoozeCount(this, 0)
        AlarmStore.setActiveSession(this, null)
        AlarmStore.setLastDeadline(this, deadline)
        AlarmScheduler.armAfterWake(this)
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
        unwatchScreenOff()
        stopOutputs()
        restoreSilence()
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

    /**
     * Reproduce el tono elegido. Si el sistema no deja leerlo directamente (pasa en Xiaomi
     * con algunos tonos), se reproduce como tono del sistema, que sí tiene acceso. Solo si
     * eso también falla se usa el tono de alarma predeterminado.
     */
    private fun startPlayer(volume: Float) {
        val chosen = AlarmStore.soundUri(this)?.let(Uri::parse)
        val defaults = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        val attempts = buildList<() -> AlarmSound?> {
            if (chosen != null) {
                add { mediaSound(chosen, volume) }
                add { ringtoneSound(chosen, volume) }
            }
            defaults.forEach { uri -> add { mediaSound(uri, volume) } }
            defaults.firstOrNull()?.let { uri -> add { ringtoneSound(uri, volume) } }
        }
        for (attempt in attempts) {
            val s = runCatching { attempt() }.getOrNull()
            if (s != null) { sound = s; return }
        }
        // Sin ningún sonido disponible, la vibración fuerte sigue siendo el respaldo.
        startVibration(strong = true)
    }

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun mediaSound(uri: Uri, volume: Float): AlarmSound {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(alarmAttributes)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            throw e
        }
        return object : AlarmSound {
            override fun setVolume(v: Float) = mp.setVolume(v, v)
            override fun stop() { runCatching { mp.stop() }; mp.release() }
        }
    }

    private fun ringtoneSound(uri: Uri, volume: Float): AlarmSound? {
        val r = RingtoneManager.getRingtone(this, uri) ?: return null
        r.audioAttributes = alarmAttributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
            r.volume = volume
        }
        r.play()
        return object : AlarmSound {
            override fun setVolume(v: Float) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.volume = v }
            override fun stop() = r.stop()
        }
    }

    // ---------------------------------------------------------------- pantalla

    /**
     * Si la persona presiona el botón de bloqueo mientras suena, Android apaga la pantalla.
     * Se vuelve a encender y a mostrar la alarma (con sus destellos) hasta que la apague.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val phase = AlarmController.state.value.phase
            if (phase == AlarmController.Phase.IDLE) return
            scope.launch {
                delay(400)
                LockScreen.wakeScreen(this@AlarmService)
                LockScreen.launch(
                    this@AlarmService,
                    if (phase == AlarmController.Phase.CHECK) WakeCheckActivity::class.java else AlarmActivity::class.java,
                )
            }
        }
    }

    private fun watchScreenOff() {
        if (screenOffRegistered) return
        ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenOffRegistered = true
    }

    private fun unwatchScreenOff() {
        if (!screenOffRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenOffRegistered = false
    }

    /**
     * Vibración continua: se repite en un ciclo propio en lugar de confiar en el patrón
     * repetido del sistema, que algunos fabricantes (p. ej. Xiaomi) cortan tras unos pulsos.
     */
    private fun startVibration(strong: Boolean) {
        val v = vibrator ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator = v
        if (!v.hasVibrator()) return
        vibrationJob?.cancel()
        // Golpes a máxima intensidad en lugar de una vibración pareja: la piel se acostumbra
        // rápido a un zumbido continuo y deja de notarlo; los golpes con pausas cortas se
        // sienten mucho más fuertes con el mismo motor. Al escalar, los golpes son más largos y seguidos.
        val timings = if (strong) longArrayOf(0, 900, 70, 900, 70, 900, 70)
        else longArrayOf(0, 450, 110, 450, 110, 450, 110, 1100, 250)
        val max = if (v.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) max else 0 }
        val cycle = timings.sum()
        vibrationJob = scope.launch {
            while (true) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                }
                delay(cycle)
            }
        }
    }

    /**
     * Silencio y No molestar: si el usuario dio acceso a "No molestar", se desactiva
     * mientras suena y se restaura exactamente como estaba al terminar.
     */
    private fun overrideSilence() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching {
            val filter = nm.currentInterruptionFilter
            if (filter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                if (savedFilter == null) savedFilter = filter
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            val am = getSystemService(AudioManager::class.java)
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                if (savedRinger == null) savedRinger = am.ringerMode
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        }
    }

    private fun restoreSilence() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        runCatching { savedRinger?.let { getSystemService(AudioManager::class.java).ringerMode = it } }
        runCatching { savedFilter?.let { nm.setInterruptionFilter(it) } }
        savedRinger = null
        savedFilter = null
    }

    private fun stopOutputs() {
        sound?.runCatching { stop() }
        sound = null
        vibrationJob?.cancel()
        vibrationJob = null
        vibrator?.cancel()
        originalAlarmVolume?.let { vol ->
            runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, vol, 0) }
        }
        originalAlarmVolume = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        unwatchScreenOff()
        stopOutputs()
        restoreSilence()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.alarmapp.alarm.START"
        const val ACTION_SNOOZE = "com.alarmapp.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.alarmapp.alarm.DISMISS"
        const val ACTION_CHECK = "com.alarmapp.alarm.CHECK"
        const val ACTION_CHECK_OK = "com.alarmapp.alarm.CHECK_OK"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_SOUND_AT = "sound_at"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_DEADLINE = "deadline"

        private const val START_VOLUME = 0.1f
        private const val CHECK_VOLUME = 0.6f
        private const val RAMP_MS = 60_000L
        private const val ESCALATE_AFTER_MS = 120_000L
        private const val MAX_RING_MS = 60 * 60_000L

        fun start(context: Context, fire: AlarmStore.Fire) {
            val i = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, fire.sessionId)
                .putExtra(EXTRA_SOUND_AT, fire.soundAt)
                .putExtra(EXTRA_KIND, fire.kind.name)
                .putExtra(EXTRA_DEADLINE, fire.deadline)
            ContextCompat.startForegroundService(context, i)
        }

        fun check(context: Context, sessionId: Long) {
            val i = Intent(context, AlarmService::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_SESSION, sessionId)
            ContextCompat.startForegroundService(context, i)
        }

        fun command(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
