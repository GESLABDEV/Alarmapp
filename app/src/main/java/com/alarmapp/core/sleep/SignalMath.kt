package com.alarmapp.core.sleep

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Procesamiento de señales en el dispositivo (RF2). Recibe números y devuelve números:
 * el audio crudo nunca sale de la memoria ni se guarda.
 */
object SignalMath {

    /** Nivel relativo en dB a partir del RMS de muestras PCM de 16 bits (0 ≈ silencio digital, 90 ≈ máximo). */
    fun rmsToDb(rms: Double): Float {
        if (rms <= 0.0) return 0f
        val dbfs = 20 * log10(rms / Short.MAX_VALUE)
        return (dbfs + 90).toFloat().coerceIn(0f, 90f)
    }

    fun rms(samples: ShortArray, from: Int = 0, to: Int = samples.size): Double {
        if (to <= from) return 0.0
        var sum = 0.0
        for (i in from until to) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / (to - from))
    }

    /** Proporción de cambios de signo entre muestras (0..1): baja en sonidos graves como el ronquido. */
    fun zeroCrossingRate(samples: ShortArray, count: Int = samples.size): Float {
        if (count < 2) return 0f
        var z = 0
        for (i in 1 until count) if ((samples[i] >= 0) != (samples[i - 1] >= 0)) z++
        return z.toFloat() / (count - 1)
    }

    /** Desviación media absoluta de la magnitud del acelerómetro: cuánto se movió el colchón. */
    fun movementIndex(magnitudes: FloatArray, count: Int = magnitudes.size): Float {
        if (count <= 1) return 0f
        var mean = 0f
        for (i in 0 until count) mean += magnitudes[i]
        mean /= count
        var dev = 0f
        for (i in 0 until count) dev += abs(magnitudes[i] - mean)
        return dev / count
    }
}

/**
 * Estima ritmo y regularidad de la respiración a partir de la envolvente del sonido
 * (energía cada 100 ms) usando autocorrelación. Con el micrófono del celular la señal
 * es débil y ruidosa: si no hay modulación clara devuelve null en lugar de inventar un valor.
 */
object BreathingAnalyzer {

    data class Result(val ratePerMinute: Float?, val regularity: Float?)

    private const val MIN_BPM = 8f
    private const val MAX_BPM = 30f
    private const val MIN_MODULATION = 0.05f
    private const val MIN_CORRELATION_FOR_RATE = 0.3f

    fun analyze(envelope: FloatArray, frameRateHz: Float): Result {
        val n = envelope.size
        if (n < 20) return Result(null, null)
        val mean = envelope.average().toFloat()
        if (mean <= 0f) return Result(null, null)
        val x = FloatArray(n) { envelope[it] - mean }
        var variance = 0f
        for (v in x) variance += v * v
        variance /= n
        val modulation = sqrt(variance) / mean
        if (modulation < MIN_MODULATION) return Result(null, null)

        val minLag = max(1, (frameRateHz * 60f / MAX_BPM).toInt())
        val maxLag = (frameRateHz * 60f / MIN_BPM).toInt().coerceAtMost(n / 2)
        if (maxLag <= minLag) return Result(null, null)

        var bestLag = -1
        var best = -1f
        for (lag in minLag..maxLag) {
            var s = 0f
            for (i in 0 until n - lag) s += x[i] * x[i + lag]
            val r = s / ((n - lag) * variance)
            if (r > best) { best = r; bestLag = lag }
        }
        val regularity = best.coerceIn(0f, 1f)
        val rate = if (best >= MIN_CORRELATION_FOR_RATE && bestLag > 0) 60f * frameRateHz / bestLag else null
        return Result(rate, regularity)
    }
}
