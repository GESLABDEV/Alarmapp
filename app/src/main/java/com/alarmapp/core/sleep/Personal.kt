package com.alarmapp.core.sleep

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** Datos personales que ajustan los reportes. Todo es opcional. */

enum class Sex(val label: String) {
    FEMALE("Mujer"), MALE("Hombre"), UNDISCLOSED("Prefiero no decirlo")
}

/**
 * Rangos de edad con las horas de sueño recomendadas (National Sleep Foundation:
 * 18–25 y 26–64 años, 7 a 9 h; 65 o más, 7 a 8 h).
 */
enum class AgeRange(val label: String, val minHours: Int, val maxHours: Int) {
    YOUNG("18 a 25 años", 7, 9),
    ADULT("26 a 64 años", 7, 9),
    SENIOR("65 años o más", 7, 8),
}

/** Factores del día que se marcan al acostarse. Se guardan como máscara de bits. */
enum class DayFactor(val label: String, val effect: String) {
    CAFFEINE("Café o energizantes tarde", "tomas café o energizantes tarde"),
    ALCOHOL("Alcohol", "tomas alcohol"),
    EXERCISE("Ejercicio", "haces ejercicio"),
    SCREENS("Pantallas antes de dormir", "usas pantallas antes de dormir"),
    HEAVY_MEAL("Cena pesada o tarde", "cenas pesado o tarde"),
    NAP("Siesta", "tomas siesta"),
    STRESS("Estrés", "tienes un día estresante");

    val bit: Int get() = 1 shl ordinal

    companion object {
        fun from(mask: Int): List<DayFactor> = entries.filter { mask and it.bit != 0 }
    }
}

/**
 * Ciclo menstrual estimado con el modelo habitual: la ovulación ocurre unos 14 días antes
 * del siguiente periodo. Es orientativo: no sirve como método anticonceptivo.
 */
object MenstrualCycle {

    const val DEFAULT_CYCLE_DAYS = 28
    const val DEFAULT_PERIOD_DAYS = 5
    private const val DAY = 86_400_000L

    enum class Phase(val label: String) {
        MENSTRUAL("Menstrual"), FOLLICULAR("Folicular"), OVULATORY("Ovulatoria"), LUTEAL("Lútea")
    }

    data class Status(val cycleDay: Int, val phase: Phase, val nextPeriod: Long, val premenstrual: Boolean)

    /**
     * Duración del ciclo aprendida de los inicios registrados (promedio de los últimos 6
     * intervalos plausibles, 21–45 días). Sin datos suficientes devuelve [fallback].
     */
    fun learnedCycleDays(starts: List<Long>, fallback: Int = DEFAULT_CYCLE_DAYS, tz: TimeZone = TimeZone.getDefault()): Int {
        val days = starts.map { dayNumber(it, tz) }.distinct().sorted()
        val gaps = days.zipWithNext { a, b -> (b - a).toInt() }.filter { it in 21..45 }.takeLast(6)
        return if (gaps.isEmpty()) fallback else gaps.average().roundToInt()
    }

    /** Estado del ciclo en el instante [at], o null si no hay ningún inicio anterior registrado. */
    fun status(
        at: Long,
        starts: List<Long>,
        cycleDays: Int,
        periodDays: Int,
        tz: TimeZone = TimeZone.getDefault(),
    ): Status? {
        val today = dayNumber(at, tz)
        val last = starts.map { dayNumber(it, tz) }.filter { it <= today }.maxOrNull() ?: return null
        val cycle = cycleDays.coerceIn(21, 45)
        // Si pasó más de un ciclo sin registrar, se proyecta hacia adelante.
        val elapsed = (today - last).toInt()
        val dayInCycle = elapsed % cycle // 0-based
        val ovulation = cycle - 14 // 0-based, día estimado de ovulación
        val phase = when {
            dayInCycle < periodDays.coerceIn(2, 10) -> Phase.MENSTRUAL
            dayInCycle < ovulation - 1 -> Phase.FOLLICULAR
            dayInCycle <= ovulation + 1 -> Phase.OVULATORY
            else -> Phase.LUTEAL
        }
        val daysToNext = cycle - dayInCycle
        val nextStart = startOfDay(at, tz) + daysToNext * DAY
        return Status(
            cycleDay = dayInCycle + 1,
            phase = phase,
            nextPeriod = nextStart,
            premenstrual = phase == Phase.LUTEAL && daysToNext <= 5,
        )
    }

    private fun dayNumber(ms: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return (ms + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / DAY
    }

    private fun startOfDay(ms: Long, tz: TimeZone): Long = Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * Detección de ronquido en un intervalo, a partir de cuadros de 100 ms del micrófono:
 * nivel (dB) y tasa de cruces por cero (indicador de frecuencia; el ronquido es grave,
 * la voz y los ruidos de fondo suelen ser más agudos o continuos).
 *
 * Se considera ronquido cuando hay al menos 3 "golpes" graves y fuertes, separados con un
 * ritmo parecido al de la respiración (2–8 s) y razonablemente regular. Es una estimación.
 */
object SnoreDetector {

    private const val LOUD_OVER_FLOOR_DB = 12f
    private const val MAX_ZCR = 0.12f
    private const val MIN_BURST_FRAMES = 3      // 300 ms
    private const val MAX_BURST_FRAMES = 25     // 2.5 s
    private const val MIN_BURSTS = 3

    fun isSnoring(frameDb: FloatArray, zcr: FloatArray, frameMs: Long = 100): Boolean {
        val n = minOf(frameDb.size, zcr.size)
        if (n < 50) return false
        val sorted = frameDb.copyOf(n).sorted()
        val floor = sorted[(n * 0.2).toInt()]
        val hit = BooleanArray(n) { frameDb[it] >= floor + LOUD_OVER_FLOOR_DB && zcr[it] <= MAX_ZCR }

        // Rachas de cuadros "graves y fuertes"
        val burstStarts = mutableListOf<Int>()
        var i = 0
        while (i < n) {
            if (hit[i]) {
                var j = i
                while (j + 1 < n && hit[j + 1]) j++
                val len = j - i + 1
                if (len in MIN_BURST_FRAMES..MAX_BURST_FRAMES) burstStarts += i
                i = j + 1
            } else i++
        }
        if (burstStarts.size < MIN_BURSTS) return false

        val intervals = burstStarts.zipWithNext { a, b -> (b - a) * frameMs }
        val minMs = 2_000L
        val maxMs = 8_000L
        val plausible = intervals.filter { it in minMs..maxMs }
        if (plausible.size < MIN_BURSTS - 1) return false
        val mean = plausible.average()
        val sd = kotlin.math.sqrt(plausible.sumOf { (it - mean) * (it - mean) } / plausible.size)
        return sd / mean < 0.45
    }
}
