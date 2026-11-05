package com.alarmapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alarmapp.core.sleep.SleepStage

/**
 * Modelo de cinco tablas del documento de requisitos (sección 6, "Base de datos local").
 * Las fechas se guardan como números en milisegundos.
 */

enum class AlarmMode { FIXED, RECOMMENDED, INVERSE }

/** 1. Perfil: lo que la IA aprende. Hay un solo perfil por instalación (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = PROFILE_ID,
    val avgCycleMinutes: Double,
    val avgLatencyMinutes: Double,
    val windowMinutes: Int,
    val uploadConsent: Boolean = false,
    // Datos personales (v3). Opcionales; nunca se envían al servidor.
    val name: String? = null,
    /** Nombre de [com.alarmapp.core.sleep.Sex]. */
    val sex: String? = null,
    /** Nombre de [com.alarmapp.core.sleep.AgeRange]. */
    val ageRange: String? = null,
    /** Hora habitual de despertar, minutos desde medianoche. */
    val wakeMinute: Int? = null,
    @ColumnInfo(defaultValue = "28") val cycleDays: Int = 28,
    @ColumnInfo(defaultValue = "5") val periodDays: Int = 5,
) {
    companion object { const val PROFILE_ID = 1L }
}

/**
 * 2. Alarma: modo, hora (minutos desde medianoche), días, ventana y estado activo.
 * Se pueden guardar varias. El tono es uno solo para todas (Ajustes); soundUri queda sin uso.
 */
@Entity(
    tableName = "alarm",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")],
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = ProfileEntity.PROFILE_ID,
    val mode: AlarmMode,
    val minuteOfDay: Int,
    val windowMinutes: Int,
    val soundUri: String? = null,
    val active: Boolean,
    /** Máscara de días: bit 0 = lunes … bit 6 = domingo. 127 = todos. */
    @ColumnInfo(defaultValue = "127") val days: Int = 127,
)

/** 3. Sesión de sueño: una por noche. */
@Entity(
    tableName = "sleep_session",
    foreignKeys = [
        ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AlarmEntity::class, ["id"], ["alarmId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("profileId"), Index("alarmId"), Index("startTime")],
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = ProfileEntity.PROFILE_ID,
    /** Opcional: el modo recomendado no usa una alarma guardada. */
    val alarmId: Long?,
    val mode: AlarmMode,
    /** Momento exacto en que se presionó "Me voy a dormir". */
    val startTime: Long,
    val endTime: Long? = null,
    /** Hora límite de esta noche. */
    val deadlineTime: Long,
    val windowMinutes: Int,
    val latencyMinutes: Int? = null,
    /** Hora real en que sonó la alarma. */
    val actualAlarmTime: Long? = null,
    val wakeStage: SleepStage? = null,
    val snoozeCount: Int = 0,
    // Resumen precalculado para que el reporte aparezca en pocos segundos (RNF3).
    val totalSleepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    /** Factores del día marcados al acostarse (máscara de DayFactor). */
    @ColumnInfo(defaultValue = "0") val factors: Int = 0,
    val snoreMinutes: Int? = null,
)

/** 4. Indicadores: un registro cada 30 segundos. Nunca audio. */
@Entity(
    tableName = "indicator",
    foreignKeys = [ForeignKey(SleepSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class IndicatorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val movement: Float,
    val noiseDb: Float,
    val breathingRate: Float?,
    val breathingRegularity: Float?,
    val stage: SleepStage,
    @ColumnInfo(defaultValue = "0") val snoring: Boolean = false,
)

/** Inicio de cada periodo menstrual registrado (dato de salud: solo local y cifrado). */
@Entity(tableName = "period", indices = [Index("startTime")])
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
)

/** 5. Calificación: respuesta matutina 1–5, como máximo una por sesión. */
@Entity(
    tableName = "rating",
    foreignKeys = [ForeignKey(SleepSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId"], unique = true)],
)
data class RatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val score: Int,
    val createdAt: Long,
    val uploaded: Boolean = false,
)
