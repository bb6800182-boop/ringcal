package com.c520.likexiang.widget

import com.c520.likexiang.L
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.c520.likexiang.R
import com.c520.likexiang.data.EventInstance
import com.c520.likexiang.data.WidgetRange
import com.c520.likexiang.ui.Fmt
import com.c520.likexiang.ui.layout.WeekLayout
import com.c520.likexiang.ui.layout.WeekLayoutEngine
import com.c520.likexiang.ui.layout.WeekSpan
import com.c520.likexiang.ui.layout.weekSpans
import com.c520.likexiang.ui.theme.Tok
import java.time.LocalDate
import java.time.ZoneId

/**
 * 月曆小工具（WID-01）：跟 App 月檢視一樣的整頁月曆格，每天直接列出行程。
 * 可切換上下月、「＋」新增，點日期開那一天的日檢視。
 */
class MonthWidget : GlanceAppWidget() {
    /**
     * 只給一種版面：月曆格是整張畫好的圖（見 [GridPainter]），圖的大小照桌面回報的小工具大小算，
     * 顯示時拉到整格，所以回報的大小不準也只是稍微變形，不會被切掉。
     * （1.4.1 以前給四種高度、每個行程一個元件；行程多的月份會撞到一個版面 500 個元件的上限，只好少排幾條。）
     */
    override val sizeMode = SizeMode.Single
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Widgets.usePalette(context)
        // 先載好再顯示，更新時不會閃一下「載入中」
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val first = WidgetData.month(context, prefs[Widgets.OFFSET] ?: 0, prefs[Widgets.REFRESH] ?: 0)
        val size = widgetSize(context, id)
        provideContent {
            val offset = currentState(Widgets.OFFSET) ?: 0
            val refresh = currentState(Widgets.REFRESH) ?: 0
            var model by remember { mutableStateOf(first) }
            LaunchedEffect(offset, refresh) {
                if (offset != model.offset || refresh != model.refresh) model = WidgetData.month(context, offset, refresh)
            }
            MonthContent(model, size)
        }
    }
}

/** 小工具目前的大小（直向或橫向那一組）；桌面沒回報就當成整個螢幕。不會超過螢幕。 */
private suspend fun widgetSize(context: Context, id: GlanceId): DpSize {
    val dm = context.resources.displayMetrics
    val screen = DpSize((dm.widthPixels / dm.density).dp, (dm.heightPixels / dm.density).dp)
    val sizes = runCatching { GlanceAppWidgetManager(context).getAppWidgetSizes(id) }.getOrDefault(emptyList())
    if (sizes.isEmpty()) return screen
    val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val s = if (landscape) sizes.maxBy { it.width } else sizes.minBy { it.width }
    return DpSize(minOf(s.width, screen.width), minOf(s.height, screen.height))
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.scheduleMidnight(context)
    }
}

/** 上下月與回到這個月。位移限制在前後十年內。 */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { p ->
            p[Widgets.OFFSET] = if (delta == 0) 0 else ((p[Widgets.OFFSET] ?: 0) + delta).coerceIn(-120, 120)
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}

// 版面：跟 App 的月曆格同一組 token（DESIGN.md）
private val HEADER_H = 40.dp
private val ROOT_PAD = 2.dp

/** 色塊之間的間距，跟 App 一樣 2dp（DESIGN.md「Layout」）。 */
private val CHIP_GAP = 2.dp

/** 月曆格的尺寸，跟著系統字級放大（小工具的字會跟著字級變大，高度也要跟著）。 */
internal class GridDims(fontScale: Float) {
    /** 今天的紫色圓。 */
    val mark = 16.dp * fontScale

    /** 日期＋農曆那一行。 */
    val label = mark + 2.dp

    /** 一條車道：色塊（9sp 一行字）加下面的間距。 */
    val slot = 11.dp * fontScale + 3.dp + CHIP_GAP

    /** 星期列的高度。 */
    val weekday = 10.dp * fontScale * 1.5f + 4.dp
}

/**
 * 一週那一列的高度放得下幾條色塊：扣掉上方 1dp 格線和日期列，最後一條下面的間距可以被切掉。
 * 排到格子滿為止，放不下的行程在最後一條顯示「+N」；0 表示只放得下日期，改用色點。
 */
internal fun lanesForRow(rowHeight: Dp, d: GridDims): Int {
    val room = rowHeight - 1.dp - d.label
    return ((room + CHIP_GAP) / d.slot).toInt().coerceAtLeast(0)
}

/** 月曆格（圖）的大小：小工具扣掉外距、頂部列和星期列。 */
internal fun gridSize(widget: DpSize, d: GridDims): DpSize = DpSize(
    (widget.width - ROOT_PAD * 2).coerceAtLeast(120.dp),
    (widget.height - ROOT_PAD * 2 - HEADER_H - d.weekday).coerceAtLeast(40.dp),
)

/** 整個小工具：頂部列、星期列，下面是整張畫好的月曆格，上面疊一層透明的格子接點擊。 */
@Composable
private fun MonthContent(m: MonthModel, widget: DpSize) {
    val context = LocalContext.current
    val dims = GridDims(context.resources.configuration.fontScale)
    Column(
        GlanceModifier.fillMaxSize().background(Widgets.color(Tok.canvas)).cornerRadius(Tok.rXl).padding(ROOT_PAD),
    ) {
        Header(m)
        if (!m.ok) {
            Message(L.t("請先開啟${LocalContext.current.getString(R.string.app_name)}，允許讀取日曆", "Open ${LocalContext.current.getString(R.string.app_name)} first and allow calendar access"))
            return@Column
        }
        val cols = m.weeks.first().size
        WeekdayRow(m.weeks.first(), dims)
        val zone = ZoneId.systemDefault()
        val spans = m.weeks.map { weekSpans(m.instances, it.first(), zone, cols) }
        val grid = gridSize(widget, dims)
        val lanes = lanesForRow(grid.height / m.weeks.size, dims)
        val layouts = spans.map { if (lanes > 0) WeekLayoutEngine.layout(it, lanes) else null }
        val bitmap = GridPainter(context, m, dims, cols, lanes).draw(grid, spans, layouts)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            // 點擊與螢幕閱讀器：每天一格透明的格子，疊在圖上面
            Column(GlanceModifier.fillMaxSize()) {
                m.weeks.forEachIndexed { i, week ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        week.forEachIndexed { col, day ->
                            val events = eventsOn(m, spans[i], col)
                            Box(
                                GlanceModifier.defaultWeight().fillMaxHeight()
                                    .clickable(actionStartActivity(Widgets.openDay(context, day)))
                                    .semantics { contentDescription = dayDescription(m, day, events) },
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

/** 這一欄（這一天）的行程，照月曆的順序。 */
private fun eventsOn(m: MonthModel, spans: List<WeekSpan>, col: Int): List<EventInstance> =
    spans.filter { col in it.startCol until it.startCol + it.span }.sortedBy { it.sortKey }.map { m.instances[it.id] }

/**
 * 把月曆格畫成一張圖：格線、今天的淡底、日期與農曆、行程色塊（跨日的接成一條）、「+N」。
 * 小工具一個版面最多 500 個元件，一個行程一個元件的話，行程多的月份排不滿格子；畫成圖就沒有這個上限。
 * 樣式跟 App 的月曆格同一組 token（DESIGN.md）。
 */
private class GridPainter(context: Context, private val m: MonthModel, private val d: GridDims, private val cols: Int, private val lanes: Int) {
    private val density = context.resources.displayMetrics.density
    private val fontScale = context.resources.configuration.fontScale
    private val medium = Typeface.create(Typeface.DEFAULT, 500, false)
    private val bold = Typeface.create(Typeface.DEFAULT, 700, false)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun px(v: Dp): Float = v.value * density

    private fun text(sp: Float, color: Int, face: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp * density * fontScale
        this.color = color
        typeface = face
    }

    /** 文字垂直置中在 cy 的基線位置。 */
    private fun baseline(cy: Float, p: Paint): Float = cy - (p.ascent() + p.descent()) / 2

    fun draw(size: DpSize, spans: List<List<WeekSpan>>, layouts: List<WeekLayout?>): Bitmap {
        val w = px(size.width).toInt().coerceAtLeast(1)
        val h = px(size.height).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val rowH = h.toFloat() / m.weeks.size
        val colW = w.toFloat() / cols
        val gap = px(CHIP_GAP)
        val line = px(1.dp)
        val hairline = Tok.hairline.toArgb()

        // 日與日之間的直線：畫在每欄右邊留的間距裡，色塊蓋在上面，跨日行程才接得起來
        fill.color = hairline
        for (col in 1 until cols) c.drawRect(col * colW - line, 0f, col * colW, h.toFloat(), fill)

        m.weeks.forEachIndexed { i, week ->
            val top = i * rowH
            fill.color = hairline
            c.drawRect(0f, top, w.toFloat(), top + line, fill)
            week.forEachIndexed { col, day ->
                val left = col * colW
                val right = left + colW - gap
                if (day == m.today) {
                    // 今天整格鋪淡底（重點色疊在底色上），一眼找得到
                    fill.color = Tok.todayTint.toArgb()
                    c.drawRect(left, top + line, right, top + rowH, fill)
                }
                label(c, day, left, right, top + line)
            }
            val lanesTop = top + line + px(d.label)
            val layout = layouts[i]
            if (layout == null) {
                week.indices.forEach { col -> dots(c, eventsOn(m, spans[i], col), col * colW, col * colW + colW - gap, lanesTop) }
            } else {
                val slot = px(d.slot)
                layout.placed.forEach { p ->
                    val y = lanesTop + p.lane * slot
                    chip(c, m.instances[p.id], p.startCol * colW, (p.startCol + p.span) * colW - gap, y, y + slot - gap, multiDay = p.span > 1)
                }
                layout.more.forEach { (col, n) ->
                    val y = lanesTop + (lanes - 1) * slot
                    val p = text(9f, Tok.slate.toArgb(), medium).apply { textAlign = Paint.Align.CENTER }
                    c.drawText("+$n", col * colW + (colW - gap) / 2, baseline(y + (slot - gap) / 2, p), p)
                }
            }
        }
        return bitmap
    }

    /** 日期列：數字置中，今天是紫色圓（DESIGN.md today-marker），後面接農曆。 */
    private fun label(c: Canvas, day: LocalDate, left: Float, right: Float, top: Float) {
        val isToday = day == m.today
        val inMonth = m.month == null || day.month == m.month.month
        val lunar = m.lunar[day]?.takeIf { it.isNotEmpty() }
        val number = "${day.dayOfMonth}"
        val cy = top + px(d.label) / 2
        val numberPaint = if (isToday) text(11f, Tok.onPrimary.toArgb(), bold) else text(11f, (if (inMonth) Tok.ink else Tok.steel).toArgb(), medium)
        val mark = px(d.mark)
        val numberW = if (isToday) mark else numberPaint.measureText(number)
        val lunarPaint = lunar?.let {
            text(8f, (if (isToday) Tok.primary else if (inMonth) Tok.slate else Tok.steel).toArgb())
        }
        val lunarGap = px(2.dp)
        val lunarW = if (lunar != null && lunarPaint != null) lunarGap + lunarPaint.measureText(lunar) else 0f
        // 放不下就只置中數字，農曆往右排、超出的部分切掉
        val total = if (numberW + lunarW <= right - left) numberW + lunarW else numberW
        val x = left + (right - left - total) / 2
        if (isToday) {
            fill.color = Tok.primary.toArgb()
            c.drawCircle(x + mark / 2, cy, mark / 2, fill)
            numberPaint.textAlign = Paint.Align.CENTER
            c.drawText(number, x + mark / 2, baseline(cy, numberPaint), numberPaint)
        } else {
            c.drawText(number, x, baseline(cy, numberPaint), numberPaint)
        }
        if (lunar != null && lunarPaint != null) {
            c.save()
            c.clipRect(left, top, right, top + px(d.label))
            c.drawText(lunar, x + numberW + lunarGap, baseline(cy, lunarPaint), lunarPaint)
            c.restore()
        }
    }

    /** 行程色塊：單日用圓角（event-chip），跨日直角接成一條；標題寫在這週的第一格，放不下的切掉。 */
    private fun chip(c: Canvas, inst: EventInstance, left: Float, right: Float, top: Float, bottom: Float, multiDay: Boolean) {
        fill.color = Tok.eventColor(inst.color).toArgb()
        if (multiDay) {
            c.drawRect(left, top, right, bottom, fill)
        } else {
            val r = px(Tok.rXs)
            c.drawRoundRect(left, top, right, bottom, r, r, fill)
        }
        val pad = px(2.dp)
        val p = text(9f, Tok.onEventColor(inst.color).toArgb(), medium)
        c.save()
        c.clipRect(left + pad, top, right - pad, bottom)
        c.drawText(chipTitle(m, inst), left + pad, baseline((top + bottom) / 2, p), p)
        c.restore()
    }

    /** 小工具太矮、放不下色塊時改用色點（WID-01）：最多三個，顏色跟行程一樣。 */
    private fun dots(c: Canvas, events: List<EventInstance>, left: Float, right: Float, top: Float) {
        val shown = events.take(3)
        if (shown.isEmpty()) return
        val r = px(2.5.dp)
        val step = r * 2 + px(2.dp)
        var x = (left + right) / 2 - step * (shown.size - 1) / 2
        shown.forEach { e ->
            fill.color = Tok.eventColor(e.color).toArgb()
            c.drawCircle(x, top + r + px(1.dp), r, fill)
            x += step
        }
    }
}

@Composable
private fun Header(m: MonthModel) {
    val context = LocalContext.current
    val monthTitle = m.title
    Row(GlanceModifier.fillMaxWidth().height(HEADER_H).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().padding(start = 6.dp).clickable(actionStartActivity(Widgets.openApp(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(monthTitle, style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (m.lunarRange.isNotEmpty()) {
                Text("  ${m.lunarRange}", style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 11.sp), maxLines = 1)
            }
        }
        if (m.offset != 0) {
            // App 頂部的「今天」（button-outline）；小工具畫不了外框，改用 surface 底色
            Text(
                L.t("今天", "Today"),
                style = TextStyle(color = Widgets.color(Tok.ink), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.padding(horizontal = Tok.sSm, vertical = 6.dp).cornerRadius(Tok.rMd)
                    .background(Widgets.color(Tok.surface)).clickable(shiftMonth(0))
                    .semantics { contentDescription = L.t("回到今天", "Go to today") },
                maxLines = 1,
            )
        }
        val unit = when (m.range) {
            WidgetRange.MONTH -> null
            WidgetRange.WEEKS -> L.n(m.weeks.size, "週", "week")
            WidgetRange.DAYS -> L.n(m.weeks.first().size, "天", "day")
        }
        WidgetIconButton(R.drawable.ic_chevron_left, unit?.let { L.t("前 $it", "Previous $it") } ?: L.t("上個月", "Previous month"), shiftMonth(-1))
        WidgetIconButton(R.drawable.ic_chevron_right, unit?.let { L.t("後 $it", "Next $it") } ?: L.t("下個月", "Next month"), shiftMonth(1))
        WidgetIconButton(R.drawable.ic_mic, L.t("用語音新增行程", "Add event by voice"), actionStartActivity(Widgets.voice(context)))
        WidgetIconButton(R.drawable.ic_plus, L.t("新增行程", "New event"), actionStartActivity(Widgets.newEvent(context)), primary = true)
    }
}

private fun shiftMonth(delta: Int): Action = actionRunCallback<ShiftMonthAction>(actionParametersOf(ShiftMonthAction.DELTA to delta))

/** 星期列：照第一列的日期寫（「N 天」從哪天開始都對得上）。高度固定成 [GridDims.weekday]，月曆格的圖才算得準。 */
@Composable
private fun WeekdayRow(firstRow: List<LocalDate>, d: GridDims) {
    Row(GlanceModifier.fillMaxWidth().height(d.weekday), verticalAlignment = Alignment.CenterVertically) {
        firstRow.map { Fmt.dowChar(it) }.forEach {
            Text(
                it,
                style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 10.sp, textAlign = TextAlign.Center),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
        }
    }
}

private fun chipTitle(m: MonthModel, inst: EventInstance): String = if (inst.key in m.alarmOn) "⏰${inst.displayTitle}" else inst.displayTitle

private fun dayDescription(m: MonthModel, day: LocalDate, events: List<EventInstance>): String = buildString {
    append(Fmt.dateWithDow(day))
    if (day == m.today) append(L.t("，今天", ", today"))
    m.lunar[day]?.let { append(L.t("，農曆", ", lunar ")).append(it) }
    if (events.isEmpty()) {
        append(L.t("，沒有行程", ", no events"))
    } else {
        append(L.t("，${events.size} 個行程：", ", ${events.size} events: "))
        append(events.take(5).joinToString(L.t("、", ", ")) { if (it.key in m.alarmOn) L.t("${it.displayTitle}（鬧鐘）", "${it.displayTitle} (alarm)") else it.displayTitle })
        if (events.size > 5) append(L.t(" 等", " and more"))
    }
}

@Composable
private fun Message(text: String) {
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Widgets.openApp(LocalContext.current))), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = Widgets.color(Tok.slate), fontSize = 13.sp, textAlign = TextAlign.Center))
    }
}
