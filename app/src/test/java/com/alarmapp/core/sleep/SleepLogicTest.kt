package com.alarmapp.core.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SleepLogicTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val bedtime = 22 * hour // 22:00 del "día 0"
    private val profile = SleepProfile()

    // ---------------------------------------------------------------- RF4: ciclos

    @Test
    fun recommendedWakeTimes_addLatencyAndCycles() {
        val times = CycleCalculator.recommendedWakeTimes(bedtime, profile)
        // 15 min de latencia + 4, 5 y 6 ciclos de 90 min
        assertEquals(listOf(28 * hour + 15 * minute, 29 * hour + 45 * minute, 31 * hour + 15 * minute), times)
    }

    @Test
    fun inverseMode_returnsBedtimesFromMostCycles() {
        val wake = 30 * hour // 06:00 del día siguiente
        val beds = CycleCalculator.recommendedBedtimes(wake, profile)
        assertEquals(listOf(20 * hour + 45 * minute, 22 * hour + 15 * minute, 23 * hour + 45 * minute), beds)
    }

    @Test
    fun recommendedTimes_usePersonalCycle() {
        val p = profile.copy(avgCycleMinutes = 100.0, avgLatencyMinutes = 10.0)
        val first = CycleCalculator.recommendedWakeTimes(bedtime, p).first()
        assertEquals(bedtime + 410 * minute, first)
    }

    // ---------------------------------------------------------------- RF4: ventana

    @Test
    fun smartWake_onlyInsideWindowAndInLightPhase() {
        val deadline = 30 * hour
        val inside = deadline - 10 * minute
        val outside = deadline - 40 * minute
        val light = listOf(SleepStage.LIGHT, SleepStage.LIGHT)
        assertTrue(SmartWake.shouldWakeNow(inside, deadline, 30, light))
        assertFalse(SmartWake.shouldWakeNow(outside, deadline, 30, light))
        assertFalse(SmartWake.shouldWakeNow(inside, deadline, 30, listOf(SleepStage.DEEP, SleepStage.DEEP)))
        assertFalse(SmartWake.shouldWakeNow(inside, deadline, 30, listOf(SleepStage.DEEP, SleepStage.LIGHT)))
        assertFalse(SmartWake.shouldWakeNow(deadline, deadline, 30, light))
    }

    @Test
    fun windowTuner_staysBetween10And45() {
        var w = WindowTuner.DEFAULT_WINDOW
        repeat(10) { w = WindowTuner.adjust(w, rating = 1, wakeStage = SleepStage.DEEP, minutesBeforeDeadline = 0) }
        assertEquals(45, w)
        repeat(10) { w = WindowTuner.adjust(w, rating = 5, wakeStage = SleepStage.LIGHT, minutesBeforeDeadline = 44) }
        assertEquals(10, w)
        assertEquals(30, WindowTuner.adjust(30, rating = 3, wakeStage = SleepStage.LIGHT, minutesBeforeDeadline = 5))
    }

    // ---------------------------------------------------------------- RF3: fases

    @Test
    fun estimator_detectsLatencyAfterRestlessPeriod() {
        val epochs = syntheticNight(awakeMinutes = 20, sleepMinutes = 420)
        val stages = PhaseEstimator().classifyNight(epochs)
        val summary = SleepMetrics.summarize(stages)
        val latency = summary.latencyMinutes
        assertNotNull(latency)
        assertTrue("latencia=$latency", abs(latency!! - 20) <= 3)
        assertTrue(summary.deepMinutes > 0)
        assertTrue(summary.remMinutes > 0)
        assertTrue(summary.lightMinutes > 0)
        assertTrue(summary.totalSleepMinutes in 380..425)
    }

    @Test
    fun estimator_allMovement_meansNoSleep() {
        val epochs = List(120) { i -> Epoch(i * EPOCH_MS, movement = if (i % 2 == 0) 0.4f else 0.6f, noiseDb = 40f, breathingRate = null, breathingRegularity = null) }
        val summary = SleepMetrics.summarize(PhaseEstimator().classifyNight(epochs))
        assertNull(summary.latencyMinutes)
        assertEquals(0, summary.totalSleepMinutes)
    }

    @Test
    fun estimator_causalMatchesAwakeAtStart() {
        val epochs = syntheticNight(awakeMinutes = 20, sleepMinutes = 60).take(20)
        assertEquals(SleepStage.AWAKE, PhaseEstimator().classifyLatest(epochs))
    }

    @Test
    fun onset_requiresFiveMinutesOfSleep() {
        val asleep = List(9) { true } + false + List(10) { true }
        assertEquals(10, SleepMetrics.onsetIndex(asleep))
    }

    // ---------------------------------------------------------------- RF2: respiración

    @Test
    fun breathing_detectsRegularRhythm() {
        val fps = 10f
        val env = FloatArray(200) { i -> 100f + 30f * sin(2 * PI * (15.0 / 60.0) * i / fps).toFloat() }
        val r = BreathingAnalyzer.analyze(env, fps)
        assertNotNull(r.ratePerMinute)
        assertTrue("ritmo=${r.ratePerMinute}", abs(r.ratePerMinute!! - 15f) < 1.5f)
        assertTrue(r.regularity!! > 0.8f)
    }

    @Test
    fun breathing_flatSignal_returnsNull() {
        val r = BreathingAnalyzer.analyze(FloatArray(200) { 100f }, 10f)
        assertNull(r.ratePerMinute)
        assertNull(r.regularity)
    }

    // ---------------------------------------------------------------- IA: aprendizaje

    @Test
    fun learner_movesLatencyTowardMeasured() {
        val s = NightSummary(400, 35, 200, 100, 100, 10, SleepStage.LIGHT, 4, 100.0)
        val learned = ProfileLearner.learn(profile, s)
        assertEquals(21.0, learned.avgLatencyMinutes, 0.01) // 15 + 0.3 * (35 - 15)
        assertEquals(92.0, learned.avgCycleMinutes, 0.01)   // 90 + 0.2 * (100 - 90)
    }

    // ---------------------------------------------------------------- utilidades

    /** Noche sintética: periodo inquieto y luego ciclos de 90 min con quietud y respiración variable. */
    private fun syntheticNight(awakeMinutes: Int, sleepMinutes: Int): List<Epoch> {
        val list = ArrayList<Epoch>()
        var t = 0L
        repeat(awakeMinutes * EPOCHS_PER_MINUTE) { i ->
            list += Epoch(t, movement = if (i % 3 == 0) 0.5f else 0.3f, noiseDb = 45f, breathingRate = null, breathingRegularity = null)
            t += EPOCH_MS
        }
        repeat(sleepMinutes * EPOCHS_PER_MINUTE) { i ->
            val minuteInCycle = (i / EPOCHS_PER_MINUTE) % 90
            val (move, reg) = when {
                minuteInCycle < 15 -> (if (i % 6 == 0) 0.06f else 0.01f) to 0.5f // ligero, algo de movimiento
                minuteInCycle < 60 -> 0.01f to 0.8f                                // profundo
                minuteInCycle < 75 -> (if (i % 5 == 0) 0.07f else 0.01f) to 0.5f // ligero
                else -> 0.01f to 0.2f                                              // REM
            }
            list += Epoch(t, movement = move, noiseDb = 30f, breathingRate = 14f, breathingRegularity = reg)
            t += EPOCH_MS
        }
        return list
    }
}
