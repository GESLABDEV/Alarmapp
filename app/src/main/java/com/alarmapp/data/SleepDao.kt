package com.alarmapp.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alarmapp.core.sleep.SleepStage
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    // -------- Perfil
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profile(): ProfileEntity?

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(p: ProfileEntity)

    // -------- Alarmas
    @Insert
    suspend fun insertAlarm(a: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(a: AlarmEntity)

    @Query("SELECT * FROM alarm ORDER BY minuteOfDay")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm ORDER BY minuteOfDay")
    suspend fun alarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun alarm(id: Long): AlarmEntity?

    @Query("UPDATE alarm SET active = :active WHERE id = :id")
    suspend fun setAlarmActive(id: Long, active: Boolean)

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun deleteAlarm(id: Long)

    @Query("UPDATE alarm SET windowMinutes = :window")
    suspend fun setAlarmWindows(window: Int)

    // -------- Sesiones
    @Insert
    suspend fun insertSession(s: SleepSessionEntity): Long

    @Update
    suspend fun updateSession(s: SleepSessionEntity)

    @Query("SELECT * FROM sleep_session WHERE id = :id")
    suspend fun session(id: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_session WHERE id = :id")
    fun observeSession(id: Long): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_session WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT 1")
    fun observeLastFinished(): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_session WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT :limit")
    fun observeHistory(limit: Int = 14): Flow<List<SleepSessionEntity>>

    /** Noches cerradas desde [since], con su calificación si la hay (sección Reportes). */
    @Query(
        "SELECT s.*, r.score AS score FROM sleep_session s " +
            "LEFT JOIN rating r ON r.sessionId = s.id " +
            "WHERE s.endTime IS NOT NULL AND s.startTime >= :since ORDER BY s.startTime DESC"
    )
    fun observeNightsSince(since: Long): Flow<List<SessionWithRating>>

    @Query("SELECT * FROM sleep_session WHERE endTime IS NULL")
    suspend fun openSessions(): List<SleepSessionEntity>

    @Query("UPDATE sleep_session SET snoozeCount = snoozeCount + 1 WHERE id = :id")
    suspend fun incrementSnooze(id: Long)

    // -------- Indicadores
    @Insert
    suspend fun insertIndicator(i: IndicatorEntity)

    @Query("SELECT * FROM indicator WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun indicators(sessionId: Long): List<IndicatorEntity>

    @Query("UPDATE indicator SET stage = :stage WHERE id = :id")
    suspend fun updateIndicatorStage(id: Long, stage: SleepStage)

    @Transaction
    suspend fun updateStages(items: List<Pair<Long, SleepStage>>) {
        items.forEach { (id, stage) -> updateIndicatorStage(id, stage) }
    }

    // -------- Periodos
    @Insert
    suspend fun insertPeriod(p: PeriodEntity): Long

    @Query("DELETE FROM period WHERE id = :id")
    suspend fun deletePeriod(id: Long)

    @Query("SELECT * FROM period ORDER BY startTime")
    fun observePeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM period ORDER BY startTime")
    suspend fun periods(): List<PeriodEntity>

    // -------- Calificaciones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(r: RatingEntity)

    @Query("SELECT * FROM rating WHERE sessionId = :sessionId")
    fun observeRating(sessionId: Long): Flow<RatingEntity?>

    @Query("SELECT * FROM rating WHERE uploaded = 0")
    suspend fun pendingUploads(): List<RatingEntity>

    @Query("UPDATE rating SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: Long)
}

data class SessionWithRating(
    @Embedded val session: SleepSessionEntity,
    @ColumnInfo(name = "score") val score: Int?,
)
