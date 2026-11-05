package com.alarmapp.core.sleep

/** Cálculos del reporte matutino (RF6) a partir de las fases de cada intervalo. */
object SleepMetrics {

    /** 5 minutos de sueño continuo marcan el inicio del sueño. */
    private const val ONSET_RUN = 10
    /** Dos episodios REM separados por menos de 10 min se cuentan como uno. */
    private const val REM_MERGE_GAP = 20
    private const val REM_MIN_LEN = 2

    /** Índice del primer intervalo de una racha de [ONSET_RUN] intervalos dormidos. */
    fun onsetIndex(asleep: List<Boolean>): Int? {
        var run = 0
        for (i in asleep.indices) {
            run = if (asleep[i]) run + 1 else 0
            if (run == ONSET_RUN) return i - ONSET_RUN + 1
        }
        return null
    }

    fun summarize(stages: List<SleepStage>): NightSummary {
        val onset = onsetIndex(stages.map { it != SleepStage.AWAKE })
        if (onset == null) {
            return NightSummary(
                totalSleepMinutes = 0, latencyMinutes = null,
                lightMinutes = 0, deepMinutes = 0, remMinutes = 0,
                awakeMinutes = stages.size / EPOCHS_PER_MINUTE,
                wakeStage = stages.lastOrNull(), remEpisodes = 0, estimatedCycleMinutes = null,
            )
        }
        val after = stages.subList(onset, stages.size)
        fun minutes(s: SleepStage) = after.count { it == s } / EPOCHS_PER_MINUTE

        val (episodes, lastRemEnd) = remEpisodes(stages, onset)
        val cycle = if (episodes >= 2 && lastRemEnd != null) {
            (lastRemEnd - onset + 1).toDouble() / EPOCHS_PER_MINUTE / episodes
        } else null

        val light = minutes(SleepStage.LIGHT)
        val deep = minutes(SleepStage.DEEP)
        val rem = minutes(SleepStage.REM)
        return NightSummary(
            totalSleepMinutes = light + deep + rem,
            latencyMinutes = onset / EPOCHS_PER_MINUTE,
            lightMinutes = light,
            deepMinutes = deep,
            remMinutes = rem,
            awakeMinutes = minutes(SleepStage.AWAKE),
            wakeStage = stages.lastOrNull(),
            remEpisodes = episodes,
            estimatedCycleMinutes = cycle,
        )
    }

    /** Devuelve (número de episodios REM, índice donde termina el último). */
    private fun remEpisodes(stages: List<SleepStage>, from: Int): Pair<Int, Int?> {
        var episodes = 0
        var lastEnd: Int? = null
        var i = from
        while (i < stages.size) {
            if (stages[i] == SleepStage.REM) {
                var j = i
                while (j + 1 < stages.size && stages[j + 1] == SleepStage.REM) j++
                if (j - i + 1 >= REM_MIN_LEN) {
                    if (lastEnd == null || i - lastEnd > REM_MERGE_GAP) episodes++
                    lastEnd = j
                }
                i = j + 1
            } else i++
        }
        return episodes to lastEnd
    }
}
