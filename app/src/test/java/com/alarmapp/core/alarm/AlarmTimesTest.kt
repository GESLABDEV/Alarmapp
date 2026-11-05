package com.alarmapp.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmTimesTest {

    private val tz = TimeZone.getTimeZone("America/Bogota")

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int): Long =
        Calendar.getInstance(tz).apply { clear(); set(year, month - 1, day, h, m, 0) }.timeInMillis

    // Jueves 5 de noviembre de 2026
    private val thu1100 = at(2026, 11, 5, 11, 0)

    @Test
    fun laterToday_ringsToday() {
        assertEquals(at(2026, 11, 5, 11, 50), AlarmTimes.next(11 * 60 + 50, AlarmTimes.ALL_DAYS, thu1100, tz))
    }

    @Test
    fun earlierToday_ringsTomorrow() {
        assertEquals(at(2026, 11, 6, 6, 0), AlarmTimes.next(6 * 60, AlarmTimes.ALL_DAYS, thu1100, tz))
    }

    @Test
    fun weekdaysOnly_skipsWeekend() {
        val fri0700 = at(2026, 11, 6, 7, 0)
        assertEquals(at(2026, 11, 9, 6, 0), AlarmTimes.next(6 * 60, AlarmTimes.WEEKDAYS, fri0700, tz))
    }

    @Test
    fun weekendOnly() {
        assertEquals(at(2026, 11, 7, 8, 0), AlarmTimes.next(8 * 60, AlarmTimes.WEEKEND, thu1100, tz))
    }

    @Test
    fun sameDayNextWeek_whenOnlyTodayAndPassed() {
        val thursday = 1 shl 3
        assertEquals(at(2026, 11, 12, 6, 0), AlarmTimes.next(6 * 60, thursday, thu1100, tz))
    }

    @Test
    fun nextOf_picksSoonest() {
        val list = listOf(AlarmTimes.Spec(6 * 60, AlarmTimes.ALL_DAYS), AlarmTimes.Spec(11 * 60 + 30, AlarmTimes.ALL_DAYS))
        assertEquals(at(2026, 11, 5, 11, 30), AlarmTimes.nextOf(list, thu1100, tz))
        assertNull(AlarmTimes.nextOf(emptyList(), thu1100, tz))
    }

    @Test
    fun encodeDecode_roundTrip() {
        val list = listOf(AlarmTimes.Spec(360, 127), AlarmTimes.Spec(450, 31))
        assertEquals(list, AlarmTimes.decode(AlarmTimes.encode(list)))
        assertEquals(emptyList<AlarmTimes.Spec>(), AlarmTimes.decode(""))
    }

    @Test
    fun labels() {
        assertEquals("Todos los días", AlarmTimes.label(AlarmTimes.ALL_DAYS))
        assertEquals("Lunes a viernes", AlarmTimes.label(AlarmTimes.WEEKDAYS))
        assertEquals("Lun, Mié, Vie", AlarmTimes.label(0b0010101))
    }
}
