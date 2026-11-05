package com.alarmapp.core.sleep

import kotlin.math.roundToLong

/**
 * Modos de alarma 2 y 3 (RF4): horas que coinciden con el final de un ciclo,
 * sumando la latencia. Usa la duración de ciclo y la latencia aprendidas del usuario.
 */
object CycleCalculator {

    private const val MINUTE = 60_000L

    /** Modo "Hora recomendada": horas de despertar desde que el usuario se acuesta. */
    fun recommendedWakeTimes(
        bedtimeMs: Long,
        profile: SleepProfile,
        cycles: IntRange = 4..6,
    ): List<Long> = cycles.map { n ->
        roundToMinute(bedtimeMs + ((profile.avgLatencyMinutes + n * profile.avgCycleMinutes) * MINUTE).roundToLong())
    }

    /** Modo inverso: a qué hora acostarse para despertar a [wakeMs]. De más a menos ciclos. */
    fun recommendedBedtimes(
        wakeMs: Long,
        profile: SleepProfile,
        cycles: IntRange = 4..6,
    ): List<Long> = cycles.reversed().map { n ->
        roundToMinute(wakeMs - ((profile.avgLatencyMinutes + n * profile.avgCycleMinutes) * MINUTE).roundToLong())
    }

    fun cyclesFor(bedtimeMs: Long, wakeMs: Long, profile: SleepProfile): Double =
        ((wakeMs - bedtimeMs) / MINUTE.toDouble() - profile.avgLatencyMinutes) / profile.avgCycleMinutes

    private fun roundToMinute(ms: Long): Long = ((ms + MINUTE / 2) / MINUTE) * MINUTE
}
