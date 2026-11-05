package com.alarmapp.core.sleep

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Resumen, observaciones y plan a partir de varias noches (sección Reportes).
 * Sin dependencias de Android.
 *
 * Referencias generales del sueño en adultos:
 *  - Duración recomendada según la edad ([AgeRange]; National Sleep Foundation).
 *  - Latencia: dormirse suele tomar menos de 20–30 min; más de 30 min con frecuencia es un
 *    criterio usado al evaluar el insomnio.
 *  - Regularidad: horarios muy variables se asocian con peor descanso.
 *  - Ronquido frecuente puede asociarse a apnea del sueño: se sugiere consultar.
 * No son un diagnóstico: el celular solo estima.
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
        val snoreMinutes: Int = 0,
        /** Máscara de [DayFactor]. */
        val factors: Int = 0,
        /** Fase del ciclo menstrual esa noche, si se registra. */
        val cyclePhase: MenstrualCycle.Phase? = null,
        val premenstrual: Boolean = false,
    ) {
        /** Sesiones de menos de 2 horas (pruebas, siestas) no cuentan para el resumen. */
        val counts: Boolean get() = end - start >= MIN_NIGHT_MS
    }

    /** Contexto personal que ajusta el análisis. Todo opcional. */
    data class Context(
        val ageRange: AgeRange? = null,
        /** Hora habitual de despertar, minutos desde medianoche. */
        val wakeMinute: Int? = null,
        val cycleMinutes: Double = SleepProfile.DEFAULT_CYCLE_MINUTES,
        val latencyMinutes: Double = SleepProfile.DEFAULT_LATENCY_MINUTES,
    )

    /** Hora sugerida para acostarse: ciclos completos que cubran las horas recomendadas. */
    data class Plan(val bedtimeMinute: Int, val wakeMinute: Int, val cycles: Int, val sleepMinutes: Int)

    enum class Tone { GOOD, NEUTRAL, ATTENTION }

    data class Observation(val tone: Tone, val title: String, val detail: String)

    data class Summary(
        val nights: Int,
        val avgSleepMinutes: Int?,
        val avgLatencyMinutes: Int?,
        val avgRating: Double?,
        /** Desviación típica de la hora de acostarse, en minutos. */
        val bedtimeSpreadMinutes: Int?,
        val avgSnoreMinutes: Int?,
        val lightMinutes: Int,
        val deepMinutes: Int,
        val remMinutes: Int,
        val awakeMinutes: Int,
        val observations: List<Observation>,
        val plan: Plan?,
    )

    const val MIN_NIGHTS = 3
    private const val MIN_NIGHT_MS = 2 * 60 * 60_000L

    fun summarize(all: List<Night>, ctx: Context = Context(), tz: TimeZone = TimeZone.getDefault()): Summary {
        val nights = all.filter { it.counts }
        val n = nights.size
        val avgSleep = nights.map { it.totalSleepMinutes }.avgInt()
        val avgLatency = nights.mapNotNull { it.latencyMinutes }.avgInt()
        val avgRating = nights.mapNotNull { it.rating?.toDouble() }.let { if (it.isEmpty()) null else it.average() }
        val spread = if (n >= 2) bedtimeSpread(nights.map { it.start }, tz) else null

        return Summary(
            nights = n,
            avgSleepMinutes = avgSleep,
            avgLatencyMinutes = avgLatency,
            avgRating = avgRating,
            bedtimeSpreadMinutes = spread,
            avgSnoreMinutes = nights.map { it.snoreMinutes }.avgInt(),
            lightMinutes = nights.map { it.lightMinutes }.avgInt() ?: 0,
            deepMinutes = nights.map { it.deepMinutes }.avgInt() ?: 0,
            remMinutes = nights.map { it.remMinutes }.avgInt() ?: 0,
            awakeMinutes = nights.map { it.awakeMinutes }.avgInt() ?: 0,
            observations = if (n < MIN_NIGHTS) emptyList() else observations(nights, ctx, avgSleep, avgLatency, spread),
            plan = plan(ctx, avgLatency),
        )
    }

    /**
     * Plan para la noche: con la hora habitual de despertar, la duración de ciclo aprendida
     * y la latencia, el menor número de ciclos completos que alcanza las horas mínimas.
     */
    fun plan(ctx: Context, measuredLatency: Int? = null): Plan? {
        val wake = ctx.wakeMinute ?: return null
        val minHours = ctx.ageRange?.minHours ?: 7
        val cycle = ctx.cycleMinutes
        val cycles = ceil(minHours * 60 / cycle).toInt().coerceIn(4, 6)
        val sleep = (cycles * cycle).roundToInt()
        val latency = (measuredLatency?.toDouble() ?: ctx.latencyMinutes).roundToInt()
        val bed = ((wake - sleep - latency) % (24 * 60) + 24 * 60) % (24 * 60)
        return Plan(bedtimeMinute = bed, wakeMinute = wake, cycles = cycles, sleepMinutes = sleep)
    }

    private fun observations(
        nights: List<Night>, ctx: Context, avgSleep: Int?, avgLatency: Int?, spread: Int?,
    ): List<Observation> {
        val out = mutableListOf<Observation>()
        val range = ctx.ageRange
        val minH = range?.minHours ?: 7
        val maxH = range?.maxHours ?: 9
        val who = range?.let { "para tu edad (${it.label})" } ?: "para adultos"

        avgSleep?.let { m ->
            val text = "En promedio duermes ${hm(m)}. Lo recomendado $who es de $minH a $maxH horas."
            out += when {
                m < (minH - 1) * 60 -> Observation(Tone.ATTENTION, "Estás durmiendo poco", text)
                m < minH * 60 -> Observation(Tone.NEUTRAL, "Un poco menos de lo recomendado", text)
                m <= maxH * 60 -> Observation(Tone.GOOD, "Buena duración de sueño", text)
                else -> Observation(Tone.NEUTRAL, "Duermes más de lo habitual", "$text Si aun así te levantas cansado, vale la pena consultarlo.")
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

        // Ronquidos
        val snoreNights = nights.count { it.snoreMinutes >= 15 }
        val avgSnore = nights.map { it.snoreMinutes }.average().roundToInt()
        out += when {
            snoreNights >= 2 && snoreNights * 2 >= nights.size -> Observation(
                Tone.ATTENTION, "Roncas con frecuencia",
                "Detectamos ronquidos en $snoreNights de ${nights.size} noches (≈ $avgSnore min por noche). Si el ronquido es fuerte, con pausas al respirar o te sientes con sueño de día, consulta a un médico: puede ser señal de apnea del sueño.",
            )
            snoreNights >= 1 -> Observation(
                Tone.NEUTRAL, "Algunos ronquidos",
                "Detectamos ronquidos en $snoreNights de ${nights.size} noches. Dormir de lado y evitar el alcohol antes de dormir suele reducirlos.",
            )
            else -> Observation(Tone.GOOD, "Casi no roncas", "No detectamos ronquidos relevantes en este periodo.")
        }

        // ¿Te sientes mejor cuando despiertas en fase ligera?
        compareRating(
            nights,
            { it.wakeStage == SleepStage.LIGHT || it.wakeStage == SleepStage.AWAKE },
            { it.wakeStage == SleepStage.DEEP || it.wakeStage == SleepStage.REM },
        )?.let { (a, b) ->
            if (a - b >= 0.5) out += Observation(
                Tone.GOOD, "Despertar en fase ligera te sienta mejor",
                "Calificas tus mañanas con ${one(a)}/5 cuando despiertas en fase ligera y ${one(b)}/5 cuando no.",
            )
        }

        // ¿Te sientes mejor cuando duermes las horas recomendadas?
        compareRating(nights, { it.totalSleepMinutes >= minH * 60 }, { it.totalSleepMinutes < minH * 60 })?.let { (a, b) ->
            if (abs(a - b) >= 0.5) out += if (a > b) Observation(
                Tone.GOOD, "Dormir $minH horas o más se nota",
                "Tus mañanas promedian ${one(a)}/5 cuando duermes $minH h o más, y ${one(b)}/5 cuando duermes menos.",
            ) else Observation(
                Tone.NEUTRAL, "Más horas no siempre se sienten mejor",
                "Promedias ${one(a)}/5 con $minH h o más y ${one(b)}/5 con menos. La hora de despertar puede pesar más que la duración.",
            )
        }

        // Factores del día: el efecto más claro de cada uno
        DayFactor.entries.forEach { f -> factorEffect(nights, f)?.let { out += it } }

        // Ciclo menstrual
        cycleEffect(nights)?.let { out += it }

        val snoozeAvg = nights.map { it.snoozes }.average()
        if (snoozeAvg >= 1.0) {
            out += Observation(
                Tone.NEUTRAL, "Sueles posponer la alarma",
                "Pospones ${one(snoozeAvg)} veces por mañana en promedio. Volver a dormirte unos minutos suele dejar más somnolencia que levantarte a la primera.",
            )
        }
        return out
    }

    private fun factorEffect(nights: List<Night>, f: DayFactor): Observation? {
        val with = nights.filter { it.factors and f.bit != 0 }
        val without = nights.filter { it.factors and f.bit == 0 }
        if (with.size < 2 || without.size < 2) return null

        val latW = with.mapNotNull { it.latencyMinutes }.avgInt()
        val latO = without.mapNotNull { it.latencyMinutes }.avgInt()
        val sleepW = with.map { it.totalSleepMinutes }.avgInt()!!
        val sleepO = without.map { it.totalSleepMinutes }.avgInt()!!
        val rate = compareRating(nights, { it.factors and f.bit != 0 }, { it.factors and f.bit == 0 })

        val parts = mutableListOf<String>()
        var better = 0
        var worse = 0
        if (latW != null && latO != null && abs(latW - latO) >= 10) {
            parts += if (latW > latO) "tardas ${latW - latO} min más en dormirte" else "te duermes ${latO - latW} min antes"
            if (latW > latO) worse++ else better++
        }
        if (abs(sleepW - sleepO) >= 30) {
            parts += if (sleepW < sleepO) "duermes ${hm(sleepO - sleepW)} menos" else "duermes ${hm(sleepW - sleepO)} más"
            if (sleepW < sleepO) worse++ else better++
        }
        if (rate != null && abs(rate.first - rate.second) >= 0.5) {
            parts += "te sientes ${one(rate.first)}/5 frente a ${one(rate.second)}/5"
            if (rate.first < rate.second) worse++ else better++
        }
        if (parts.isEmpty()) return null
        val tone = when {
            worse > better -> Tone.ATTENTION
            better > worse -> Tone.GOOD
            else -> Tone.NEUTRAL
        }
        return Observation(
            tone, f.label,
            "Las noches en que ${f.effect}, ${parts.joinToString(", ")}. (${with.size} noches con, ${without.size} sin.)",
        )
    }

    private fun cycleEffect(nights: List<Night>): Observation? {
        val pre = nights.filter { it.premenstrual || it.cyclePhase == MenstrualCycle.Phase.MENSTRUAL }
        val rest = nights.filter { it.cyclePhase != null && !it.premenstrual && it.cyclePhase != MenstrualCycle.Phase.MENSTRUAL }
        if (pre.size < 2 || rest.size < 2) return null
        val sleepP = pre.map { it.totalSleepMinutes }.avgInt()!!
        val sleepR = rest.map { it.totalSleepMinutes }.avgInt()!!
        val rP = pre.mapNotNull { it.rating?.toDouble() }
        val rR = rest.mapNotNull { it.rating?.toDouble() }
        val parts = mutableListOf<String>()
        if (abs(sleepP - sleepR) >= 20) parts += if (sleepP < sleepR) "duermes ${hm(sleepR - sleepP)} menos" else "duermes ${hm(sleepP - sleepR)} más"
        if (rP.size >= 2 && rR.size >= 2 && abs(rP.average() - rR.average()) >= 0.5) {
            parts += "te sientes ${one(rP.average())}/5 frente a ${one(rR.average())}/5"
        }
        if (parts.isEmpty()) return Observation(
            Tone.GOOD, "Tu ciclo no parece afectar tu sueño",
            "En los días previos al periodo y durante él duermes de forma parecida al resto del ciclo.",
        )
        return Observation(
            Tone.NEUTRAL, "Tu ciclo influye en tu descanso",
            "En los días previos al periodo y durante él, ${parts.joinToString(" y ")}. Es común; esos días ayuda acostarte un poco antes.",
        )
    }

    /** Promedio de calificación (a, b) para dos grupos, si cada uno tiene al menos 2. */
    private fun compareRating(nights: List<Night>, a: (Night) -> Boolean, b: (Night) -> Boolean): Pair<Double, Double>? {
        val ra = nights.filter { it.rating != null && a(it) }.map { it.rating!!.toDouble() }
        val rb = nights.filter { it.rating != null && b(it) }.map { it.rating!!.toDouble() }
        if (ra.size < 2 || rb.size < 2) return null
        return ra.average() to rb.average()
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

    private fun List<Int>.avgInt(): Int? = if (isEmpty()) null else average().roundToInt()

    fun hm(m: Int): String = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

    private fun one(d: Double): String = String.format(Locale.US, "%.1f", d).replace('.', ',')
}
