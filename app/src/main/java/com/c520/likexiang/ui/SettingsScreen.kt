package com.c520.likexiang.ui

import com.c520.likexiang.L
import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import com.c520.likexiang.data.WidgetRange
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.annotation.DrawableRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.likexiang.About
import com.c520.likexiang.BuildConfig
import com.c520.likexiang.R
import com.c520.likexiang.data.CalendarInfo
import com.c520.likexiang.data.Settings
import com.c520.likexiang.data.defaultCalendarFor
import com.c520.likexiang.ui.theme.Palette
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DEFAULT_RINGTONE: String get() = L.t("系統預設鬧鐘鈴聲", "System default alarm sound")

/** 設定頁點一列跳出的對話框。 */
private enum class SettingsDialog { OFFSET, DAY_START, CALENDAR, LICENSES, ACCENT, CANVAS }

/**
 * 設定：依用途分成幾張卡片（鬧鐘、新增行程、檢視、外觀、月曆小工具、顯示的日曆、權限、關於），
 * 卡片之間留白、各有標題，不會全部擠在一起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var colorFor by remember { mutableStateOf<CalendarInfo?>(null) } // 正在換顏色的日曆
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            vm.updateSettings { it.copy(ringtoneUri = uri?.toString()) }
        }
    }
    // 鈴聲名稱要查媒體資料庫，放到背景執行緒；查完把 Ringtone 停掉，不留著播放器
    val ringtoneName by produceState(DEFAULT_RINGTONE, settings.ringtoneUri) {
        value = withContext(Dispatchers.IO) {
            settings.ringtoneUri?.let { s ->
                runCatching {
                    RingtoneManager.getRingtone(context, Uri.parse(s))?.let { r -> r.getTitle(context).also { r.stop() } }
                }.getOrNull()
            } ?: DEFAULT_RINGTONE
        }
    }
    val defaultCalendar = defaultCalendarFor(calendars, settings)

    Column(Modifier.fillMaxSize().background(Tok.surface)) {
        Box(Modifier.background(Tok.canvas)) { TopBar(L.t("設定", "Settings"), navIcon = R.drawable.ic_back, onNav = { vm.back() }) }
        Divider()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Tok.sMd)) {
            SettingsCard(L.t("鬧鐘", "Alarms"), R.drawable.ic_alarm) {
                SettingRow(L.t("預設提前時間", "Default lead time"), Fmt.leadLabel(settings.defaultOffsetMin), L.t("電腦上加 ⏰ 的行程也用這個", "Also used for events you mark with ⏰ on a computer")) { dialog = SettingsDialog.OFFSET }
                CardDivider()
                ChoiceBlock(L.t("稍後提醒", "Snooze"), L.t("響鈴時按「幾分後再響」，隔多久再響一次", "How long to wait after you tap snooze on the ringing screen")) {
                    Settings.SNOOZE_OPTIONS.forEach { m ->
                        OptionChip(L.n(m, "分鐘", "minute"), m == settings.snoozeMin) { vm.updateSettings { it.copy(snoozeMin = m) } }
                    }
                }
                CardDivider()
                ChoiceBlock(L.t("沒人理會時響多久", "Ring for how long if nobody responds")) {
                    Settings.RING_MINUTE_OPTIONS.forEach { m ->
                        OptionChip(L.n(m, "分鐘", "minute"), m == settings.ringMinutes) { vm.updateSettings { it.copy(ringMinutes = m) } }
                    }
                }
                CardDivider()
                SettingRow(L.t("鈴聲", "Sound"), ringtoneName) {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, L.t("鬧鐘鈴聲", "Alarm sound"))
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, settings.ringtoneUri?.let(Uri::parse))
                    try {
                        ringtoneLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        vm.showMessage(L.t("這支手機沒有鈴聲選擇器", "This phone has no ringtone picker"))
                    }
                }
                CardDivider()
                SwitchRow(L.t("震動", "Vibrate"), settings.vibrate, onChange = { on -> vm.updateSettings { it.copy(vibrate = on) } })
                CardDivider()
                Box(Modifier.padding(vertical = Tok.sSm)) { OutlineButton(L.t("10 秒後試響", "Test ring in 10 seconds"), onClick = { vm.testAlarm() }) }
            }

            SettingsCard(L.t("新增行程", "New event"), R.drawable.ic_plus) {
                SettingRow(L.t("預設日曆", "Default calendar"), defaultCalendar?.displayName ?: L.t("沒有可寫入的日曆", "No writable calendar"), defaultCalendar?.accountName?.takeIf { it != defaultCalendar.displayName }) {
                    dialog = SettingsDialog.CALENDAR
                }
                CardDivider()
                ChoiceBlock(L.t("預設行程長度", "Default event length")) {
                    Settings.DURATION_OPTIONS.forEach { m ->
                        OptionChip(if (m % 60 == 0) L.n(m / 60, "小時", "hour") else L.n(m, "分鐘", "minute"), m == settings.defaultDurationMin) {
                            vm.updateSettings { it.copy(defaultDurationMin = m) }
                        }
                    }
                }
                CardDivider()
                ChoiceBlock(L.t("預設一般通知", "Default notification"), L.t("沒開鬧鐘的行程，由 Google 月曆發通知", "For events without an alarm; sent by Google Calendar")) {
                    OptionChip(L.t("無", "None"), settings.defaultReminderMin == null) { vm.updateSettings { it.copy(defaultReminderMin = null) } }
                    Settings.REMINDER_OPTIONS.forEach { m ->
                        OptionChip(Fmt.reminderLabel(m), m == settings.defaultReminderMin) { vm.updateSettings { it.copy(defaultReminderMin = m) } }
                    }
                }
            }

            SettingsCard(L.t("檢視", "View"), R.drawable.ic_calendar) {
                SettingRow(L.t("一天從幾點開始", "Day starts at"), Fmt.minuteOfDay(settings.dayStartMinute), L.t("日檢視與「N 天」從這裡開始，更早的行程照樣顯示；整天行程的鬧鐘也在這時候響", "Day view and the N-day view start here; earlier events still show. All-day event alarms ring at this time too")) {
                    dialog = SettingsDialog.DAY_START
                }
                CardDivider()
                ChoiceBlock(L.t("自訂天數", "Custom days"), L.t("「N 天」檢視一次看幾天，天數少格子就寬", "How many days the N-day view shows. Fewer days, wider columns")) {
                    Settings.CUSTOM_DAYS.forEach { n ->
                        OptionChip(L.n(n, "天", "day"), n == settings.customDays) { vm.updateSettings { it.copy(customDays = n) } }
                    }
                }
                CardDivider()
                ChoiceBlock(L.t("自訂週數", "Custom weeks"), L.t("「N 週」檢視一次看幾週，週數少格子就大", "How many weeks the N-week view shows. Fewer weeks, bigger cells")) {
                    Settings.CUSTOM_WEEKS.forEach { n ->
                        OptionChip(L.n(n, "週", "week"), n == settings.customWeeks) { vm.updateSettings { it.copy(customWeeks = n) } }
                    }
                }
                CardDivider()
                ChoiceBlock(L.t("一週的第一天", "Week starts on")) {
                    OptionChip(L.t("週一", "Monday"), settings.weekStartsMonday) { vm.updateSettings { it.copy(weekStartsMonday = true) } }
                    OptionChip(L.t("週日", "Sunday"), !settings.weekStartsMonday) { vm.updateSettings { it.copy(weekStartsMonday = false) } }
                }
                CardDivider()
                SwitchRow(L.t("顯示農曆", "Show lunar calendar"), settings.showLunar, onChange = { on -> vm.updateSettings { it.copy(showLunar = on) } })
            }

            SettingsCard(L.t("外觀", "Appearance"), R.drawable.ic_palette) {
                SettingRow(L.t("重點色", "Accent color"), accentLabel(settings), L.t("今天的圓、選取中的選項、按鈕與開關", "Today's circle, selected options, buttons and switches"), swatch = Tok.primary) {
                    dialog = SettingsDialog.ACCENT
                }
                CardDivider()
                SettingRow(
                    L.t("底色", "Background"),
                    canvasLabel(settings),
                    L.t("App 和桌面小工具的底色；選深色時，字和格線會跟著變淺色", "Background of the app and widgets. With a dark color, text and grid lines turn light"),
                    swatch = Tok.canvas,
                ) { dialog = SettingsDialog.CANVAS }
                if (settings.accentColor != null || settings.canvasColor != null) {
                    CardDivider()
                    Box(Modifier.padding(vertical = Tok.sSm)) {
                        OutlineButton(L.t("恢復預設顏色", "Reset colors"), onClick = { vm.updateSettings { it.copy(accentColor = null, canvasColor = null) } })
                    }
                }
            }

            SettingsCard(L.t("月曆小工具", "Month widget"), R.drawable.ic_rows) {
                ChoiceBlock(L.t("顯示範圍", "Range"), L.t("天數、週數跟上面的自訂檢視一樣；改了之後小工具回到今天", "Days and weeks follow the custom views above. Changing this moves the widget back to today")) {
                    OptionChip(L.t("整個月", "Whole month"), settings.widgetRange == WidgetRange.MONTH) { vm.updateSettings { it.copy(widgetRange = WidgetRange.MONTH) } }
                    OptionChip(L.n(settings.customDays, "天", "day"), settings.widgetRange == WidgetRange.DAYS) { vm.updateSettings { it.copy(widgetRange = WidgetRange.DAYS) } }
                    OptionChip(L.n(settings.customWeeks, "週", "week"), settings.widgetRange == WidgetRange.WEEKS) { vm.updateSettings { it.copy(widgetRange = WidgetRange.WEEKS) } }
                }
            }

            SettingsCard(L.t("顯示的日曆", "Calendars shown"), R.drawable.ic_list) {
                Box(Modifier.padding(bottom = Tok.sXs)) {
                    Column {
                        CalendarToggles(calendars, settings.hiddenCalendarIds, onColor = { colorFor = it }) { c, shown -> vm.toggleCalendar(c, shown) }
                        Text(L.t("隱藏的日曆仍會照常響鬧鐘（鬧鐘是逐筆設定的）。", "Hidden calendars still ring their alarms (alarms are set per event)."), style = TypeTok.caption, color = Tok.slate, modifier = Modifier.padding(top = Tok.sXxs))
                        Text(
                            L.t("日曆的顏色只在曆刻響裡換，Google 月曆的顏色不變；行程自己設了顏色的，照行程的顏色。", "Calendar colors change only in RingCal, not in Google Calendar. Events with their own color keep it."),
                            style = TypeTok.caption,
                            color = Tok.slate,
                            modifier = Modifier.padding(top = Tok.sXxs),
                        )
                    }
                }
            }

            SettingsCard(L.t("權限", "Permissions"), R.drawable.ic_check) {
                PermissionSection(vm)
            }

            SettingsCard(L.t("關於", "About"), R.drawable.ic_info) {
                Column(Modifier.padding(vertical = Tok.sSm)) {
                    Text("${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = TypeTok.body, color = Tok.ink)
                    Text(L.t("行程存在你的 Google 月曆；這支 App 不連網路，資料不會離開手機。免費、開放原始碼（MIT 授權）。", "Events are stored in your Google Calendar. This app has no network access, so your data never leaves the phone. Free and open source (MIT License)."), style = TypeTok.caption, color = Tok.slate)
                }
                CardDivider()
                SettingRow(L.t("開放原始碼授權", "Open source licenses"), "MIT・Apache 2.0・BSD") { dialog = SettingsDialog.LICENSES }
                CardDivider()
                SettingRow(L.t("聯繫作者", "Contact"), L.t("寄信給作者", "Email the author"), caption = About.CONTACT_TEXT) { About.mail(context, L.t("曆刻響", "RingCal"), BuildConfig.VERSION_NAME) }
                CardDivider()
                SettingRow(L.t("更多軟體", "More apps"), L.t("到 C520 看看", "Visit C520"), caption = About.MORE_TEXT) { About.more(context) }
            }
            Spacer(Modifier.size(Tok.sXxl))
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.OFFSET -> LeadTimeDialog(settings.defaultOffsetMin, onPick = { m ->
            vm.updateSettings { it.copy(defaultOffsetMin = m) }
            dialog = null
        }, onDismiss = dismiss)
        SettingsDialog.CALENDAR -> ChoiceDialog(L.t("預設日曆", "Default calendar"), dismiss) {
            calendars.filter { it.writable }.groupBy { it.accountName }.forEach { (account, list) ->
                OptionGroupTitle(account)
                list.forEach { c ->
                    OptionRow(c.displayName, selected = c.id == defaultCalendar?.id, leading = { ColorDot(c.color, 18.dp) }, onClick = {
                        vm.updateSettings { it.copy(defaultCalendarId = c.id) }
                        dialog = null
                    })
                }
            }
        }
        SettingsDialog.DAY_START -> {
            val m = settings.dayStartMinute
            val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = dismiss,
                containerColor = Tok.canvas,
                title = { Text(L.t("一天從幾點開始", "Day starts at"), style = TypeTok.titleMd, color = Tok.ink) },
                confirmButton = {
                    TextButton(onClick = {
                        // 最晚中午：再晚的話，早上的行程大多會變成「例外」往前延伸
                        vm.updateSettings { it.copy(dayStartMinute = (state.hour * 60 + state.minute).coerceAtMost(12 * 60)) }
                        dialog = null
                    }) { DialogText(L.t("確定", "OK"), primary = true) }
                },
                dismissButton = { TextButton(onClick = dismiss) { DialogText(L.t("取消", "Cancel"), primary = false) } },
                text = { TimePicker(state = state) },
            )
        }
        SettingsDialog.LICENSES -> LicensesDialog(dismiss)
        SettingsDialog.ACCENT -> ColorPickerDialog(
            title = L.t("重點色", "Accent color"),
            current = settings.accentColor,
            swatches = ACCENT_SWATCHES,
            defaultArgb = Palette.DEFAULT.primary.toArgb(),
            onPick = { argb ->
                vm.updateSettings { it.copy(accentColor = argb) }
                dialog = null
            },
            onDismiss = dismiss,
        ) { PalettePreview(Palette.of(it, settings.canvasColor)) }
        SettingsDialog.CANVAS -> ColorPickerDialog(
            title = L.t("底色", "Background"),
            current = settings.canvasColor,
            swatches = CANVAS_SWATCHES,
            defaultArgb = Palette.DEFAULT.canvas.toArgb(),
            onPick = { argb ->
                vm.updateSettings { it.copy(canvasColor = argb) }
                dialog = null
            },
            onDismiss = dismiss,
        ) { PalettePreview(Palette.of(settings.accentColor, it)) }
        null -> Unit
    }
    colorFor?.let { c ->
        ColorPickerDialog(
            title = L.t("「${c.displayName}」的顏色", "Color of ${c.displayName}"),
            current = settings.calendarColors[c.id],
            swatches = CALENDAR_SWATCHES,
            defaultArgb = c.googleColor,
            display = Tok::eventColor,
            onPick = { argb ->
                vm.updateSettings { s -> s.copy(calendarColors = if (argb == null) s.calendarColors - c.id else s.calendarColors + (c.id to argb)) }
                colorFor = null
            },
            onDismiss = { colorFor = null },
        ) { CalendarColorPreview(c.displayName, it ?: c.googleColor) }
    }
}

/** 底色目前的值：自選的色碼；太中間的灰自動調過時註明。 */
private fun canvasLabel(s: Settings): String {
    val chosen = s.canvasColor ?: return L.t("預設（白）", "Default (white)")
    val label = "#${hexOf(chosen)}"
    return when {
        Tok.canvas.toArgb() == chosen or 0xFF000000.toInt() -> label
        Tok.palette.isLight -> L.t("$label（已自動調淺，字才看得清楚）", "$label (lightened automatically to keep text readable)")
        else -> L.t("$label（已自動調深，字才看得清楚）", "$label (darkened automatically to keep text readable)")
    }
}

/** 重點色目前的值：自選的色碼；太淺或太深、自動調過時註明，免得以為選錯。 */
private fun accentLabel(s: Settings): String {
    val chosen = s.accentColor ?: Palette.DEFAULT.primary.toArgb()
    val label = s.accentColor?.let { "#${hexOf(it)}" } ?: L.t("預設（紫）", "Default (purple)")
    return when {
        Tok.primary.toArgb() == chosen or 0xFF000000.toInt() -> label
        Tok.palette.isLight -> L.t("$label（已自動調深，字才看得清楚）", "$label (darkened automatically to keep text readable)")
        else -> L.t("$label（已自動調淺，字才看得清楚）", "$label (lightened automatically to keep text readable)")
    }
}

/** 一張設定卡片：上面是圖示＋標題，下面白底圓角卡片，卡片裡的項目用細線分開。 */
@Composable
private fun SettingsCard(title: String, @DrawableRes icon: Int, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.padding(start = Tok.sXxs, top = Tok.sXl, bottom = Tok.sXs).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, null, Tok.primary, 18.dp)
        Spacer(Modifier.width(Tok.sXs))
        Text(title, style = TypeTok.titleMd, color = Tok.ink)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.canvas)
            .border(1.dp, Tok.hairline, RoundedCornerShape(Tok.rLg))
            .padding(horizontal = Tok.sMd),
        content = content,
    )
}

@Composable
private fun CardDivider() = Divider()

/** 一組單選選項：標題（＋說明）在上、選項在下。 */
@Composable
private fun ChoiceBlock(title: String, caption: String? = null, options: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = Tok.sSm)) {
        Text(title, style = TypeTok.body, color = Tok.ink)
        if (caption != null) Text(caption, style = TypeTok.caption, color = Tok.slate)
        Spacer(Modifier.size(Tok.sXs))
        ChipRow(options)
    }
}

/** 點了開對話框或其他畫面的一列：標題、目前的值（重點色）、說明，右邊一個箭頭（顏色設定在箭頭前多一個色點）。 */
@Composable
private fun SettingRow(title: String, value: String, caption: String? = null, swatch: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 目前的值放在標題下面：字放大時長的日曆名稱也不會被擠成兩截
        Column(Modifier.weight(1f)) {
            Text(title, style = TypeTok.body, color = Tok.ink)
            Text(value, style = TypeTok.body, color = Tok.primary)
            if (caption != null) Text(caption, style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        if (swatch != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(swatch).border(1.dp, Tok.hairlineStrong, CircleShape))
            Spacer(Modifier.width(Tok.sXs))
        }
        AppIcon(R.drawable.ic_chevron_right, null, Tok.slate, 18.dp)
    }
}

/** 開放原始碼授權：本 App（MIT）與用到的套件（Apache 2.0、BSD 3-Clause），條文從 res/raw/licenses.txt 讀。 */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text by produceState("", resources) {
        value = withContext(Dispatchers.IO) {
            runCatching { resources.openRawResource(R.raw.licenses).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.canvas,
        shape = RoundedCornerShape(Tok.rXxl),
        title = { Text(L.t("開放原始碼授權", "Open source licenses"), style = TypeTok.titleMd, color = Tok.ink) },
        text = { Text(text, style = TypeTok.caption, color = Tok.charcoal, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { DialogText(L.t("關閉", "Close"), primary = true) } },
    )
}

/** 權限狀態與一鍵前往設定。設定頁與首次設定精靈共用。 */
@Composable
fun PermissionSection(vm: MainViewModel) {
    val perms by vm.perms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionsChanged() }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionsChanged() }

    PermissionRow(L.t("讀寫日曆", "Calendar access"), L.t("讀取與新增 Google 月曆的行程", "Read and add Google Calendar events"), perms.calendar) {
        calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    PermissionRow(L.t("通知", "Notifications"), L.t("沒有通知權限，響鈴時看不到畫面、也沒有地方按停", "Without it, the ringing screen can't show and there is nowhere to stop the alarm"), perms.notifications) {
        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PermissionRow(L.t("全螢幕通知", "Full-screen notifications"), L.t("鎖定畫面時直接跳出響鈴畫面", "Show the ringing screen over the lock screen"), perms.fullScreen) {
            open(context, Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
    if (!perms.exactAlarm) {
        PermissionRow(L.t("鬧鐘與提醒", "Alarms & reminders"), L.t("準時觸發鬧鐘", "Fire alarms exactly on time"), false) {
            open(context, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
    }
    PermissionRow(L.t("鬧鐘音量", "Alarm volume"), if (perms.alarmVolumeOk) L.t("鬧鐘音量大於 0", "Alarm volume is above 0") else L.t("鬧鐘音量是 0，鬧鐘不會出聲", "Alarm volume is 0, so alarms are silent"), perms.alarmVolumeOk, okText = L.t("正常", "OK"), fixText = L.t("調整", "Adjust")) {
        open(context, Intent(AndroidSettings.ACTION_SOUND_SETTINGS))
    }
    PermissionRow(L.t("電池用量「不受限制」", "Battery usage \"Unrestricted\""), L.t("讓電腦上改的行程更快同步到鬧鐘", "Lets changes made on a computer reach the alarms sooner"), perms.batteryUnrestricted) {
        open(context, Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }
    Text(
        L.t("按了沒反應？可能是先前選過「不允許」，請到系統設定開啟。", "Nothing happens? You may have chosen \"Don't allow\" before. Turn it on in system settings."),
        style = TypeTok.caption,
        color = Tok.slate,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = L.t("開啟系統的 App 設定", "Open the app's system settings")) {
                open(context, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            .padding(vertical = Tok.sXs),
    )
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    okText: String = L.t("已開啟", "On"),
    fixText: String = L.t("開啟", "Turn on"),
    onRequest: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Tok.sXxs), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(if (granted) R.drawable.ic_check else R.drawable.ic_info, null, if (granted) Tok.primary else Tok.ink, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text(title, style = TypeTok.body, color = Tok.ink)
            Text(desc, style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.width(Tok.sXs))
        if (granted) Text(okText, style = TypeTok.caption, color = Tok.slate) else OutlineButton(fixText, onClick = onRequest)
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** 第一次開啟：把鬧鐘需要的權限開好、試響一次。日曆與通知是必要的，其他建議開。 */
@Composable
fun WizardScreen(vm: MainViewModel) {
    val perms by vm.perms.collectAsStateWithLifecycle()
    val essentials = perms.calendar && perms.notifications
    Column(Modifier.fillMaxSize().background(Tok.canvas).verticalScroll(rememberScrollState()).padding(Tok.sMd)) {
        Spacer(Modifier.size(Tok.sXl))
        Box(Modifier.size(56.dp).clip(CircleShape).background(Tok.primary), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_alarm, null, Tok.onPrimary, 30.dp)
        }
        Spacer(Modifier.size(Tok.sMd))
        TitleBlock(L.t("歡迎使用${stringResource(R.string.app_name)}", "Welcome to ${stringResource(R.string.app_name)}"), null)
        Text(
            L.t("行程照樣存在你的 Google 月曆；建立行程時打開「鬧鐘提醒」，時間到就會像鬧鐘一樣響到你按掉。先把下面幾項打開，前兩項是必要的：", "Events stay in your Google Calendar. Turn on \"Alarm\" when you create an event and it rings like an alarm clock until you dismiss it. Turn on the items below first; the first two are required:"),
            style = TypeTok.body,
            color = Tok.charcoal,
            modifier = Modifier.padding(top = Tok.sXs, bottom = Tok.sMd),
        )
        PermissionSection(vm)
        Spacer(Modifier.size(Tok.sMd))
        if (perms.notifications) {
            OutlineButton(L.t("10 秒後試響", "Test ring in 10 seconds"), onClick = { vm.testAlarm() })
        } else {
            Text(L.t("開了通知權限後，可以先試響一次。", "Once notifications are allowed, you can try a test ring."), style = TypeTok.caption, color = Tok.slate)
        }
        Spacer(Modifier.size(Tok.sXl))
        PrimaryButton(
            when {
                !perms.calendar -> L.t("請先允許讀寫日曆", "Allow calendar access first")
                !perms.notifications -> L.t("請先允許通知", "Allow notifications first")
                else -> L.t("開始使用", "Get started")
            },
            onClick = { vm.finishWizard() },
            enabled = essentials,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
