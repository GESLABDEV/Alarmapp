package com.alarmapp.alarm

import android.content.Context
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.data.AlarmEntity

/** Copia las alarmas activas al almacenamiento de arranque y reprograma la más próxima. */
object AlarmSync {
    fun apply(context: Context, alarms: List<AlarmEntity>) {
        AlarmStore.setAlarms(
            context,
            alarms.filter { it.active }.map { AlarmTimes.Spec(it.minuteOfDay, it.days) },
        )
        AlarmScheduler.refresh(context)
    }
}
