package com.alarmapp.monitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.alarmapp.alarm.AlarmScheduler
import com.alarmapp.app
import com.alarmapp.core.sleep.Epoch
import com.alarmapp.core.sleep.PhaseEstimator
import com.alarmapp.core.sleep.SleepStage
import com.alarmapp.core.sleep.SmartWake
import com.alarmapp.util.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitoreo nocturno (RF2, HU2): servicio en primer plano con notificación fija,
 * activo con la pantalla apagada. Cada 30 s guarda indicadores, estima la fase (RF3)
 * y, dentro de la ventana, adelanta la alarma si detecta fase ligera (RF4).
 */
class SleepMonitorService : LifecycleService() {

    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> begin(
                sessionId = intent.getLongExtra(EXTRA_SESSION, -1),
                deadline = intent.getLongExtra(EXTRA_DEADLINE, 0),
                window = intent.getIntExtra(EXTRA_WINDOW, 30),
                cycle = intent.getDoubleExtra(EXTRA_CYCLE, 90.0),
            )
            ACTION_STOP -> end()
            else -> stopSelf() // reinicio del proceso sin datos: la alarma sigue programada aparte
        }
        return START_NOT_STICKY
    }

    private fun begin(sessionId: Long, deadline: Long, window: Int, cycle: Double) {
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or (if (micGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && micGranted -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else -> 0
        }
        ServiceCompat.startForeground(this, Notifications.ID_MONITOR, Notifications.monitor(this), type)

        // La CPU no debe dormirse mientras se mide (la pantalla sí se apaga).
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmapp:monitor")
            .apply { acquire(MAX_SESSION_MS) }

        _running.value = sessionId
        val repo = app.repository
        val estimator = PhaseEstimator(cycle)
        val source: SleepDataSource = PhoneSensorSource(this) { _mic.value = it }
        val night = ArrayList<Epoch>()
        val stages = ArrayList<SleepStage>()
        val decisionDeadline = deadline - AlarmScheduler.LIGHT_LEAD_MS

        job?.cancel()
        job = lifecycleScope.launch {
            source.epochs().collect { epoch ->
                night += epoch
                val stage = estimator.classifyLatest(night)
                stages += stage
                runCatching { repo.addIndicator(sessionId, epoch, stage) }

                val now = System.currentTimeMillis()
                if (SmartWake.shouldWakeNow(now, decisionDeadline, window, stages.takeLast(2))) {
                    // Fase ligera dentro de la ventana: se adelanta la alarma. Se usa
                    // AlarmManager para que el arranque sea igual de confiable que a la hora límite.
                    val soundAt = minOf(now + AlarmScheduler.LIGHT_LEAD_MS, deadline)
                    AlarmScheduler.scheduleSmartWake(this@SleepMonitorService, sessionId, soundAt, deadline)
                    end()
                }
            }
        }
    }

    private fun end() {
        job?.cancel()
        job = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        _running.value = null
        _mic.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        _running.value = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.alarmapp.monitor.START"
        private const val ACTION_STOP = "com.alarmapp.monitor.STOP"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_DEADLINE = "deadline"
        private const val EXTRA_WINDOW = "window"
        private const val EXTRA_CYCLE = "cycle"
        private const val MAX_SESSION_MS = 14 * 60 * 60_000L

        private val _running = MutableStateFlow<Long?>(null)
        /** Id de la sesión que se está midiendo, o null. */
        val running: StateFlow<Long?> = _running.asStateFlow()

        private val _mic = MutableStateFlow<PhoneSensorSource.MicState?>(null)
        /** Estado del micrófono durante la medición, o null si no se está midiendo. */
        val mic: StateFlow<PhoneSensorSource.MicState?> = _mic.asStateFlow()

        /** Debe llamarse con la app en primer plano (al presionar "Me voy a dormir"). */
        fun start(context: Context, sessionId: Long, deadline: Long, window: Int, cycleMinutes: Double) {
            val i = Intent(context, SleepMonitorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_DEADLINE, deadline)
                .putExtra(EXTRA_WINDOW, window)
                .putExtra(EXTRA_CYCLE, cycleMinutes)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            if (_running.value == null) return
            context.startService(Intent(context, SleepMonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
