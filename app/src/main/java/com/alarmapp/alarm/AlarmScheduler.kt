package com.alarmapp.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.ui.MainActivity
import com.alarmapp.util.Notifications

/**
 * Programación confiable (RNF2). Usa AlarmManager.setAlarmClock: es el mecanismo que
 * Android reserva a los despertadores, atraviesa el modo Doze y no depende de que la app
 * esté abierta. Lo programado se copia en [AlarmStore] (almacenamiento disponible antes
 * de desbloquear) para reprogramarlo tras un reinicio.
 *
 * Siempre hay como máximo UNA alarma pendiente en el sistema: la de la noche en curso o,
 * si no hay noche, la más próxima de las alarmas guardadas.
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
    /** Aviso "Próxima alarma" con esta anticipación. */
    const val UPCOMING_LEAD_MS = 2 * 60 * 60_000L

    const val NO_SESSION = -1L

    private const val RC_FIRE = 100
    private const val RC_WAKE_CHECK = 101
    private const val RC_UPCOMING = 102
    private const val MISSED_GRACE_MS = 60 * 60_000L

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    // ---------------------------------------------------------------- programar

    /** Hora límite: la secuencia de luz arranca [LIGHT_LEAD_MS] antes. */
    fun scheduleDeadline(context: Context, sessionId: Long, deadlineMs: Long) {
        val fireAt = maxOf(System.currentTimeMillis() + 1_000, deadlineMs - LIGHT_LEAD_MS)
        scheduleFire(context, AlarmStore.Fire(sessionId, fireAt, deadlineMs, AlarmStore.Kind.DEADLINE, deadlineMs))
    }

    /** La alarma inteligente encontró fase ligera: adelanta el despertar. */
    fun scheduleSmartWake(context: Context, sessionId: Long, soundAt: Long, deadline: Long) {
        scheduleFire(context, AlarmStore.Fire(sessionId, System.currentTimeMillis() + 1_000, soundAt, AlarmStore.Kind.DEADLINE, deadline))
    }

    fun scheduleSnooze(context: Context, sessionId: Long, deadline: Long) {
        val at = System.currentTimeMillis() + SNOOZE_MS
        scheduleFire(context, AlarmStore.Fire(sessionId, at, at, AlarmStore.Kind.SNOOZE, deadline))
    }

    /** Vuelve a sonar si nadie responde "¿Sigues despierto?". */
    fun scheduleRecheckAlarm(context: Context, sessionId: Long) {
        val at = System.currentTimeMillis() + WAKE_CHECK_TIMEOUT_MS
        scheduleFire(context, AlarmStore.Fire(sessionId, at, at, AlarmStore.Kind.GUARANTEED, AlarmStore.lastDeadline(context)))
    }

    /** Prueba desde Ajustes: suena en un minuto y luego se recupera la alarma guardada. */
    fun scheduleTest(context: Context) {
        val at = System.currentTimeMillis() + 60_000
        scheduleFire(context, AlarmStore.Fire(NO_SESSION, at, at, AlarmStore.Kind.TEST, at))
    }

    fun scheduleWakeCheck(context: Context, sessionId: Long) {
        val at = System.currentTimeMillis() + WAKE_CHECK_DELAY_MS
        AlarmStore.saveWakeCheck(context, sessionId, at)
        setAlarmClock(context, at, wakeCheckIntent(context))
    }

    private fun scheduleFire(context: Context, f: AlarmStore.Fire) {
        AlarmStore.saveFire(context, f)
        setAlarmClock(context, f.fireAt, fireIntent(context))
        scheduleUpcoming(context, f)
    }

    // ---------------------------------------------------------------- alarmas guardadas

    /**
     * Programa la más próxima de las alarmas guardadas, si no hay ya algo pendiente.
     * Así suena aunque el usuario no presione "Me voy a dormir" (sin medición).
     */
    fun armNext(context: Context, from: Long = System.currentTimeMillis()) {
        if (AlarmStore.fire(context) != null) return
        val next = AlarmTimes.nextOf(AlarmStore.alarms(context), from) ?: return
        scheduleDeadline(context, NO_SESSION, next)
    }

    /** Tras despertar: la siguiente es la posterior a la que acaba de sonar (no se salta ninguna). */
    fun armAfterWake(context: Context) {
        val from = maxOf(System.currentTimeMillis(), AlarmStore.lastDeadline(context) + 60_000)
        armNext(context, from)
    }

    /** Las alarmas guardadas cambiaron: se recalcula la pendiente si no hay noche en curso. */
    fun refresh(context: Context) {
        if (AlarmStore.activeSession(context) != null) return
        val f = AlarmStore.fire(context)
        val replaceable = f == null || (f.sessionId == NO_SESSION && f.kind == AlarmStore.Kind.DEADLINE)
        if (!replaceable) return
        cancelFire(context)
        armNext(context)
    }

    /** "Omitir esta vez" desde el aviso de próxima alarma. */
    fun skipUpcoming(context: Context) {
        val f = AlarmStore.fire(context) ?: return
        if (f.sessionId != NO_SESSION) return
        cancelFire(context)
        AlarmStore.setLastDeadline(context, f.deadline)
        armAfterWake(context)
    }

    // ---------------------------------------------------------------- cancelar

    fun cancelFire(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(fireIntent(context))
        am.cancel(upcomingIntent(context))
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_UPCOMING)
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

    // ---------------------------------------------------------------- reinicio

    /** Tras reinicio o cambio de hora: vuelve a programar lo que estaba pendiente. */
    fun restore(context: Context) {
        val now = System.currentTimeMillis()
        AlarmStore.fire(context)?.let { f ->
            when {
                f.fireAt > now -> {
                    setAlarmClock(context, f.fireAt, fireIntent(context))
                    scheduleUpcoming(context, f)
                }
                // El teléfono estaba apagado a la hora de la alarma: si fue hace poco, sonar ya.
                now - f.soundAt < MISSED_GRACE_MS ->
                    scheduleFire(context, f.copy(fireAt = now + 1_000, soundAt = now + 1_000, kind = AlarmStore.Kind.GUARANTEED))
                else -> {
                    AlarmStore.clearFire(context)
                    AlarmStore.setLastDeadline(context, f.deadline)
                }
            }
        }
        AlarmStore.wakeCheck(context)?.let { w ->
            if (w.at > now) setAlarmClock(context, w.at, wakeCheckIntent(context))
            else scheduleRecheckAlarm(context, w.sessionId)
        }
        armAfterWake(context)
    }

    // ---------------------------------------------------------------- aviso previo

    /** Aviso silencioso "Próxima alarma" unas horas antes (solo alarmas sin noche en curso). */
    private fun scheduleUpcoming(context: Context, f: AlarmStore.Fire) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(upcomingIntent(context))
        if (f.sessionId != NO_SESSION || f.kind != AlarmStore.Kind.DEADLINE) return
        val at = f.soundAt - UPCOMING_LEAD_MS
        if (at <= System.currentTimeMillis()) {
            Notifications.postUpcoming(context, f.soundAt)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, upcomingIntent(context))
        }
    }

    // ---------------------------------------------------------------- utilidades

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

    private fun broadcast(context: Context, code: Int, action: String) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fireIntent(context: Context) = broadcast(context, RC_FIRE, AlarmReceiver.ACTION_FIRE)
    private fun wakeCheckIntent(context: Context) = broadcast(context, RC_WAKE_CHECK, AlarmReceiver.ACTION_WAKE_CHECK)
    private fun upcomingIntent(context: Context) = broadcast(context, RC_UPCOMING, AlarmReceiver.ACTION_UPCOMING)
    fun skipIntent(context: Context) = broadcast(context, RC_UPCOMING + 1, AlarmReceiver.ACTION_SKIP)
}
