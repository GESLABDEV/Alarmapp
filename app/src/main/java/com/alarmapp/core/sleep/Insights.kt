package com.alarmapp.core.sleep

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Resumen y observaciones de varias noches (sección Reportes). Sin dependencias de Android.
 *
 * Las observaciones se basan en referencias generales del sueño en adultos:
 *  - Duración: se recomiendan 7 a 9 horas (consenso AASM/SRS y National Sleep Foundation).
 *  - Latencia: dormirse suele tomar menos de 20–30 min; más de 30 min de forma frecuente
 *    es un criterio usado en la evaluación del insomnio.
 *  - Regularidad: horarios de sueño muy variables se asocian con peor descanso.
 * No son un diagnóstico: el celular solo estima, y cualquier preocupación va con un profesional.
 */
object Insights {

    /** Una noche ya cerrada. */
    data class Night(
        val id: Long,
        val start: Long,
        val end: Long,
        val totalSleepMinutes: Int,
        val latencyMinutes: Int?,
        val lightMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val awakeMinutes: Int,
        val wakeStage: SleepStage?,
        val snoozes: Int,
        val rating: Int?,
    ) {
        /** Sesiones de menos de 2 horas (pruebas, siestas) no cuentan para el resumen. */
        val counts: Boolean get() = end - start >= MIN_NIGHT_MS
    }

    enum class Tone { GOOD, NEUTRAL, ATTENTION }

    data class Observation(val tone: Tone, val title: String, val detail: String)

    data class Summary(
        val nights: Int,
        val avgSleepMinutes: Int?,
        val avgLatencyMinutes: Int?,
        val avgRating: Double?,
        /** Desviación típica de la hora de acostarse, en minutos. */
        val bedtimeSpreadMinutes: Int?,
        val lightMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val awakeMinutes: Int,
        val observations: List<Observation>,
    )

    const val MIN_NIGHTS = 3
    private const val MIN_NIGHT_MS = 2 * 60 * 60_000L

    fun summarize(all: List<Night>, tz: TimeZone = TimeZone.getDefault()): Summary {
        val nights = all.filter { it.counts }
        val n = nights.size
        val avgSleep = nights.map { it.totalSleepMinutes }.averageOrNull()?.roundToInt()
        val avgLatency = nights.mapNotNull { it.latencyMinutes }.averageOrNull()?.roundToInt()
        val avgRating = nights.mapNotNull { it.rating?.toDouble() }.averageOrNull()
        val spread = if (n >= 2) bedtimeSpread(nights.map { it.start }, tz) else null

        fun avgOf(f: (Night) -> Int) = nights.map(f).averageOrNull()?.roundToInt() ?: 0

        return Summary(
            nights = n,
            avgSleepMinutes = avgSleep,
            avgLatencyMinutes = avgLatency,
            avgRating = avgRating,
            bedtimeSpreadMinutes = spread,
            lightMinutes = avgOf { it.lightMinutes },
            deepMinutes = avgOf { it.deepMinutes },
            remMinutes = avgOf { it.remMinutes },
            awakeMinutes = avgOf { it.awakeMinutes },
            observations = if (n < MIN_NIGHTS) emptyList() else observations(nights, avgSleep, avgLatency, spread),
        )
    }

    private fun observations(nights: List<Night>, avgSleep: Int?, avgLatency: Int?, spread: Int?): List<Observation> {
        val out = mutableListOf<Observation>()

        avgSleep?.let { m ->
            val text = "En promedio duermes ${hm(m)}. La recomendación para adultos es de 7 a 9 horas."
            out += when {
                m < 6 * 60 -> Observation(Tone.ATTENTION, "Estás durmiendo poco", text)
                m < 7 * 60 -> Observation(Tone.NEUTRAL, "Un poco menos de lo recomendado", text)
                m <= 9 * 60 -> Observation(Tone.GOOD, "Buena duración de sueño", text)
                else -> Observation(Tone.NEUTRAL, "Duermes más de 9 horas", "$text Si te levantas cansado de todos modos, vale la pena consultarlo.")
            }
        }

        avgLatency?.let { l ->
            out += when {
                l > 30 -> Observation(
                    Tone.ATTENTION, "Te cuesta conciliar el sueño",
                    "Tardas en promedio $l min en dormirte. Si pasa más de 30 min con frecuencia, conviene comentarlo con un profesional de la salud.",
                )
                l >= 20 -> Observation(Tone.NEUTRAL, "Tardas un poco en dormirte", "En promedio $l min. Lo habitual es menos de 20 a 30 min.")
                else -> Observation(Tone.GOOD, "Te duermes con facilidad", "En promedio tardas $l min en dormirte.")
            }
        }

        spread?.let { s ->
            out += when {
                s > 60 -> Observation(
                    Tone.ATTENTION, "Horario irregular",
                    "Tu hora de acostarte varía ±${hm(s)} entre noches. Acostarte a una hora parecida ayuda a que la alarma te encuentre en fase ligera.",
                )
                s > 30 -> Observation(Tone.NEUTRAL, "Horario algo variable", "Tu hora de acostarte varía ±$s min entre noches.")
                else -> Observation(Tone.GOOD, "Horario constante", "Te acuestas casi a la misma hora (±$s min). Eso favorece un buen despertar.")
            }
        }

        // ¿Te sientes mejor cuando despiertas en fase ligera?
        val lightWake = nights.filter { it.rating != null && (it.wakeStage == SleepStage.LIGHT || it.wakeStage == SleepStage.AWAKE) }
        val heavyWake = nights.filter { it.rating != null && (it.wakeStage == SleepStage.DEEP || it.wakeStage == SleepStage.REM) }
        if (lightWake.size >= 2 && heavyWake.size >= 2) {
            val a = lightWake.map { it.rating!!.toDouble() }.average()
            val b = heavyWake.map { it.rating!!.toDouble() }.average()
            if (a - b >= 0.5) {
                out += Observation(
                    Tone.GOOD, "Despertar en fase ligera te sienta mejor",
                    "Calificas tus mañanas con ${one(a)}/5 cuando despiertas en fase ligera y ${one(b)}/5 cuando no.",
                )
            }
        }

        // ¿Te sientes mejor cuando duermes 7 horas o más?
        val longN = nights.filter { it.rating != null && it.totalSleepMinutes >= 7 * 60 }
        val shortN = nights.filter { it.rating != null && it.totalSleepMinutes < 7 * 60 }
        if (longN.size >= 2 && shortN.size >= 2) {
            val a = longN.map { it.rating!!.toDouble() }.average()
            val b = shortN.map { it.rating!!.toDouble() }.average()
            if (abs(a - b) >= 0.5) {
                out += if (a > b) Observation(
                    Tone.GOOD, "Dormir 7 horas o más se nota",
                    "Tus mañanas promedian ${one(a)}/5 cuando duermes 7 h o más, y ${one(b)}/5 cuando duermes menos.",
                ) else Observation(
                    Tone.NEUTRAL, "Más horas no siempre se sienten mejor",
                    "Promedias ${one(a)}/5 con 7 h o más y ${one(b)}/5 con menos. La hora de despertar puede pesar más que la duración.",
                )
            }
        }

        val snoozeAvg = nights.map { it.snoozes }.average()
        if (snoozeAvg >= 1.0) {
            out += Observation(
                Tone.NEUTRAL, "Sueles posponer la alarma",
                "Pospones ${one(snoozeAvg)} veces por mañana en promedio. Volver a dormirte unos minutos suele dejar más somnolencia que levantarte a la primera.",
            )
        }
        return out
    }

    /**
     * Variación de la hora de acostarse. Se mide desde el mediodía para que las 23:30 y
     * la 1:00 queden cerca (y no a 22 horas de distancia).
     */
    fun bedtimeSpread(starts: List<Long>, tz: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(tz)
        val mins = starts.map {
            cal.timeInMillis = it
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            ((m - 12 * 60) + 24 * 60) % (24 * 60)
        }
        val mean = mins.average()
        return sqrt(mins.sumOf { (it - mean) * (it - mean) } / mins.size).roundToInt()
    }

    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()

    @JvmName("averageOrNullDouble")
    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun hm(m: Int): String = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

    private fun one(d: Double): String = String.format(java.util.Locale.US, "%.1f", d).replace('.', ',')
}
