package com.alarmapp.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmapp.monitor.SleepMonitorService
import com.alarmapp.util.Notifications

/** Recibe las alarmas programadas en AlarmManager. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> deliverFire(context)
            ACTION_WAKE_CHECK -> deliverWakeCheck(context)
            ACTION_UPCOMING -> AlarmStore.fire(context)?.let { Notifications.postUpcoming(context, it.soundAt) }
            ACTION_SKIP -> AlarmScheduler.skipUpcoming(context)
        }
    }

    companion object {
        /**
         * Hace sonar la alarma pendiente. Lo llama AlarmManager y, como respaldo, AlarmService
         * si el sistema no entregó la alarma a tiempo. Borrar el estado primero evita que suene dos veces.
         */
        fun deliverFire(context: Context): Boolean {
            val fire = AlarmStore.fire(context) ?: return false
            // Aviso atrasado de una alarma ya reemplazada por otra posterior: se ignora.
            if (fire.fireAt - System.currentTimeMillis() > 60_000) return false
            AlarmStore.clearFire(context)
            context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_UPCOMING)
            SleepMonitorService.stop(context)
            AlarmService.start(context, fire)
            return true
        }

        fun deliverWakeCheck(context: Context): Boolean {
            val check = AlarmStore.wakeCheck(context) ?: return false
            if (check.at - System.currentTimeMillis() > 60_000) return false
            AlarmStore.clearWakeCheck(context)
            AlarmScheduler.scheduleRecheckAlarm(context, check.sessionId)
            AlarmService.check(context, check.sessionId)
            return true
        }

        const val ACTION_FIRE = "com.alarmapp.action.FIRE"
        const val ACTION_WAKE_CHECK = "com.alarmapp.action.WAKE_CHECK"
        const val ACTION_UPCOMING = "com.alarmapp.action.UPCOMING"
        const val ACTION_SKIP = "com.alarmapp.action.SKIP"
    }
}

/** Reprograma la alarma tras reiniciar, actualizar la app o cambiar la hora (RNF2). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.restore(context)
    }
}
