package com.c520.likexiang.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** 英文介面的日期與時間長度寫法（L 看的是 Locale.getDefault()，測完要還原）。 */
class FmtEnTest {
    private val saved = Locale.getDefault()

    @Before
    fun english() = Locale.setDefault(Locale.US)

    @After
    fun restore() = Locale.setDefault(saved)

    @Test
    fun durations() {
        assertEquals("At start", Fmt.reminderLabel(0))
        assertEquals("10 minutes before", Fmt.reminderLabel(10))
        assertEquals("1 hour before", Fmt.reminderLabel(60))
        assertEquals("2 weeks before", Fmt.reminderLabel(2 * 7 * 1440))
        assertEquals("1 day 2 hours 5 minutes", Fmt.duration(1440 + 125))
        assertEquals("On time", Fmt.leadLabel(0))
        assertEquals("1 hour 30 minutes early", Fmt.leadLabel(90))
        assertEquals("1 hr", Fmt.offsetChip(60))
    }

    @Test
    fun dates() {
        val d = LocalDate.of(2026, 10, 11) // 週日
        assertEquals("Oct 11", Fmt.dateShort(d))
        assertEquals("Sun, Oct 11", Fmt.dateWithDow(d))
        assertEquals("Sunday", Fmt.weekday(d))
        assertEquals("S", Fmt.dowChar(d))
        assertEquals("October 2026", Fmt.monthTitle(2026, 10, true))
        assertEquals("Oct–Nov", Fmt.monthSpan(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 8), d))
        assertEquals("Dec 2026 – Jan 2027", Fmt.monthSpan(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), d))
    }
}
