package com.alarmapp.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmapp.alarm.AlarmScheduler
import com.alarmapp.alarm.AlarmStore
import com.alarmapp.alarm.AlarmSync
import com.alarmapp.app
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.core.sleep.CycleCalculator
import com.alarmapp.core.sleep.SleepProfile
import com.alarmapp.data.AlarmEntity
import com.alarmapp.data.AlarmMode
import com.alarmapp.data.ProfileEntity
import com.alarmapp.data.SleepSessionEntity
import com.alarmapp.monitor.SleepMonitorService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val profile: SleepProfile = SleepProfile(),
    /** Alarmas activas. */
    val alarms: List<AlarmEntity> = emptyList(),
    val totalAlarms: Int = 0,
    val lastNight: SleepSessionEntity? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = application.app.repository

    val state: StateFlow<HomeState> = combine(
        repo.observeProfile(), repo.observeAlarms(), repo.observeLastFinished(),
    ) { p: ProfileEntity?, all: List<AlarmEntity>, s: SleepSessionEntity? ->
        HomeState(
            profile = p?.let { SleepProfile(it.avgCycleMinutes, it.avgLatencyMinutes, it.windowMinutes) } ?: SleepProfile(),
            alarms = all.filter { it.active },
            totalAlarms = all.size,
            lastNight = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        val active = SleepMonitorService.running.value ?: AlarmStore.activeSession(application)
        viewModelScope.launch {
            // Noches que no se pudieron cerrar al apagar la alarma se cierran aquí.
            runCatching { repo.closeDanglingSessions(active) }
            // Mantiene sincronizada la alarma programada con las alarmas guardadas.
            runCatching { AlarmSync.apply(application, repo.observeAlarms().first()) }
        }
    }

    fun recommendedWakeTimes(now: Long = System.currentTimeMillis()): List<Long> =
        CycleCalculator.recommendedWakeTimes(now, state.value.profile)

    fun nextTime(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long =
        AlarmTimes.next(alarm.minuteOfDay, alarm.days, now)

    /** La alarma activa que sonará primero. */
    fun nextAlarm(now: Long = System.currentTimeMillis()): Pair<AlarmEntity, Long>? =
        state.value.alarms.map { it to nextTime(it, now) }.minByOrNull { it.second }

    /**
     * RF1: marca el inicio exacto, programa la hora límite (RNF2) e inicia el monitoreo.
     * Devuelve el id de la sesión.
     */
    suspend fun startNight(context: Context, mode: AlarmMode, deadline: Long, alarmId: Long?): Long {
        val start = System.currentTimeMillis()
        val profile = repo.profile()
        val sessionId = repo.startSession(mode, alarmId, start, deadline, profile.windowMinutes)

        AlarmStore.setActiveSession(context, sessionId)
        AlarmStore.setSnoozeCount(context, 0)
        AlarmScheduler.cancelAll(context)
        AlarmScheduler.scheduleDeadline(context, sessionId, deadline)
        SleepMonitorService.start(context, sessionId, deadline, profile.windowMinutes, profile.avgCycleMinutes)
        return sessionId
    }

    companion object {
        fun nextOccurrence(minuteOfDay: Int, now: Long): Long = AlarmTimes.next(minuteOfDay, AlarmTimes.ALL_DAYS, now)
    }
}
