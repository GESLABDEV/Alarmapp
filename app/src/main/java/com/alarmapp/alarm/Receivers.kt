package com.alarmapp.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmapp.monitor.SleepMonitorService
import com.alarmapp.util.LockScreen
import com.alarmapp.util.Notifications

/** Recibe las alarmas programadas en AlarmManager. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val fire = AlarmStore.fire(context) ?: return
                AlarmStore.clearFire(context)
                SleepMonitorService.stop(context)
                AlarmService.start(context, fire.sessionId, fire.soundAt, fire.kind)
            }
            ACTION_WAKE_CHECK -> {
                val check = AlarmStore.wakeCheck(context) ?: return
                AlarmStore.clearWakeCheck(context)
                context.getSystemService(NotificationManager::class.java)
                    .notify(Notifications.ID_WAKE_CHECK, Notifications.wakeCheck(context))
                LockScreen.wakeScreen(context)
                LockScreen.launch(context, WakeCheckActivity::class.java)
                AlarmScheduler.scheduleRecheckAlarm(context, check.sessionId)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.alarmapp.action.FIRE"
        const val ACTION_WAKE_CHECK = "com.alarmapp.action.WAKE_CHECK"
    }
}

/** Reprograma la alarma tras reiniciar, actualizar la app o cambiar la hora (RNF2). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.restore(context)
    }
}
