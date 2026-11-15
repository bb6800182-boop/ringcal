package com.c520.likexiang.ui

import com.c520.likexiang.L
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.likexiang.R
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import java.time.Instant
import java.time.ZoneId

/** 鬧鐘清單：未來 7 天會響的鬧鐘，可單次略過。 */
@Composable
fun AlarmsScreen(vm: MainViewModel) {
    val list by vm.upcoming.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    LaunchedEffect(alarmState) { vm.loadUpcoming() }
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().background(Tok.canvas)) {
        TopBar(L.t("鬧鐘清單", "Alarms"), subtitle = L.t("未來 7 天", "Next 7 days"), navIcon = R.drawable.ic_back, onNav = { vm.back() })
        Divider()
        if (list.isEmpty()) {
            Text(L.t("未來 7 天沒有鬧鐘。新增行程時打開「鬧鐘提醒」就會出現在這裡。", "No alarms in the next 7 days. Turn on \"Alarm\" when adding an event and it appears here."), style = TypeTok.body, color = Tok.slate, modifier = Modifier.padding(Tok.sXl))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.key + if (it.snoozed) "#s" else "" }) { a ->
                val date = Instant.ofEpochMilli(a.triggerAt).atZone(zone).toLocalDate()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = L.t("看行程內容", "View event")) { vm.navigate(Screen.Detail(a.eventId, a.begin)) }
                        .padding(horizontal = Tok.sMd, vertical = Tok.sSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.widthIn(min = 72.dp)) {
                        Text(Fmt.time(a.triggerAt), style = TypeTok.titleMd, color = if (a.skipped) Tok.steel else Tok.ink)
                        Text(Fmt.dateWithDow(date), style = TypeTok.caption, color = Tok.slate)
                    }
                    Spacer(Modifier.width(Tok.sSm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.title,
                            style = TypeTok.body,
                            color = if (a.skipped) Tok.steel else Tok.ink,
                            textDecoration = if (a.skipped) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                a.skipped -> L.t("這次略過", "Skipped")
                                a.snoozed -> L.t("稍後再響", "Snoozed")
                                a.allDay -> L.t("整天行程", "All-day event")
                                else -> L.t("${Fmt.time(a.begin)} 開始", "Starts ${Fmt.time(a.begin)}")
                            },
                            style = TypeTok.caption,
                            color = Tok.slate,
                        )
                    }
                    if (!a.snoozed) {
                        OutlineButton(if (a.skipped) L.t("恢復", "Restore") else L.t("略過這次", "Skip"), onClick = { vm.setSkip(a.key, !a.skipped) })
                    }
                }
                Divider()
            }
        }
    }
}
