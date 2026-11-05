package com.alarmapp.core.sleep

/**
 * Lógica de sueño pura: este paquete NO depende de Android.
 * Así se puede probar con JUnit y reutilizar (o portar) en la migración a Flutter.
 */

/** Fases estimadas. Con solo el celular son una estimación, no un diagnóstico. */
enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

/** Intervalo estándar de los estudios de sueño: 30 segundos. */
const val EPOCH_MS: Long = 30_000L
const val EPOCHS_PER_MINUTE: Int = 2

/**
 * Indicadores de un intervalo de 30 s. Nunca contiene audio, solo números derivados.
 *
 * @param movement desviación media de la magnitud del acelerómetro (m/s²).
 * @param noiseDb nivel de ruido relativo (dBFS + 90, aprox. 0–90).
 * @param breathingRate respiraciones por minuto estimadas, o null si no hubo señal clara.
 * @param breathingRegularity 0..1 (1 = muy regular), o null si no hubo señal clara.
 */
data class Epoch(
    val timestamp: Long,
    val movement: Float,
    val noiseDb: Float,
    val breathingRate: Float?,
    val breathingRegularity: Float?,
    /** Se detectó ronquido en este intervalo (estimación). */
    val snoring: Boolean = false,
)

/** Lo que la IA aprende de cada persona (tabla Perfil). */
data class SleepProfile(
    val avgCycleMinutes: Double = DEFAULT_CYCLE_MINUTES,
    val avgLatencyMinutes: Double = DEFAULT_LATENCY_MINUTES,
    val windowMinutes: Int = WindowTuner.DEFAULT_WINDOW,
) {
    companion object {
        /** Aproximadamente 90 minutos por ciclo (documento de requisitos, RF4). */
        const val DEFAULT_CYCLE_MINUTES = 90.0
        /** Valor inicial razonable mientras se mide la latencia real del usuario. */
        const val DEFAULT_LATENCY_MINUTES = 15.0
    }
}

/** Resumen de una noche (reporte matutino, RF6). */
data class NightSummary(
    val totalSleepMinutes: Int,
    val latencyMinutes: Int?,
    val lightMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val wakeStage: SleepStage?,
    val remEpisodes: Int,
    val estimatedCycleMinutes: Double?,
) {
    val asleepMinutes: Int get() = lightMinutes + deepMinutes + remMinutes

    fun percent(stage: SleepStage): Int {
        val total = lightMinutes + deepMinutes + remMinutes + awakeMinutes
        if (total == 0) return 0
        val m = when (stage) {
            SleepStage.LIGHT -> lightMinutes
            SleepStage.DEEP -> deepMinutes
            SleepStage.REM -> remMinutes
            SleepStage.AWAKE -> awakeMinutes
        }
        return Math.round(m * 100f / total)
    }
}
