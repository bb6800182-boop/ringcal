package com.c520.likexiang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VoiceParserEnTest {
    private val now = LocalDateTime.of(2026, 10, 11, 10, 0) // 週日上午十點

    private fun p(text: String) = VoiceParserEn.parse(text, now, 60)

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun dateAndTime() {
        p("Meeting with the client tomorrow at 3 pm").let {
            assertEquals("Meeting with the client", it.title)
            assertEquals(at("2026-10-12T15:00"), it.start)
            assertEquals(at("2026-10-12T16:00"), it.end) // 沒說結束：預設長度
            assertFalse(it.allDay)
            assertTrue(it.complete)
        }
        p("Add security meeting on December 1 from 9 am to 12 pm").let {
            assertEquals("Security meeting", it.title)
            assertEquals(at("2026-12-01T09:00"), it.start)
            assertEquals(at("2026-12-01T12:00"), it.end)
        }
        assertEquals(at("2026-10-11T20:00"), p("Dinner tonight at 8").start)
        assertEquals(at("2026-10-12T07:30"), p("Run tomorrow morning at 7:30").start)
        assertEquals(at("2026-10-12T09:00"), p("Standup tomorrow morning").start)
        assertEquals(at("2026-10-13T15:30"), p("Schedule a dentist appointment the day after tomorrow at three thirty p.m.").start)
        assertEquals("Dentist appointment", p("Schedule a dentist appointment the day after tomorrow at three thirty p.m.").title)
    }

    @Test
    fun weekdaysAndDates() {
        assertEquals(at("2026-10-16T10:30"), p("Dentist on Friday at 10:30").start)
        assertEquals(at("2026-10-16T10:30"), p("Dentist next Friday at 10:30").start) // 週日說的 next Friday 就是下一週的星期五
        assertEquals(at("2026-10-18T00:00"), p("Brunch on Sunday").start) // 今天是週日：下一個週日
        assertEquals(at("2026-11-05T00:00"), p("Rent due on the 5th of November").start)
        assertEquals(at("2027-03-02T00:00"), p("Trip on March 2nd").start) // 今年的已經過了
        assertEquals(at("2026-12-25T00:00"), p("Birthday party on 12/25").start)
        assertEquals(at("2026-10-15T00:00"), p("Pay bills on the 15th").start)
    }

    @Test
    fun allDayAndRanges() {
        p("Team offsite October 20 to October 22").let {
            assertEquals("Team offsite", it.title)
            assertTrue(it.allDay)
            assertEquals(at("2026-10-20T00:00"), it.start)
            assertEquals(at("2026-10-23T00:00"), it.end)
        }
        p("Conference all day tomorrow").let {
            assertTrue(it.allDay)
            assertEquals("Conference", it.title)
        }
        p("Workshop tomorrow from 10 to 2").let {
            assertEquals(at("2026-10-12T10:00"), it.start)
            assertEquals(at("2026-10-12T14:00"), it.end) // 10 到 2：2 是下午
        }
        p("Lunch at noon for 90 minutes").let {
            assertEquals("Lunch", it.title)
            assertEquals(at("2026-10-11T12:00"), it.start)
            assertEquals(at("2026-10-11T13:30"), it.end)
        }
        assertEquals(at("2026-10-12T17:30"), p("Review tomorrow at 4 pm for an hour and a half").end)
    }

    @Test
    fun timeOnlyAndGuesses() {
        // 只說時間、今天已經過了：明天
        assertEquals(at("2026-10-12T09:00"), p("Call mom at 9").start)
        // 沒說上午下午：1–7 點當下午
        assertEquals(at("2026-10-11T15:00"), p("Pick up the kids at 3").start)
        // 單獨的數字不是時間
        p("Buy 2 tickets tomorrow").let {
            assertTrue(it.allDay)
            assertEquals("Buy 2 tickets", it.title)
        }
        p("Table for 2 to 3 people on Friday").let { assertTrue(it.allDay) }
    }

    @Test
    fun alarmRepeatAndIncomplete() {
        p("Flight to Tokyo tomorrow at 6 am with an alarm").let {
            assertTrue(it.alarm)
            assertEquals("Flight to Tokyo", it.title)
            assertEquals(at("2026-10-12T06:00"), it.start)
        }
        p("Yoga every Monday at 7 pm").let {
            assertTrue(it.repeating)
            assertFalse(it.complete)
        }
        p("Buy milk").let {
            assertNull(it.start)
            assertEquals("Buy milk", it.title)
            assertFalse(it.complete)
        }
        assertFalse(p("tomorrow at 5").complete) // 沒有標題
    }
}
