package com.alarmapp.core.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InsightsTest {

    private val tz = TimeZone.getTimeZone("America/Bogota")
    private val hour = 60 * 60_000L

    private fun at(day: Int, h: Int, m: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(2026, 10, day, h, m, 0) }.timeInMillis

    private fun night(
        day: Int, bedH: Int, bedM: Int, sleepMin: Int, latency: Int = 15,
        wake: SleepStage = SleepStage.LIGHT, rating: Int? = null, snoozes: Int = 0,
    ): Insights.Night {
        val start = at(day, bedH, bedM)
        return Insights.Night(
            id = day.toLong(), start = start, end = start + (sleepMin + latency) * 60_000L,
            totalSleepMinutes = sleepMin, latencyMinutes = latency,
            lightMinutes = sleepMin / 2, deepMinutes = sleepMin / 4, remMinutes = sleepMin / 4, awakeMinutes = 10,
            wakeStage = wake, snoozes = snoozes, rating = rating,
        )
    }

    @Test
    fun fewerThanThreeNights_noObservations() {
        val s = Insights.summarize(listOf(night(1, 23, 0, 420), night(2, 23, 0, 420)), tz)
        assertEquals(2, s.nights)
        assertTrue(s.observations.isEmpty())
    }

    @Test
    fun shortSessions_doNotCount() {
        val test = Insights.Night(99, at(5, 14, 0), at(5, 14, 5), 0, null, 0, 0, 0, 5, null, 0, null)
        val s = Insights.summarize(listOf(test), tz)
        assertEquals(0, s.nights)
        assertNull(s.avgSleepMinutes)
    }

    @Test
    fun shortSleep_flagged() {
        val s = Insights.summarize((1..3).map { night(it, 0, 30, 5 * 60) }, tz)
        assertEquals(300, s.avgSleepMinutes)
        assertTrue(s.observations.any { it.tone == Insights.Tone.ATTENTION && it.title.contains("poco") })
    }

    @Test
    fun longLatency_flagged() {
        val s = Insights.summarize((1..3).map { night(it, 23, 0, 450, latency = 45) }, tz)
        assertTrue(s.observations.any { it.title == "Te cuesta conciliar el sueño" })
    }

    @Test
    fun bedtimeSpread_aroundMidnight() {
        // 23:30 y 00:30 están a una hora, no a 23 horas
        val spread = Insights.bedtimeSpread(listOf(at(1, 23, 30), at(3, 0, 30)), tz)
        assertEquals(30, spread)
    }

    @Test
    fun irregularSchedule_flagged() {
        val s = Insights.summarize(listOf(night(1, 21, 0, 420), night(2, 1, 0, 420), night(3, 23, 0, 420)), tz)
        assertTrue(s.observations.any { it.title == "Horario irregular" })
    }

    @Test
    fun lightWake_feelsBetter() {
        val nights = listOf(
            night(1, 23, 0, 420, wake = SleepStage.LIGHT, rating = 5),
            night(2, 23, 0, 420, wake = SleepStage.LIGHT, rating = 4),
            night(3, 23, 0, 420, wake = SleepStage.DEEP, rating = 2),
            night(4, 23, 0, 420, wake = SleepStage.REM, rating = 3),
        )
        val s = Insights.summarize(nights, tz)
        assertTrue(s.observations.any { it.title.startsWith("Despertar en fase ligera") })
        assertEquals(3.5, s.avgRating!!, 0.001)
    }
}
