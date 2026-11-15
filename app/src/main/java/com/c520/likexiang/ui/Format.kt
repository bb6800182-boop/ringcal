package com.c520.likexiang.ui

import com.c520.likexiang.L
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    /** 一種語言的日期格式；中文、英文各一組，跟著手機語言選（見 L）。 */
    private class Pats(val locale: Locale, short: String, withDow: String, long: String, dowChar: String) {
        val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val dateShort: DateTimeFormatter = DateTimeFormatter.ofPattern(short, locale)
        val dateWithDow: DateTimeFormatter = DateTimeFormatter.ofPattern(withDow, locale)
        val dateLong: DateTimeFormatter = DateTimeFormatter.ofPattern(long, locale)
        val dowChar: DateTimeFormatter = DateTimeFormatter.ofPattern(dowChar, locale)
        val weekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", locale)
    }

    private val zh = Pats(Locale.TRADITIONAL_CHINESE, "M月d日", "M月d日（E）", "M月d日 EEEE", "E")
    private val en = Pats(Locale.ENGLISH, "MMM d", "EEE, MMM d", "EEEE, MMM d", "EEEEE")
    private val p: Pats get() = if (L.zh) zh else en

    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).format(p.time)
    fun time(t: LocalDateTime): String = t.format(p.time)
    fun dateShort(d: LocalDate): String = d.format(p.dateShort)
    fun dateWithDow(d: LocalDate): String = d.format(p.dateWithDow)
    fun dateLong(d: LocalDate): String = d.format(p.dateLong)

    /** 「星期日」／"Sunday"。 */
    fun weekday(d: LocalDate): String = d.format(p.weekday)

    /** 「一」「二」…（中文星期縮寫的最後一個字）；英文是 "M" "T" …。 */
    fun dowChar(d: LocalDate): String = d.format(p.dowChar).takeLast(1)

    private fun monthName(month: Int, style: TextStyle): String = Month.of(month).getDisplayName(style, Locale.ENGLISH)

    fun monthTitle(year: Int, month: Int, showYear: Boolean): String = when {
        L.zh -> if (showYear) "${year}年${month}月" else "${month}月"
        // 英文用縮寫（Nov）：全名在標題列會被擠成兩行
        showYear -> "${monthName(month, TextStyle.SHORT)} $year"
        else -> monthName(month, TextStyle.SHORT)
    }

    /** 「N 天」「N 週」檢視的標題只寫月份（「10月」「10–11月」），日期看格子；不是今年才加年份。 */
    fun monthSpan(first: LocalDate, last: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        first.year != last.year -> L.t(
            "${first.year}年${first.monthValue}月–${last.year}年${last.monthValue}月",
            "${monthName(first.monthValue, TextStyle.SHORT)} ${first.year} – ${monthName(last.monthValue, TextStyle.SHORT)} ${last.year}",
        )
        first.month == last.month -> monthTitle(first.year, first.monthValue, first.year != today.year)
        else -> L.t(
            (if (first.year != today.year) "${first.year}年" else "") + "${first.monthValue}–${last.monthValue}月",
            "${monthName(first.monthValue, TextStyle.SHORT)}–${monthName(last.monthValue, TextStyle.SHORT)}" + if (first.year != today.year) " ${first.year}" else "",
        )
    }

    /** 行程時間的完整描述，全天行程用 UTC 換日期。 */
    fun range(begin: Long, end: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (allDay) {
            val s = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val e = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            return if (!e.isAfter(s)) L.t("${dateWithDow(s)} 整天", "${dateWithDow(s)}, all day") else "${dateWithDow(s)} – ${dateWithDow(e)}"
        }
        val s = Instant.ofEpochMilli(begin).atZone(zone)
        val e = Instant.ofEpochMilli(end).atZone(zone)
        return if (s.toLocalDate() == e.toLocalDate() || end <= begin) {
            "${dateWithDow(s.toLocalDate())} ${s.format(p.time)} – ${e.format(p.time)}"
        } else {
            "${dateWithDow(s.toLocalDate())} ${s.format(p.time)} – ${dateWithDow(e.toLocalDate())} ${e.format(p.time)}"
        }
    }

    /** 一段時間：「1 週」「2 天」「1 小時 30 分鐘」「45 分鐘」；英文 "1 week" "1 hour 30 minutes"。 */
    fun duration(minutes: Int): String {
        if (minutes <= 0) return L.n(0, "分鐘", "minute")
        if (minutes % WEEK_MIN == 0) return L.n(minutes / WEEK_MIN, "週", "week")
        val d = minutes / DAY_MIN
        val h = minutes % DAY_MIN / 60
        val m = minutes % 60
        return listOfNotNull(
            L.n(d, "天", "day").takeIf { d > 0 },
            L.n(h, "小時", "hour").takeIf { h > 0 },
            L.n(m, "分鐘", "minute").takeIf { m > 0 },
        ).joinToString(" ")
    }

    /** 一般通知：「開始時」「10 分鐘前」「1 天前」。 */
    fun reminderLabel(minutes: Int): String = if (minutes == 0) L.t("開始時", "At start") else L.t("${duration(minutes)}前", "${duration(minutes)} before")

    /** 鬧鐘提前時間：「準時響」「提前 10 分鐘」。 */
    fun leadLabel(minutes: Int): String = if (minutes == 0) L.t("準時響", "On time") else L.t("提前 ${duration(minutes)}", "${duration(minutes)} early")

    fun offsetChip(minutes: Int): String = when {
        minutes == 0 -> L.t("準時", "On time")
        minutes % 60 == 0 -> L.t("${minutes / 60} 小時", "${minutes / 60} hr")
        else -> L.t("$minutes 分", "$minutes min")
    }

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    private const val DAY_MIN = 24 * 60
    private const val WEEK_MIN = 7 * DAY_MIN
}
