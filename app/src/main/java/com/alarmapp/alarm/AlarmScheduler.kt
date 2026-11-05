package com.alarmapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alarmapp.ui.MainActivity
import java.util.Calendar

/**
 * Programación confiable (RNF2). Usa AlarmManager.setAlarmClock: es el mecanismo que
 * Android reserva a los despertadores, atraviesa el modo Doze y no depende de que la app
 * esté abierta. Lo programado se copia en [AlarmStore] (almacenamiento disponible antes
 * de desbloquear) para reprogramarlo tras un reinicio.
 */
object AlarmScheduler {

    /** La luz empieza a subir estos minutos antes de que suene (RF5). */
    const val LIGHT_LEAD_MS = 2 * 60_000L
    const val SNOOZE_MS = 5 * 60_000L
    const val MAX_SNOOZES = 2
    /** "¿Sigues despierto?" unos minutos después de apagarla (HU7). */
    const val WAKE_CHECK_DELAY_MS = 3 * 60_000L
    /** Tiempo para responder antes de que la alarma vuelva a sonar. */
    const val WAKE_CHECK_TIMEOUT_MS = 60_000L

    private const val RC_FIRE = 100
    private const val RC_WAKE_CHECK = 101

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Hora límite de la noche: la secuencia de luz arranca [LIGHT_LEAD_MS] antes. */
    fun scheduleDeadline(context: Context, sessionId: Long, deadlineMs: Long) {
        val fireAt = maxOf(System.currentTimeMillis() + 1_000, deadlineMs - LIGHT_LEAD_MS)
        scheduleFire(context, sessionId, fireAt, deadlineMs, AlarmStore.Kind.DEADLINE)
    }

    /**
     * Alarma diaria de respaldo: si el usuario guardó una hora fija y olvida presionar
     * "Me voy a dormir", la alarma igual suena (sin medición). Al iniciar la noche se
     * reemplaza por la alarma de la sesión.
     */
    fun armDaily(context: Context, from: Long = System.currentTimeMillis()) {
        val minute = AlarmStore.dailyMinute(context) ?: return
        if (AlarmStore.fire(context) != null) return
        scheduleDeadline(context, NO_SESSION, nextOccurrence(minute, from))
    }

    /** Tras despertar: la siguiente alarma diaria es la de mañana, aunque se haya despertado antes de la hora. */
    fun armDailyAfterWake(context: Context) = armDaily(context, System.currentTimeMillis() + 2 * 60 * 60_000L)

    fun nextOccurrence(minuteOfDay: Int, now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now + 60_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    const val NO_SESSION = -1L

    fun scheduleSnooze(context: Context, sessionId: Long) {
        val at = System.currentTimeMillis() + SNOOZE_MS
        scheduleFire(context, sessionId, at, at, AlarmStore.Kind.SNOOZE)
    }

    /** Vuelve a sonar si nadie responde "¿Sigues despierto?". */
    fun scheduleRecheckAlarm(context: Context, sessionId: Long) {
        val at = System.currentTimeMillis() + WAKE_CHECK_TIMEOUT_MS
        scheduleFire(context, sessionId, at, at, AlarmStore.Kind.GUARANTEED)
    }

    fun scheduleWakeCheck(context: Context, sessionId: Long) {
        val at = System.currentTimeMillis() + WAKE_CHECK_DELAY_MS
        AlarmStore.saveWakeCheck(context, sessionId, at)
        setAlarmClock(context, at, wakeCheckIntent(context))
    }

    internal fun scheduleFire(context: Context, sessionId: Long, fireAt: Long, soundAt: Long, kind: AlarmStore.Kind) {
        AlarmStore.saveFire(context, sessionId, fireAt, soundAt, kind)
        setAlarmClock(context, fireAt, fireIntent(context))
    }

    fun cancelFire(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context))
        AlarmStore.clearFire(context)
    }

    fun cancelWakeCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(wakeCheckIntent(context))
        AlarmStore.clearWakeCheck(context)
    }

    fun cancelAll(context: Context) {
        cancelFire(context)
        cancelWakeCheck(context)
    }

    /** Tras reinicio o cambio de hora: vuelve a programar lo que estaba pendiente. */
    fun restore(context: Context) {
        val now = System.currentTimeMillis()
        AlarmStore.fire(context)?.let { f ->
            when {
                f.fireAt > now -> setAlarmClock(context, f.fireAt, fireIntent(context))
                // El teléfono estaba apagado a la hora de la alarma: si fue hace poco, sonar ya.
                now - f.soundAt < MISSED_GRACE_MS -> scheduleFire(context, f.sessionId, now + 1_000, now + 1_000, AlarmStore.Kind.GUARANTEED)
                else -> AlarmStore.clearFire(context)
            }
        }
        AlarmStore.wakeCheck(context)?.let { w ->
            if (w.at > now) setAlarmClock(context, w.at, wakeCheckIntent(context))
            else AlarmScheduler.scheduleRecheckAlarm(context, w.sessionId)
        }
        armDaily(context)
    }

    private const val MISSED_GRACE_MS = 60 * 60_000L

    private fun setAlarmClock(context: Context, at: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(context)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            // Sin permiso de alarma exacta: lo más cercano que permite Android.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun fireIntent(context: Context) = PendingIntent.getBroadcast(
        context, RC_FIRE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun wakeCheckIntent(context: Context) = PendingIntent.getBroadcast(
        context, RC_WAKE_CHECK,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_WAKE_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
