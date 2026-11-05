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
        val s = Insights.summarize(listOf(night(1, 23, 0, 420), night(2, 23, 0, 420)), tz = tz)
        assertEquals(2, s.nights)
        assertTrue(s.observations.isEmpty())
    }

    @Test
    fun shortSessions_doNotCount() {
        val test = Insights.Night(99, at(5, 14, 0), at(5, 14, 5), 0, null, 0, 0, 0, 5, null, 0, null)
        val s = Insights.summarize(listOf(test), tz = tz)
        assertEquals(0, s.nights)
        assertNull(s.avgSleepMinutes)
    }

    @Test
    fun shortSleep_flagged() {
        val s = Insights.summarize((1..3).map { night(it, 0, 30, 5 * 60) }, tz = tz)
        assertEquals(300, s.avgSleepMinutes)
        assertTrue(s.observations.any { it.tone == Insights.Tone.ATTENTION && it.title.contains("poco") })
    }

    @Test
    fun longLatency_flagged() {
        val s = Insights.summarize((1..3).map { night(it, 23, 0, 450, latency = 45) }, tz = tz)
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
        val s = Insights.summarize(listOf(night(1, 21, 0, 420), night(2, 1, 0, 420), night(3, 23, 0, 420)), tz = tz)
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
        val s = Insights.summarize(nights, tz = tz)
        assertTrue(s.observations.any { it.title.startsWith("Despertar en fase ligera") })
        assertEquals(3.5, s.avgRating!!, 0.001)
    }

    @Test
    fun ageRange_changesRecommendation() {
        val s = Insights.summarize((1..3).map { night(it, 23, 0, 7 * 60 + 30) }, Insights.Context(ageRange = AgeRange.SENIOR), tz)
        assertTrue(s.observations.any { it.detail.contains("65 años o más") && it.detail.contains("7 a 8") })
    }

    @Test
    fun plan_completesCyclesBeforeWake() {
        val p = Insights.plan(Insights.Context(wakeMinute = 6 * 60, cycleMinutes = 90.0, latencyMinutes = 15.0))!!
        assertEquals(5, p.cycles)               // 5 x 90 = 450 min >= 7 h
        assertEquals(22 * 60 + 15, p.bedtimeMinute) // 6:00 - 7 h 30 - 15 min
    }

    @Test
    fun caffeine_lateLatency_detected() {
        val coffee = DayFactor.CAFFEINE.bit
        val nights = listOf(
            night(1, 23, 0, 420, latency = 45).copy(factors = coffee),
            night(2, 23, 0, 420, latency = 50).copy(factors = coffee),
            night(3, 23, 0, 420, latency = 10),
            night(4, 23, 0, 420, latency = 12),
        )
        val obs = Insights.summarize(nights, tz = tz).observations.first { it.title == DayFactor.CAFFEINE.label }
        assertEquals(Insights.Tone.ATTENTION, obs.tone)
        assertTrue(obs.detail.contains("min más en dormirte"))
    }

    @Test
    fun frequentSnoring_flagged() {
        val nights = (1..4).map { night(it, 23, 0, 420).copy(snoreMinutes = 40) }
        assertTrue(Insights.summarize(nights, tz = tz).observations.any { it.title == "Roncas con frecuencia" })
    }

    @Test
    fun cycle_phases() {
        val day = 86_400_000L
        val start = at(1, 12, 0)
        val st = MenstrualCycle.status(start + 2 * day, listOf(start), 28, 5, tz)!!
        assertEquals(3, st.cycleDay)
        assertEquals(MenstrualCycle.Phase.MENSTRUAL, st.phase)
        assertEquals(MenstrualCycle.Phase.OVULATORY, MenstrualCycle.status(start + 14 * day, listOf(start), 28, 5, tz)!!.phase)
        val late = MenstrualCycle.status(start + 25 * day, listOf(start), 28, 5, tz)!!
        assertEquals(MenstrualCycle.Phase.LUTEAL, late.phase)
        assertTrue(late.premenstrual)
        assertEquals(30, MenstrualCycle.learnedCycleDays(listOf(start, start + 30 * day, start + 60 * day), tz = tz))
    }

    @Test
    fun snoreDetector_rhythmicLowBursts() {
        val n = 200 // 20 s
        val db = FloatArray(n) { 30f }
        val zcr = FloatArray(n) { 0.3f }
        for (b in 0 until 5) for (k in 0 until 8) { val i = b * 40 + k; db[i] = 55f; zcr[i] = 0.06f }
        assertTrue(SnoreDetector.isSnoring(db, zcr))
        val talk = FloatArray(n) { if (it % 40 < 8) 55f else 30f }
        assertTrue(!SnoreDetector.isSnoring(talk, FloatArray(n) { 0.25f })) // fuerte pero agudo
    }
}
