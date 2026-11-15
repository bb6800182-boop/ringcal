package com.c520.likexiang.alarm

import com.c520.likexiang.L
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import com.c520.likexiang.MainActivity
import com.c520.likexiang.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifications {
    const val CH_RING = "alarm_ring"
    const val CH_MISSED = "alarm_missed"
    const val CH_VOICE = "voice_added"
    private const val REQ_FULLSCREEN = 2001
    private const val REQ_DISMISS = 2002
    private const val REQ_SNOOZE = 2003
    private const val REQ_SWIPED = 2004
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, L.t("鬧鐘響鈴", "Alarm ringing"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = L.t("行程鬧鐘響鈴時的全螢幕畫面。鈴聲由 App 播放，這個通知本身不出聲。", "Full-screen alarm for events. The app plays the sound; this notification itself is silent.")
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MISSED, L.t("錯過的鬧鐘", "Missed alarms"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = L.t("鬧鐘響滿時間沒人理會、或無法顯示響鈴畫面時留下的提醒。", "Reminders left when an alarm rang out unanswered or the ringing screen could not be shown.")
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_VOICE, L.t("語音新增", "Voice add"), NotificationManager.IMPORTANCE_LOW).apply {
                description = L.t("用語音建立行程後的確認，點了可以修改或刪除。", "Confirmation after adding an event by voice. Tap to edit or delete it.")
            },
        )
    }

    /**
     * 響鈴中的前景通知：全螢幕開啟響鈴畫面，附稍後提醒與關閉；滑掉通知等於按關閉，不會變成關不掉的鈴聲。
     * 鎖定畫面上依系統「隱藏敏感內容」設定，只顯示「行程鬧鐘」與時間。
     */
    fun ringing(context: Context, items: List<RingItem>, snoozeMinutes: Int): Notification {
        val first = items.first()
        val fullScreen = PendingIntent.getActivity(
            context, REQ_FULLSCREEN,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_alarm)
        val title = if (items.size == 1) first.title else L.t("${first.title} 等 ${items.size} 個行程", "${first.title} and ${items.size - 1} more")
        val publicVersion = Notification.Builder(context, CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(L.t("行程鬧鐘", "Event alarm"))
            .setContentText(if (first.allDay) L.t("整天行程", "All-day event") else L.t(time(first.begin) + " 開始", "Starts " + time(first.begin)))
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        return Notification.Builder(context, CH_RING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(describe(first))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setDeleteIntent(RingService.actionIntent(context, RingService.ACTION_DISMISS, 0, REQ_SWIPED))
            .addAction(Notification.Action.Builder(icon, L.t("$snoozeMinutes 分後再響", "Snooze $snoozeMinutes min"), RingService.actionIntent(context, RingService.ACTION_SNOOZE, snoozeMinutes, REQ_SNOOZE)).build())
            .addAction(Notification.Action.Builder(icon, L.t("關閉", "Dismiss"), RingService.actionIntent(context, RingService.ACTION_DISMISS, 0, REQ_DISMISS)).build())
            .build()
    }

    fun missed(context: Context, items: List<RingItem>) = post(context, items, L.t("錯過的鬧鐘：", "Missed alarm: "))

    /** 響鈴服務起不來時的退路：至少有一則會出聲的通知。 */
    fun fallback(context: Context, items: List<RingItem>) = post(context, items, L.t("鬧鐘：", "Alarm: "))

    private fun post(context: Context, items: List<RingItem>, prefix: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        items.forEach { item ->
            val id = item.key.hashCode()
            val open = PendingIntent.getActivity(
                context, id,
                MainActivity.internalIntent(context, MainActivity.ACTION_OPEN_EVENT)
                    .putExtra(MainActivity.EXTRA_EVENT_ID, item.eventId)
                    .putExtra(MainActivity.EXTRA_BEGIN, item.begin),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                id,
                Notification.Builder(context, CH_MISSED)
                    .setSmallIcon(R.drawable.ic_stat_alarm)
                    .setContentTitle(prefix + item.title)
                    .setContentText(describe(item))
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    fun describe(item: RingItem): String {
        val time = if (item.allDay) L.t("整天", "All day") else L.t(time(item.begin) + " 開始", "Starts " + time(item.begin))
        return if (item.location.isBlank()) time else time + L.t("・", " · ") + item.location
    }

    private fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME)
}
