package com.alarmapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmapp.monitor.SleepMonitorService

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
                AlarmScheduler.scheduleRecheckAlarm(context, check.sessionId)
                AlarmService.check(context, check.sessionId)
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
