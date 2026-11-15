package com.c520.likexiang.ui

import com.c520.likexiang.L
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.likexiang.R
import com.c520.likexiang.data.AlarmMarker
import com.c520.likexiang.data.Attendee
import com.c520.likexiang.data.Settings
import com.c520.likexiang.data.SyncMode
import com.c520.likexiang.data.alarmKey
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok

private const val GOOGLE_CALENDAR = "com.google.android.calendar"

@Composable
fun DetailScreen(vm: MainViewModel, eventId: Long, begin: Long) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // 停在這頁時電腦上改了（或同步進來）也跟著重讀
    val dataVersion by vm.dataVersion.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var bundle by remember { mutableStateOf<EventBundle?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(eventId, begin, refresh, dataVersion) {
        bundle = vm.loadBundle(eventId, begin)
        loaded = true
    }
    val b = bundle
    Column(Modifier.fillMaxSize().background(Tok.canvas)) {
        TopBar(
            title = L.t("行程內容", "Event"),
            navIcon = R.drawable.ic_back,
            onNav = { vm.back() },
            actions = {
                // 受邀、主辦人沒開放修改的行程不給編輯；只有主辦人能刪（受邀的交給 Google 月曆回覆或移除）
                if (b != null && b.detail.canEditTitle) IconBtn(R.drawable.ic_edit, L.t("編輯", "Edit"), onClick = { vm.navigate(Screen.Edit(b.detail.id, null)) })
                if (b != null && b.detail.canDelete) IconBtn(R.drawable.ic_trash, L.t("刪除", "Delete"), onClick = { confirmDelete = true })
            },
        )
        if (b == null) {
            if (loaded) Text(L.t("找不到這筆行程，可能已在其他裝置刪除。", "Event not found. It may have been deleted on another device."), style = TypeTok.body, color = Tok.slate, modifier = Modifier.padding(Tok.sXl))
            return@Column
        }
        val d = b.detail
        val instanceEnd = b.instanceBegin + (d.endMillis - d.dtStart)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tok.sMd, vertical = Tok.sXs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Tok.eventColor(d.color)))
                Spacer(Modifier.width(Tok.sXs))
                Text(d.calendarName, style = TypeTok.caption, color = Tok.slate)
            }
            Spacer(Modifier.height(Tok.sXs))
            Text(AlarmMarker.strip(d.title).ifBlank { L.t("（無標題）", "(No title)") }, style = TypeTok.titleLg, color = Tok.ink)
            Spacer(Modifier.height(Tok.sXs))
            InfoRow(R.drawable.ic_clock, Fmt.range(b.instanceBegin, instanceEnd, d.allDay))
            if (d.recurring) InfoRow(R.drawable.ic_repeat, L.t("重複行程（修改會套用到整個系列）", "Repeating event (changes apply to the whole series)"))
            if (d.location.isNotBlank()) {
                InfoRow(R.drawable.ic_pin, d.location, actionLabel = L.t("在地圖上開啟", "Open in maps")) {
                    if (!openMap(context, d.location)) vm.showMessage(L.t("找不到可以開啟地圖的 App", "No maps app found"))
                }
            }
            if (b.attendees.isNotEmpty()) AttendeeList(b.attendees, d.ownerAccount)
            if (d.description.isNotBlank()) {
                Spacer(Modifier.height(Tok.sXs))
                Text(d.description, style = TypeTok.bodySm, color = Tok.charcoal)
            }

            AlarmCard(
                bundle = b,
                settings = settings,
                onToggle = { on -> vm.setAlarm(b, on) { refresh++ } },
                onOffset = { m -> vm.setOffset(d.id, d.originalId, m) { refresh++ } },
                onSkip = { skip -> vm.setSkip(alarmKey(d.id, b.instanceBegin), skip) { refresh++ } },
            )

            InfoRow(
                R.drawable.ic_bell,
                if (b.reminders.isEmpty()) L.t("沒有一般通知", "No notifications") else L.t("一般通知：", "Notifications: ") + b.reminders.joinToString(L.t("、", ", ")) { Fmt.reminderLabel(it) },
            )

            if (isGoogleCalendarInstalled(context)) {
                Spacer(Modifier.height(Tok.sMd))
                OutlineButton(L.t("在 Google 月曆開啟", "Open in Google Calendar"), onClick = {
                    if (!openInGoogleCalendar(context, d.id, b.instanceBegin, instanceEnd)) vm.showMessage(L.t("開不了 Google 月曆", "Can't open Google Calendar"))
                })
            }
            Spacer(Modifier.height(Tok.sXxl))
        }
    }
    if (confirmDelete && b != null) {
        ConfirmDialog(
            title = L.t("刪除這筆行程？", "Delete this event?"),
            text = when {
                b.detail.recurring -> L.t("這是重複行程，會刪掉整個系列。只想刪這一次，請按下方的「在 Google 月曆開啟」。", "This is a repeating event; the whole series will be deleted. To delete only this one, tap \"Open in Google Calendar\" below.")
                b.hasOtherAttendees -> L.t("這筆行程有其他與會者，刪除後 Google 月曆會通知他們行程取消。", "This event has other guests. Google Calendar will tell them it is cancelled.")
                else -> L.t("刪除後電腦上的 Google 月曆也會一起刪除。", "It will also be deleted from Google Calendar on your computer.")
            },
            confirmText = L.t("刪除", "Delete"),
            onConfirm = {
                confirmDelete = false
                vm.delete(b) { vm.back() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoRow(icon: Int, text: String, actionLabel: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick) else Modifier)
            .padding(vertical = Tok.sXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, null, Tok.slate, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Text(text, style = TypeTok.body, color = Tok.ink, modifier = Modifier.weight(1f))
        if (onClick != null) AppIcon(R.drawable.ic_open, null, Tok.slate, 18.dp)
    }
}

/** 與會者名單與回覆狀態（回覆邀請交給 Google 月曆）。 */
@Composable
private fun AttendeeList(list: List<Attendee>, ownerAccount: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = Tok.sXs), verticalAlignment = Alignment.Top) {
        AppIcon(R.drawable.ic_calendar, null, Tok.slate, 20.dp)
        Spacer(Modifier.width(Tok.sSm))
        Column(Modifier.weight(1f)) {
            Text(L.t("${list.size} 位與會者", if (list.size == 1) "1 guest" else "${list.size} guests"), style = TypeTok.body, color = Tok.ink)
            list.forEach { a ->
                val me = a.email.isNotBlank() && a.email.equals(ownerAccount, ignoreCase = true)
                val name = a.name.ifBlank { a.email }.ifBlank { L.t("（未命名）", "(No name)") } + if (me) L.t("（你）", " (you)") else ""
                val status = when (a.status) {
                    Attendees.ATTENDEE_STATUS_ACCEPTED -> L.t("已接受", "Accepted")
                    Attendees.ATTENDEE_STATUS_DECLINED -> L.t("已拒絕", "Declined")
                    Attendees.ATTENDEE_STATUS_TENTATIVE -> L.t("未定", "Maybe")
                    Attendees.ATTENDEE_STATUS_INVITED -> L.t("未回覆", "No reply")
                    else -> ""
                }
                Text(if (status.isEmpty()) name else name + L.t("・", " · ") + status, style = TypeTok.bodySm, color = Tok.slate)
            }
        }
    }
}

/** 鬧鐘設定卡（詳情頁）：開關、提前時間、同步方式說明、單次略過。 */
@Composable
private fun AlarmCard(
    bundle: EventBundle,
    settings: Settings,
    onToggle: (Boolean) -> Unit,
    onOffset: (Int) -> Unit,
    onSkip: (Boolean) -> Unit,
) {
    val d = bundle.detail
    Column(
        Modifier
            .padding(vertical = Tok.sSm)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.alarmTint)
            .padding(horizontal = Tok.cardPadH, vertical = Tok.sSm),
    ) {
        SwitchRow(
            title = L.t("鬧鐘提醒", "Alarm"),
            subtitle = L.t("響到你按掉為止", "Rings until you dismiss it"),
            checked = bundle.alarmOn,
            onChange = onToggle,
            titleStyle = TypeTok.titleMd,
            leading = {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(Tok.rMd)).background(Tok.canvas), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_alarm, null, Tok.primary, 20.dp)
                }
            },
        )
        if (bundle.alarmOn) {
            // 跟新增／編輯頁同一個「提前 10 分鐘 ⌄」，點了選常用時間或自訂
            var leadDialog by remember { mutableStateOf(false) }
            val current = bundle.offsetMin ?: settings.defaultOffsetMin
            LeadTimeRow(
                minutes = current,
                active = true,
                allDayAt = if (d.allDay) Fmt.minuteOfDay(settings.dayStartMinute) else null,
                onClick = { leadDialog = true },
            )
            if (leadDialog) {
                LeadTimeDialog(current, onPick = { m ->
                    leadDialog = false
                    onOffset(m)
                }, onDismiss = { leadDialog = false })
            }
            SwitchRow(title = L.t("略過這一次", "Skip this time"), checked = bundle.skipped, onChange = onSkip, modifier = Modifier.padding(top = Tok.sXs))
        }
        Row(Modifier.padding(top = Tok.sXs), verticalAlignment = Alignment.Top) {
            AppIcon(R.drawable.ic_info, null, Tok.slate, 15.dp, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Tok.sXs))
            Text(
                when (bundle.syncMode) {
                    SyncMode.TITLE -> L.t("鬧鐘會同步到 Google 月曆：標題前面加上 ⏰，電腦上也看得到。", "The alarm syncs to Google Calendar: ⏰ is added before the title, so you see it on a computer too.")
                    SyncMode.LOCAL -> if (bundle.hasOtherAttendees) L.t("這個行程有其他與會者，⏰ 只記在這支手機，不會改到大家看到的標題。", "This event has other guests, so ⏰ is kept on this phone only and the shared title is not changed.")
                    else L.t("這個行程不能修改標題，⏰ 只記在這支手機。", "This event's title can't be changed, so ⏰ is kept on this phone only.")
                },
                style = TypeTok.caption,
                color = Tok.slate,
            )
        }
    }
}

private fun openMap(context: Context, location: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))))

private fun isGoogleCalendarInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(GOOGLE_CALENDAR, 0)
    true
} catch (_: Exception) {
    false
}

/** 明確指定 Google 月曆 App 開啟，避免日後本 App 接手行事曆 Intent 時繞回自己。 */
private fun openInGoogleCalendar(context: Context, eventId: Long, begin: Long, end: Long): Boolean = start(
    context,
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        .setPackage(GOOGLE_CALENDAR)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end),
)

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
