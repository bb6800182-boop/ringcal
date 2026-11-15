package com.c520.likexiang.data

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * 語音新增的英文版：把 "Meeting with the client tomorrow at 3 pm" 拆成日期、時間、標題，結果跟 [VoiceParser] 同一種。
 * 一樣用規則：認得的日期時間說法拿掉，剩下的去掉贅字當標題。純邏輯，可在 JVM 上測。
 */
object VoiceParserEn {

    private const val CUT = '\u0001' // 拿掉的片段換成這個字元，標題由剩下的片段組成
    private val I = RegexOption.IGNORE_CASE

    private const val DOW = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val MON = "january|february|march|april|may|june|july|august|september|october|november|december|" +
        "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
    private const val ORD = "(?:st|nd|rd|th)?"

    /** 幾點：「3」「3:30」「3 pm」「3 o'clock」；群組依序是時、分、a 或 p。 */
    private const val CLK = "(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*o'?clock)?(?:\\s*([ap])\\.?m\\.?(?![a-z]))?"

    private val ALARM = Regex("\\b(?:with (?:an |the )?alarm|set (?:an |the )?alarm(?: for)?|alarm on|wake me(?: up)?|and ring)\\b", I)
    private val REPEAT = Regex("\\b(?:every (?:other )?(?:day|week|month|year|weekday|morning|evening|night|$DOW)|daily|weekly|monthly|yearly|annually)\\b", I)
    private val ALL_DAY = Regex("\\b(?:all[- ]day|the whole day)\\b", I)
    private val DATE_TO = Regex("\\s*(?:to|through|thru|until|till|-|–)\\s*", I)
    private val TIME_RANGE = Regex("\\b(?:(from|at|between) )?$CLK\\s*(?:to|until|till|and|-|–)\\s*$CLK", I)
    private val TIME_ONE = Regex("\\b(?:(at|by|around) |(@) ?)?$CLK", I)
    private val NOON = Regex("\\b(?:at |by |around )?(noon|midday|midnight)\\b", I)
    private val DAYPART = Regex("\\b(?:(?:in|during) the |this |at )?(morning|afternoon|evening|night)\\b", I)
    private val DURATION = Regex("\\bfor (?:(\\d+)(\\.5)?|(an?|one)|(half an?)) ?(hours?|hrs?|minutes?|mins?)(?: and (?:(a half)|(\\d+) ?(?:minutes?|mins?)))?", I)
    private val DEFAULT_HOUR = mapOf("morning" to 9, "afternoon" to 14, "evening" to 19, "night" to 21)

    private val WORD_NUM = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
    private val WORD_HOUR = Regex("\\b(${WORD_NUM.joinToString("|")})\\b(?=\\s*(?:a\\.?m|p\\.?m|o'?clock|thirty|fifteen|forty[- ]five))", I)
    private val WORD_MIN = Regex("\\b(\\d{1,2}) (thirty|fifteen|forty[- ]five)\\b", I)

    private val LEAD = Regex(
        "^(?:(?:please|can you|could you|hey)[, ]+)*(?:(?:add|create|schedule|set up)\\s+(?:(?:an?|the|new|my)\\s+)*" +
            "(?:(?:calendar )?(?:event|appointment|entry|reminder)\\b\\s*(?:called|named|titled|for|to|about)?\\s*)?|remind me (?:to|about|of)\\s+|i have (?:an? )?|i've got (?:an? )?)?",
        I,
    )
    private val TAIL = Regex("\\s*(?:(?:to|on|in|into) (?:my |the )?calendar|please)$", I)
    private val EDGE_HEAD = Regex("^(?:(?:on|at|in|for|from|to|by|and|called|named|titled)\\b[\\s,]*)+", I)
    private val EDGE_TAIL = Regex("(?:[\\s,]*\\b(?:on|at|in|for|from|to|by|and|the|an?))+$", I)

    /** 日期規則：比對結果、今天 → 日期（與「tonight」順帶的時段）；不合理回傳 null 或丟 DateTimeException。 */
    private val DATES: List<Pair<Regex, (MatchResult, LocalDate) -> Pair<LocalDate, String?>?>> = listOf(
        Regex("\\b(?:the )?day after tomorrow\\b", I) to { _, today -> today.plusDays(2) to null },
        Regex("\\b(tomorrow|today|tonight)\\b", I) to { m, today ->
            when (m.groupValues[1].lowercase()) {
                "tomorrow" -> today.plusDays(1) to null
                "tonight" -> today to "evening"
                else -> today to null
            }
        },
        Regex("\\b(?:on )?(?:(this|next) )?($DOW)\\b", I) to { m, today ->
            val dow = DayOfWeek.valueOf(m.groupValues[2].uppercase())
            val monday = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            val next = today.with(TemporalAdjusters.next(dow))
            // "next Friday"：下一週的那天；接下來那個星期五已經在下一週就不再往後推
            if (m.groupValues[1].equals("next", true) && monday(next) == monday(today)) next.plusWeeks(1) to null else next to null
        },
        Regex("\\b(?:on )?(?:the )?($MON)\\.? (\\d{1,2})$ORD\\b(?:,? (\\d{4}))?", I) to { m, today ->
            monthDay(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3], today) to null
        },
        Regex("\\b(?:on )?(?:the )?(\\d{1,2})$ORD (?:of )?($MON)\\b\\.?(?:,? (\\d{4}))?", I) to { m, today ->
            monthDay(m.groupValues[2], m.groupValues[1].toInt(), m.groupValues[3], today) to null
        },
        Regex("\\b(?:on )?(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b", I) to { m, today ->
            monthDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today) to null
        },
        Regex("\\b(?:on )?the (\\d{1,2})(?:st|nd|rd|th)\\b", I) to { m, today ->
            val day = m.groupValues[1].toInt()
            val thisMonth = today.withDayOfMonth(day)
            (if (thisMonth.isBefore(today)) today.plusMonths(1).withDayOfMonth(day) else thisMonth) to null
        },
    )

    fun parse(raw: String, now: LocalDateTime, defaultDurationMin: Int): VoiceParser.Result {
        val today = now.toLocalDate()
        var s = normalize(raw)

        var alarm = false
        s = ALARM.replace(s) { alarm = true; CUT.toString() }
        var repeating = false
        s = REPEAT.replace(s) { repeating = true; CUT.toString() }
        var allDayWord = false
        s = ALL_DAY.replace(s) { allDayWord = true; CUT.toString() }

        // 日期：第一個認得的日期；後面接 "to" 再一個日期就是好幾天的全天行程
        var date: LocalDate? = null
        var lastDate: LocalDate? = null
        var period: String? = null
        findDate(s, today, 0)?.let { first ->
            date = first.date
            period = first.period
            var cutEnd = first.range.last + 1
            val conn = DATE_TO.find(s, cutEnd)?.takeIf { it.range.first == cutEnd }
            if (conn != null) {
                val second = findDate(s, today, conn.range.last + 1)
                if (second != null && second.range.first == conn.range.last + 1 && !second.date.isBefore(first.date)) {
                    lastDate = second.date
                    cutEnd = second.range.last + 1
                }
            }
            s = s.cut(first.range.first, cutEnd)
        }

        // 時段（morning、evening…）先拿掉：決定上午下午，沒說幾點時當預設時間
        DAYPART.find(s)?.let { m ->
            period = m.groupValues[1].lowercase()
            s = s.cut(m.range.first, m.range.last + 1)
        }

        // 時間：先找「幾點到幾點」，再找單一時間
        var startMin: Int? = null
        var endMin: Int? = null
        for (m in TIME_RANGE.findAll(s)) {
            val g = m.groupValues
            // 沒有 from／at、也沒有 am/pm 或分鐘的「2 to 3」多半不是時間（2 to 3 people）
            val marked = g[1].isNotEmpty() || g[3].isNotEmpty() || g[4].isNotEmpty() || g[6].isNotEmpty() || g[7].isNotEmpty()
            if (!marked) continue
            // "9 to 11 am"：前面沒說上午下午就跟後面一樣
            val a = clock(g[2], g[3], g[4].ifEmpty { g[7] }, period)
            val b = clock(g[5], g[6], g[7].ifEmpty { g[4] }, period)
            if (a != null && b != null) {
                startMin = a
                endMin = b
                s = s.cut(m.range.first, m.range.last + 1)
                break
            }
        }
        if (startMin == null) {
            NOON.find(s)?.let { m ->
                startMin = if (m.groupValues[1].equals("midnight", true)) 0 else 12 * 60
                s = s.cut(m.range.first, m.range.last + 1)
            }
        }
        if (startMin == null) {
            for (m in TIME_ONE.findAll(s)) {
                val g = m.groupValues
                // 單獨一個數字要有 at／@、am/pm 或分鐘才算時間
                if (g[1].isEmpty() && g[2].isEmpty() && g[4].isEmpty() && g[5].isEmpty()) continue
                val t = clock(g[3], g[4], g[5], period) ?: continue
                startMin = t
                s = s.cut(m.range.first, m.range.last + 1)
                break
            }
        }
        if (startMin == null) period?.let { startMin = DEFAULT_HOUR.getValue(it) * 60 }

        var durationMin: Int? = null
        if (startMin != null && endMin == null) {
            DURATION.find(s)?.let { m ->
                durationMin = duration(m)
                s = s.cut(m.range.first, m.range.last + 1)
            }
        }

        val title = title(s)
        val d = date
        val sm = startMin
        return when {
            // 全天：有日期、沒說時間（或說了 all day）
            d != null && (sm == null || allDayWord) ->
                VoiceParser.Result(title, true, d.atStartOfDay(), (lastDate ?: d).plusDays(1).atStartOfDay(), alarm, repeating)
            sm == null -> VoiceParser.Result(title, false, null, null, alarm, repeating)
            else -> {
                // 只說時間：今天；今天那個時間已經過了就是明天
                var start = (d ?: today).atStartOfDay().plusMinutes(sm.toLong())
                if (d == null && !start.isAfter(now)) start = start.plusDays(1)
                val em = endMin
                val dm = durationMin
                val end = when {
                    em != null -> {
                        var e = start.toLocalDate().atStartOfDay().plusMinutes(em.toLong())
                        // "10 to 2" 的 2 點是下午；跨過午夜就是隔天
                        repeat(2) { if (!e.isAfter(start)) e = e.plusHours(12) }
                        e
                    }
                    dm != null -> start.plusMinutes(dm.toLong())
                    else -> start.plusMinutes(defaultDurationMin.toLong().coerceIn(5, 24 * 60L))
                }
                VoiceParser.Result(title, false, start, end, alarm, repeating)
            }
        }
    }

    /** 彎引號轉直的、句尾標點拿掉、"three pm" "3 thirty" 轉成數字。 */
    private fun normalize(raw: String): String {
        var s = raw.take(VoiceParser.MAX_INPUT).replace('’', '\'').replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?')
        s = WORD_HOUR.replace(s) { (WORD_NUM.indexOf(it.groupValues[1].lowercase()) + 1).toString() }
        s = WORD_MIN.replace(s) {
            val min = when (it.groupValues[2].lowercase().first()) { 't' -> "30"; else -> if (it.groupValues[2].lowercase().startsWith("fif")) "15" else "45" }
            "${it.groupValues[1]}:$min"
        }
        return s
    }

    private class DateHit(val range: IntRange, val date: LocalDate, val period: String?)

    /** 從 from 開始最先出現的日期；同一個位置取比對到比較長的。 */
    private fun findDate(s: String, today: LocalDate, from: Int): DateHit? {
        var best: DateHit? = null
        for ((re, build) in DATES) {
            val m = re.find(s, from) ?: continue
            val b = best
            if (b != null && (m.range.first > b.range.first || (m.range.first == b.range.first && m.range.last <= b.range.last))) continue
            val hit = try { build(m, today) } catch (e: DateTimeException) { null } catch (e: IllegalArgumentException) { null } ?: continue
            best = DateHit(m.range, hit.first, hit.second)
        }
        return best
    }

    private fun monthDay(month: String, day: Int, year: String, today: LocalDate): LocalDate {
        val key = month.lowercase().take(3)
        val index = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(key) + 1
        return monthDay(index, day, year, today)
    }

    /** 沒說年份：今年；今年那天已經過了就是明年。 */
    private fun monthDay(month: Int, day: Int, year: String, today: LocalDate): LocalDate {
        if (year.isNotEmpty()) return LocalDate.of(year.toInt(), month, day)
        val d = LocalDate.of(today.year, month, day)
        return if (d.isBefore(today)) LocalDate.of(today.year + 1, month, day) else d
    }

    /** 幾點幾分（從 0:00 起算的分鐘）；ap 是 a、p 或空的，沒說上午下午時看時段，再沒有就 1–7 點當下午。 */
    private fun clock(hour: String, minute: String, ap: String, period: String?): Int? {
        val h = hour.toIntOrNull() ?: return null
        val m = if (minute.isEmpty()) 0 else minute.toInt()
        if (m > 59) return null
        val h24 = when {
            ap.isNotEmpty() -> if (h !in 1..12) return null else if (ap.equals("a", true)) h % 12 else h % 12 + 12
            h > 23 -> return null
            h > 12 -> h
            period == "morning" -> h % 12
            period == "afternoon" || period == "evening" -> h % 12 + 12
            period == "night" -> if (h in 6..11) h + 12 else h % 12
            h == 12 -> 12
            h in 1..7 -> h + 12
            else -> h
        }
        return h24 * 60 + m
    }

    private fun duration(m: MatchResult): Int {
        val g = m.groupValues
        val hours = g[5].lowercase().startsWith("h")
        val base = when {
            g[1].isNotEmpty() -> g[1].toInt() * (if (hours) 60 else 1) + if (g[2].isNotEmpty() && hours) 30 else 0
            g[4].isNotEmpty() -> if (hours) 30 else 1
            else -> if (hours) 60 else 1
        }
        val extra = if (g[6].isNotEmpty()) (if (hours) 30 else 0) else g[7].toIntOrNull() ?: 0
        return (base + extra).coerceIn(5, 24 * 60)
    }

    /** 剩下的片段接起來，去掉頭尾的指令（add、schedule…）、介系詞與標點，第一個字母大寫。 */
    private fun title(s: String): String {
        var t = s.split(CUT).map { trimEdges(it) }.filter { it.isNotEmpty() }.joinToString(" ")
        t = LEAD.replace(t, "")
        t = TAIL.replace(t, "")
        t = trimEdges(t).replace(Regex("\\s+"), " ")
        return t.replaceFirstChar { it.uppercase() }.take(CalendarRepository.MAX_TITLE)
    }

    private fun trimEdges(piece: String): String {
        var t = piece
        while (true) {
            val before = t
            t = t.trim { it.isWhitespace() || it in ",.;:!?-–" }
            t = EDGE_HEAD.replace(t, "")
            t = EDGE_TAIL.replace(t, "")
            if (t == before) return t
        }
    }

    private fun String.cut(from: Int, to: Int): String = substring(0, from) + CUT + substring(to)
}
