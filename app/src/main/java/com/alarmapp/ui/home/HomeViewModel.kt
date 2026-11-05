package com.alarmapp.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmapp.alarm.AlarmScheduler
import com.alarmapp.alarm.AlarmStore
import com.alarmapp.app
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val profile: SleepProfile = SleepProfile(),
    val alarm: AlarmEntity? = null,
    val lastNight: SleepSessionEntity? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = application.app.repository

    val state: StateFlow<HomeState> = combine(
        repo.observeProfile(), repo.observeActiveAlarm(), repo.observeLastFinished(),
    ) { p: ProfileEntity?, a: AlarmEntity?, s: SleepSessionEntity? ->
        HomeState(
            profile = p?.let { SleepProfile(it.avgCycleMinutes, it.avgLatencyMinutes, it.windowMinutes) } ?: SleepProfile(),
            alarm = a,
            lastNight = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        // Noches que no se pudieron cerrar al apagar la alarma se cierran aquí.
        val active = SleepMonitorService.running.value ?: AlarmStore.activeSession(application)
        viewModelScope.launch { runCatching { repo.closeDanglingSessions(active) } }
    }

    fun recommendedWakeTimes(now: Long = System.currentTimeMillis()): List<Long> =
        CycleCalculator.recommendedWakeTimes(now, state.value.profile)

    /** Próxima ocurrencia de la hora guardada (modo fijo o inverso). */
    fun nextFixedDeadline(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long =
        nextOccurrence(alarm.minuteOfDay, now)

    /**
     * RF1: marca el inicio exacto, programa la hora límite (RNF2) e inicia el monitoreo.
     * Devuelve el id de la sesión.
     */
    suspend fun startNight(context: Context, mode: AlarmMode, deadline: Long): Long {
        val start = System.currentTimeMillis()
        val profile = repo.profile()
        val alarm = repo.activeAlarm()
        val alarmId = if (mode == AlarmMode.RECOMMENDED) null else alarm?.id
        val sessionId = repo.startSession(mode, alarmId, start, deadline, profile.windowMinutes)

        AlarmStore.setActiveSession(context, sessionId, alarm?.soundUri ?: AlarmStore.soundUri(context))
        AlarmStore.setSnoozeCount(context, 0)
        AlarmScheduler.cancelAll(context)
        AlarmScheduler.scheduleDeadline(context, sessionId, deadline)
        SleepMonitorService.start(context, sessionId, deadline, profile.windowMinutes, profile.avgCycleMinutes)
        return sessionId
    }

    companion object {
        fun nextOccurrence(minuteOfDay: Int, now: Long): Long = AlarmScheduler.nextOccurrence(minuteOfDay, now)
    }
}
