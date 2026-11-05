package com.alarmapp.core.sleep

/**
 * Reglas de la alarma inteligente (RF4):
 *  - Dentro de la ventana previa a la hora límite, despierta en la fase más ligera detectada.
 *  - Si no detecta fase ligera, la alarma suena obligatoriamente a la hora límite
 *    (eso lo garantiza AlarmManager, no esta clase).
 */
object SmartWake {

    private const val MINUTE = 60_000L

    fun windowStart(deadlineMs: Long, windowMinutes: Int): Long = deadlineMs - windowMinutes * MINUTE

    fun isInWindow(nowMs: Long, deadlineMs: Long, windowMinutes: Int): Boolean =
        nowMs >= windowStart(deadlineMs, windowMinutes) && nowMs < deadlineMs

    /**
     * Decide si despertar ahora. Exige que el último intervalo sea ligero o despierto
     * y que el anterior no haya sido profundo, para no cortar una transición.
     */
    fun shouldWakeNow(
        nowMs: Long,
        deadlineMs: Long,
        windowMinutes: Int,
        recentStages: List<SleepStage>,
    ): Boolean {
        if (!isInWindow(nowMs, deadlineMs, windowMinutes)) return false
        val last = recentStages.lastOrNull() ?: return false
        val previous = recentStages.getOrNull(recentStages.size - 2)
        val lightEnough = last == SleepStage.LIGHT || last == SleepStage.AWAKE
        return lightEnough && previous != SleepStage.DEEP
    }
}

/**
 * Ajuste de la ventana con la calificación matutina (RF4, RF6).
 * Por defecto 30 min, siempre acotada entre 10 y 45 min, en pasos de 5.
 */
object WindowTuner {
    const val DEFAULT_WINDOW = 30
    const val MIN_WINDOW = 10
    const val MAX_WINDOW = 45
    private const val STEP = 5

    /**
     * @param rating 1..5 de "¿Cómo te sientes?".
     * @param wakeStage fase en la que despertó.
     * @param minutesBeforeDeadline cuántos minutos antes de la hora límite sonó.
     */
    fun adjust(current: Int, rating: Int, wakeStage: SleepStage?, minutesBeforeDeadline: Int): Int {
        val wokeLight = wakeStage == SleepStage.LIGHT || wakeStage == SleepStage.AWAKE
        val next = when {
            // Se sintió mal y despertó en fase pesada: dar más margen para encontrar fase ligera.
            rating <= 2 && !wokeLight -> current + STEP
            // Se sintió muy bien pero se despertó mucho antes: recuperar minutos de sueño.
            rating >= 4 && wokeLight && minutesBeforeDeadline > current / 2 -> current - STEP
            else -> current
        }
        return next.coerceIn(MIN_WINDOW, MAX_WINDOW)
    }
}

/** Aprendizaje de la duración de ciclo y la latencia de cada persona (tabla Perfil). */
object ProfileLearner {
    private const val LATENCY_ALPHA = 0.3
    private const val CYCLE_ALPHA = 0.2
    private const val MIN_CYCLE = 70.0
    private const val MAX_CYCLE = 120.0
    private const val MIN_SLEEP_FOR_CYCLE = 180

    fun learn(profile: SleepProfile, summary: NightSummary): SleepProfile {
        var latency = profile.avgLatencyMinutes
        summary.latencyMinutes?.let { measured ->
            if (measured in 0..120) latency = ema(latency, measured.toDouble(), LATENCY_ALPHA)
        }
        var cycle = profile.avgCycleMinutes
        val est = summary.estimatedCycleMinutes
        if (est != null && summary.asleepMinutes >= MIN_SLEEP_FOR_CYCLE) {
            cycle = ema(cycle, est.coerceIn(MIN_CYCLE, MAX_CYCLE), CYCLE_ALPHA)
        }
        return profile.copy(avgLatencyMinutes = latency, avgCycleMinutes = cycle)
    }

    private fun ema(old: Double, new: Double, alpha: Double) = old + alpha * (new - old)
}
