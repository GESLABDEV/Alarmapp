package com.alarmapp.core.alarm

import java.util.Calendar
import java.util.TimeZone

/**
 * Cálculo de la próxima alarma con días de la semana. Sin dependencias de Android.
 * Días como máscara de bits: bit 0 = lunes … bit 6 = domingo.
 */
object AlarmTimes {

    const val ALL_DAYS = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    /** Letras para la interfaz, de lunes a domingo. */
    val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")
    private val DAY_NAMES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /** Índice 0..6 (lunes..domingo) a partir de Calendar.DAY_OF_WEEK. */
    fun dayIndex(calendarDayOfWeek: Int): Int = (calendarDayOfWeek + 5) % 7

    fun has(days: Int, index: Int): Boolean = days and (1 shl index) != 0

    fun toggle(days: Int, index: Int): Int = days xor (1 shl index)

    /**
     * Próxima vez que suena una alarma a [minuteOfDay] en los [days] indicados,
     * estrictamente más de un minuto después de [from].
     */
    fun next(minuteOfDay: Int, days: Int, from: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val mask = if (days and ALL_DAYS == 0) ALL_DAYS else days
        val cal = Calendar.getInstance(tz)
        for (d in 0..7) {
            cal.timeInMillis = from
            cal.add(Calendar.DAY_OF_YEAR, d)
            cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            cal.set(Calendar.MINUTE, minuteOfDay % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > from + 60_000 && has(mask, dayIndex(cal.get(Calendar.DAY_OF_WEEK)))) {
                return cal.timeInMillis
            }
        }
        error("Siempre hay un día válido en 8 días")
    }

    data class Spec(val minuteOfDay: Int, val days: Int)

    /** La más próxima entre varias alarmas, o null si no hay ninguna. */
    fun nextOf(alarms: List<Spec>, from: Long, tz: TimeZone = TimeZone.getDefault()): Long? =
        alarms.minOfOrNull { next(it.minuteOfDay, it.days, from, tz) }

    fun label(days: Int): String {
        val mask = if (days and ALL_DAYS == 0) ALL_DAYS else days and ALL_DAYS
        return when (mask) {
            ALL_DAYS -> "Todos los días"
            WEEKDAYS -> "Lunes a viernes"
            WEEKEND -> "Fines de semana"
            else -> (0..6).filter { has(mask, it) }.joinToString(", ") { DAY_NAMES[it] }
        }
    }

    // Formato compacto para guardar la lista fuera de la base cifrada: "360:127,450:31"
    fun encode(alarms: List<Spec>): String = alarms.joinToString(",") { "${it.minuteOfDay}:${it.days}" }

    fun decode(s: String?): List<Spec> = s.orEmpty().split(",").mapNotNull { part ->
        val bits = part.split(":")
        val m = bits.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val d = bits.getOrNull(1)?.toIntOrNull() ?: ALL_DAYS
        if (m !in 0 until 24 * 60) null else Spec(m, d)
    }
}
