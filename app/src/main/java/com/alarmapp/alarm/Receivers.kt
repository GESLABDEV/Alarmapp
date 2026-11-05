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
            ACTION_FIRE -> {
                val fire = AlarmStore.fire(context) ?: return
                AlarmStore.clearFire(context)
                context.getSystemService(NotificationManager::class.java).cancel(Notifications.ID_UPCOMING)
                SleepMonitorService.stop(context)
                AlarmService.start(context, fire)
            }
            ACTION_WAKE_CHECK -> {
                val check = AlarmStore.wakeCheck(context) ?: return
                AlarmStore.clearWakeCheck(context)
                AlarmScheduler.scheduleRecheckAlarm(context, check.sessionId)
                AlarmService.check(context, check.sessionId)
            }
            ACTION_UPCOMING -> AlarmStore.fire(context)?.let { Notifications.postUpcoming(context, it.soundAt) }
            ACTION_SKIP -> AlarmScheduler.skipUpcoming(context)
        }
    }

    companion object {
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
