package com.alarmapp.data

import com.alarmapp.core.sleep.Epoch
import com.alarmapp.core.sleep.Insights
import com.alarmapp.core.sleep.NightSummary
import com.alarmapp.core.sleep.PhaseEstimator
import com.alarmapp.core.sleep.ProfileLearner
import com.alarmapp.core.sleep.SleepMetrics
import com.alarmapp.core.sleep.SleepProfile
import com.alarmapp.core.sleep.SleepStage
import com.alarmapp.core.sleep.WindowTuner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Única puerta de entrada a los datos para la interfaz y los servicios. */
class SleepRepository(private val db: AppDatabase) {

    private val dao = db.sleepDao()

    // ---------------------------------------------------------------- Perfil

    suspend fun profile(): SleepProfile = ensureProfile().toCore()

    suspend fun profileEntity(): ProfileEntity = ensureProfile()

    fun observeProfile(): Flow<ProfileEntity?> = dao.observeProfile()

    suspend fun setUploadConsent(consent: Boolean) {
        dao.upsertProfile(ensureProfile().copy(uploadConsent = consent))
    }

    private suspend fun ensureProfile(): ProfileEntity = dao.profile() ?: ProfileEntity(
        avgCycleMinutes = SleepProfile.DEFAULT_CYCLE_MINUTES,
        avgLatencyMinutes = SleepProfile.DEFAULT_LATENCY_MINUTES,
        windowMinutes = WindowTuner.DEFAULT_WINDOW,
    ).also { dao.upsertProfile(it) }

    // ---------------------------------------------------------------- Alarmas

    fun observeAlarms(): Flow<List<AlarmEntity>> = dao.observeAlarms()

    suspend fun alarms(): List<AlarmEntity> = dao.alarms()

    suspend fun alarm(id: Long): AlarmEntity? = dao.alarm(id)

    /** Crea ([id] null) o actualiza una alarma. Devuelve su id. */
    suspend fun saveAlarm(id: Long?, mode: AlarmMode, minuteOfDay: Int, days: Int): Long {
        val profile = ensureProfile()
        val existing = id?.let { dao.alarm(it) }
        return if (existing != null) {
            dao.updateAlarm(existing.copy(mode = mode, minuteOfDay = minuteOfDay, days = days, active = true))
            existing.id
        } else {
            dao.insertAlarm(
                AlarmEntity(mode = mode, minuteOfDay = minuteOfDay, windowMinutes = profile.windowMinutes, active = true, days = days)
            )
        }
    }

    suspend fun setAlarmActive(id: Long, active: Boolean) = dao.setAlarmActive(id, active)

    suspend fun deleteAlarm(id: Long) = dao.deleteAlarm(id)

    // ---------------------------------------------------------------- Sesiones

    suspend fun startSession(
        mode: AlarmMode,
        alarmId: Long?,
        startTime: Long,
        deadlineTime: Long,
        windowMinutes: Int,
    ): Long {
        ensureProfile()
        return dao.insertSession(
            SleepSessionEntity(
                alarmId = alarmId, mode = mode, startTime = startTime,
                deadlineTime = deadlineTime, windowMinutes = windowMinutes,
            )
        )
    }

    suspend fun session(id: Long): SleepSessionEntity? = dao.session(id)

    fun observeSession(id: Long): Flow<SleepSessionEntity?> = dao.observeSession(id)

    fun observeLastFinished(): Flow<SleepSessionEntity?> = dao.observeLastFinished()

    fun observeHistory(): Flow<List<SleepSessionEntity>> = dao.observeHistory()

    /** Noches desde [since] listas para el resumen de Reportes. */
    fun observeNights(since: Long): Flow<List<Insights.Night>> = dao.observeNightsSince(since).map { rows ->
        rows.map { (s, score) ->
            Insights.Night(
                id = s.id,
                start = s.startTime,
                end = s.endTime ?: s.startTime,
                totalSleepMinutes = s.totalSleepMinutes ?: 0,
                latencyMinutes = s.latencyMinutes,
                lightMinutes = s.lightMinutes ?: 0,
                deepMinutes = s.deepMinutes ?: 0,
                remMinutes = s.remMinutes ?: 0,
                awakeMinutes = s.awakeMinutes ?: 0,
                wakeStage = s.wakeStage,
                snoozes = s.snoozeCount,
                rating = score,
            )
        }
    }

    suspend fun incrementSnooze(id: Long) = dao.incrementSnooze(id)

    suspend fun addIndicator(sessionId: Long, epoch: Epoch, stage: SleepStage) {
        dao.insertIndicator(
            IndicatorEntity(
                sessionId = sessionId, timestamp = epoch.timestamp, movement = epoch.movement,
                noiseDb = epoch.noiseDb, breathingRate = epoch.breathingRate,
                breathingRegularity = epoch.breathingRegularity, stage = stage,
            )
        )
    }

    suspend fun indicators(sessionId: Long): List<IndicatorEntity> = dao.indicators(sessionId)

    suspend fun recentEpochs(sessionId: Long): List<Epoch> = dao.indicators(sessionId).map { it.toEpoch() }

    /**
     * Cierra la noche: reclasifica todos los intervalos con contexto completo, calcula el
     * resumen y actualiza lo aprendido (latencia y ciclo). Devuelve el resumen.
     */
    suspend fun finishSession(sessionId: Long, endTime: Long, actualAlarmTime: Long?): NightSummary? =
        withContext(Dispatchers.Default) {
            val session = dao.session(sessionId) ?: return@withContext null
            if (session.endTime != null) return@withContext null
            val profile = ensureProfile()
            val items = dao.indicators(sessionId)
            val stages = PhaseEstimator(profile.avgCycleMinutes).classifyNight(items.map { it.toEpoch() })
            dao.updateStages(items.mapIndexed { i, ind -> ind.id to stages[i] })
            val summary = SleepMetrics.summarize(stages)

            dao.updateSession(
                session.copy(
                    endTime = endTime,
                    actualAlarmTime = actualAlarmTime,
                    latencyMinutes = summary.latencyMinutes,
                    wakeStage = summary.wakeStage,
                    totalSleepMinutes = summary.totalSleepMinutes,
                    lightMinutes = summary.lightMinutes,
                    deepMinutes = summary.deepMinutes,
                    remMinutes = summary.remMinutes,
                    awakeMinutes = summary.awakeMinutes,
                )
            )
            val learned = ProfileLearner.learn(profile.toCore(), summary)
            dao.upsertProfile(
                profile.copy(avgCycleMinutes = learned.avgCycleMinutes, avgLatencyMinutes = learned.avgLatencyMinutes)
            )
            summary
        }

    /**
     * Cierra noches que quedaron abiertas (p. ej. la alarma se apagó con el celular bloqueado
     * tras un reinicio y la base cifrada no estaba disponible).
     */
    suspend fun closeDanglingSessions(activeId: Long?) {
        val now = System.currentTimeMillis()
        dao.openSessions().filter { it.id != activeId && now - it.deadlineTime > 15 * 60_000L }.forEach { s ->
            val last = dao.indicators(s.id).lastOrNull()?.timestamp
            val end = (last?.plus(com.alarmapp.core.sleep.EPOCH_MS)) ?: s.startTime
            finishSession(s.id, end, actualAlarmTime = null)
        }
    }

    // ---------------------------------------------------------------- Calificación

    fun observeRating(sessionId: Long): Flow<Int?> = dao.observeRating(sessionId).map { it?.score }

    /** Guarda "¿Cómo te sientes?" y ajusta la ventana de la alarma (10–45 min). */
    suspend fun rate(sessionId: Long, score: Int): Boolean {
        val session = dao.session(sessionId) ?: return false
        dao.upsertRating(RatingEntity(sessionId = sessionId, score = score.coerceIn(1, 5), createdAt = System.currentTimeMillis()))
        val profile = ensureProfile()
        val before = session.actualAlarmTime?.let { ((session.deadlineTime - it) / 60_000L).toInt() } ?: 0
        val window = WindowTuner.adjust(profile.windowMinutes, score, session.wakeStage, before.coerceAtLeast(0))
        dao.upsertProfile(profile.copy(windowMinutes = window))
        dao.setAlarmWindows(window)
        return profile.uploadConsent
    }

    suspend fun pendingUploads(): List<Pair<RatingEntity, SleepSessionEntity>> =
        dao.pendingUploads().mapNotNull { r -> dao.session(r.sessionId)?.let { r to it } }

    suspend fun markUploaded(ratingId: Long) = dao.markUploaded(ratingId)

    // ---------------------------------------------------------------- Privacidad

    /** El usuario puede borrar todos sus datos cuando quiera (RNF5). */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        ensureProfile()
    }
}

private fun ProfileEntity.toCore() = SleepProfile(avgCycleMinutes, avgLatencyMinutes, windowMinutes)

private fun IndicatorEntity.toEpoch() = Epoch(timestamp, movement, noiseDb, breathingRate, breathingRegularity)
