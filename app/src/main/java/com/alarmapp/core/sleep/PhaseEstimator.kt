package com.alarmapp.core.sleep

import kotlin.math.max

/**
 * IA etapa 1: algoritmo basado en reglas (documento, sección 6).
 *
 * 1. Sueño/vigilia: suma ponderada de la actividad de los intervalos vecinos,
 *    con la misma estructura que el método de actigrafía de Cole-Kripke.
 *    Los pesos se reescalan para el acelerómetro del celular, por lo que es
 *    una calibración heurística, no clínicamente validada.
 * 2. Ligero / profundo / REM dentro del sueño: quietud sostenida del cuerpo,
 *    regularidad de la respiración (si el micrófono la detectó) y la posición
 *    dentro del ciclo personal (aprox. 90 min, ajustado por la IA).
 *
 * Funciona desde la primera noche, sin datos previos y sin internet (RF3).
 */
class PhaseEstimator(private val cycleMinutes: Double = SleepProfile.DEFAULT_CYCLE_MINUTES) {

    /** Clasificación de toda la noche (usa también intervalos futuros). Para el reporte. */
    fun classifyNight(epochs: List<Epoch>): List<SleepStage> {
        if (epochs.isEmpty()) return emptyList()
        val act = normalizedActivity(epochs)
        val wake = BooleanArray(epochs.size) { isWake(act, it, causal = false) }
        val raw = stagesFrom(epochs, act, wake)
        return smooth(raw)
    }

    /** Fase del último intervalo usando solo el pasado. Para decidir la alarma en tiempo real. */
    fun classifyLatest(epochs: List<Epoch>): SleepStage {
        if (epochs.isEmpty()) return SleepStage.AWAKE
        val act = normalizedActivity(epochs)
        val wake = BooleanArray(epochs.size) { isWake(act, it, causal = true) }
        return stagesFrom(epochs, act, wake).last()
    }

    // ---------------------------------------------------------------- internos

    internal fun normalizedActivity(epochs: List<Epoch>): FloatArray {
        val moves = epochs.map { it.movement }.sorted()
        // Piso de ruido del sensor; se limita para que una noche muy inquieta no se tome como "quietud".
        val baseline = moves[(moves.size * 0.2).toInt().coerceIn(0, moves.size - 1)]
            .coerceAtMost(MAX_MOVEMENT_BASELINE)
        val scale = max(baseline * 3f, MIN_MOVEMENT_SCALE)

        val noises = epochs.map { it.noiseDb }.sorted()
        val noiseBase = noises[(noises.size * 0.2).toInt().coerceIn(0, noises.size - 1)]

        return FloatArray(epochs.size) { i ->
            val e = epochs[i]
            val motion = max(0f, e.movement - baseline) / scale
            // Un ruido muy por encima del fondo (hablar, levantarse) suma algo de actividad.
            val sound = max(0f, e.noiseDb - noiseBase - NOISE_MARGIN_DB) / NOISE_MARGIN_DB
            motion + 0.5f * sound
        }
    }

    private fun isWake(act: FloatArray, i: Int, causal: Boolean): Boolean {
        var sum = 0f
        var wsum = 0f
        for (k in WEIGHTS.indices) {
            val offset = k - CURRENT_INDEX
            if (causal && offset > 0) continue
            val j = i + offset
            if (j < 0 || j >= act.size) continue
            sum += WEIGHTS[k] * act[j]
            wsum += WEIGHTS[k]
        }
        return wsum > 0f && sum / wsum >= WAKE_THRESHOLD
    }

    private fun stagesFrom(epochs: List<Epoch>, act: FloatArray, wake: BooleanArray): List<SleepStage> {
        val onset = SleepMetrics.onsetIndex(wake.map { !it })
        val still = IntArray(act.size)
        for (i in act.indices) {
            still[i] = if (act[i] < STILL_THRESHOLD) (if (i > 0) still[i - 1] else 0) + 1 else 0
        }
        return List(epochs.size) { i ->
            if (onset == null || i < onset || wake[i]) return@List SleepStage.AWAKE
            val since = (i - onset).toDouble() / EPOCHS_PER_MINUTE
            val cyclePos = (since % cycleMinutes) / cycleMinutes
            stageWithinSleep(
                still = still[i] >= STILL_EPOCHS_FOR_STABLE,
                regularity = epochs[i].breathingRegularity,
                minutesSinceOnset = since,
                cyclePos = cyclePos,
            )
        }
    }

    private fun stageWithinSleep(
        still: Boolean,
        regularity: Float?,
        minutesSinceOnset: Double,
        cyclePos: Double,
    ): SleepStage {
        if (!still) return SleepStage.LIGHT
        val lateInCycle = minutesSinceOnset >= 60 && cyclePos >= 0.70
        val midCycle = cyclePos in 0.15..0.65
        return when {
            // REM: cuerpo quieto (atonía muscular) pero respiración irregular.
            regularity != null && regularity < 0.40f && minutesSinceOnset >= 60 -> SleepStage.REM
            // Profundo: cuerpo quieto y respiración muy regular.
            regularity != null && regularity >= 0.60f && !lateInCycle -> SleepStage.DEEP
            // Sin señal de respiración clara: se usa la posición en el ciclo.
            lateInCycle -> SleepStage.REM
            // El sueño profundo se concentra en la primera mitad de la noche.
            midCycle && minutesSinceOnset < 300 -> SleepStage.DEEP
            else -> SleepStage.LIGHT
        }
    }

    /** Filtro de mayoría (5 intervalos) para quitar saltos aislados. */
    private fun smooth(raw: List<SleepStage>): List<SleepStage> = List(raw.size) { i ->
        val from = max(0, i - 2)
        val to = minOf(raw.size - 1, i + 2)
        val counts = IntArray(SleepStage.entries.size)
        for (j in from..to) counts[raw[j].ordinal]++
        val best = counts.indices.maxBy { counts[it] }
        if (counts[best] > counts[raw[i].ordinal]) SleepStage.entries[best] else raw[i]
    }

    companion object {
        /** Pesos relativos de Cole-Kripke para A-4 … A0 … A+2. */
        private val WEIGHTS = floatArrayOf(106f, 54f, 58f, 76f, 230f, 74f, 67f)
        private const val CURRENT_INDEX = 4
        private const val WAKE_THRESHOLD = 0.6f
        private const val STILL_THRESHOLD = 0.3f
        /** 10 minutos de quietud continua. */
        private const val STILL_EPOCHS_FOR_STABLE = 20
        private const val MIN_MOVEMENT_SCALE = 0.015f
        private const val MAX_MOVEMENT_BASELINE = 0.03f
        private const val NOISE_MARGIN_DB = 10f
    }
}
