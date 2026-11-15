package com.c520.likexiang.ui

import com.c520.likexiang.L
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c520.likexiang.R
import com.c520.likexiang.data.AlarmRules
import com.c520.likexiang.data.EventInstance
import com.c520.likexiang.data.SearchQuery
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import java.time.LocalDate
import java.time.ZoneId

/**
 * 搜尋行程（VIEW-06）：輸入就搜，標題、地點、說明都比對；搜尋框右邊的 X 一鍵清除。
 * 結果依日期分組，一打開就捲到今天以後的第一筆；點了看行程內容。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(vm: MainViewModel) {
    val state by vm.search.collectAsStateWithLifecycle()
    val alarmState by vm.alarmState.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    // 輸入框的字放在畫面裡（打字不等 ViewModel）；從行程內容回來時接著上次搜的關鍵字
    var text by remember { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (text.isEmpty()) focus.requestFocus() }
    // 往下捲結果時收起鍵盤，才看得到更多筆
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }

    val groups: Map<LocalDate, List<EventInstance>> = remember(state.results) {
        state.results.groupBy { it.startDate(zone) }.toSortedMap()
    }
    // 新的關鍵字才捲到今天；從行程內容回來時停在原本的位置
    var scrolledFor by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.query, groups) {
        if (groups.isEmpty() || state.query == scrolledFor) return@LaunchedEffect
        var index = 1 // 第 0 項是結果筆數
        for ((date, events) in groups) {
            if (!date.isBefore(today)) break
            index += 1 + events.size
        }
        // 全部都在今天以前時，index 超過最後一項，清單會停在最底（最近的那筆）
        listState.scrollToItem(index)
        scrolledFor = state.query
    }

    Column(Modifier.fillMaxSize().background(Tok.canvas)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Tok.topBarH), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(R.drawable.ic_back, L.t("返回", "Back"), onClick = { vm.back() })
            PlainField(
                value = text,
                onValueChange = {
                    text = it
                    vm.search(it)
                },
                placeholder = L.t("搜尋行程", "Search events"),
                modifier = Modifier.weight(1f).focusRequester(focus),
                imeAction = ImeAction.Search,
                onImeAction = { keyboard?.hide() },
            )
            if (text.isNotEmpty()) {
                IconBtn(R.drawable.ic_close, L.t("清除搜尋", "Clear search"), onClick = {
                    text = ""
                    vm.search("")
                    focus.requestFocus()
                })
            } else {
                Spacer(Modifier.width(Tok.sSm))
            }
        }
        Divider()
        val q = SearchQuery.normalize(text)
        when {
            q == null -> Hint(L.t("輸入關鍵字，找標題、地點或說明裡有這些字的行程（今天前後 ${SearchQuery.YEARS} 年）。", "Type a keyword to find events by title, location or notes (within ${SearchQuery.YEARS} years of today)."))
            state.error -> Hint(L.t("讀不到日曆，請確認已允許讀取日曆。", "Can't read calendars. Check that calendar access is allowed."))
            groups.isEmpty() -> Hint(if (state.loading || state.query != q) L.t("搜尋中…", "Searching…") else L.t("今天前後 ${SearchQuery.YEARS} 年內，找不到「$q」。", "No results for \"$q\" within ${SearchQuery.YEARS} years of today."))
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item(key = "count") {
                    val n = state.results.size
                    Text(
                        when {
                            state.loading -> L.t("搜尋中…", "Searching…")
                            n >= SearchQuery.MAX_RESULTS -> L.t("找到超過 $n 個行程，只列離今天最近的 $n 個", "More than $n events found. Showing the $n closest to today")
                            else -> L.t("找到 $n 個行程（今天前後 ${SearchQuery.YEARS} 年）", "$n events found (within ${SearchQuery.YEARS} years of today)")
                        },
                        style = TypeTok.caption,
                        color = Tok.slate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tok.sMd, vertical = Tok.sXs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                groups.forEach { (date, events) ->
                    stickyHeader(key = "h$date") { DateListHeader(date, today) }
                    items(events, key = { "${date}_${it.key}" }) { e ->
                        EventListRow(e, AlarmRules.isAlarmOn(e, alarmState), zone, showLocation = true) {
                            vm.navigate(Screen.Detail(e.eventId, e.begin))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Tok.sXl, vertical = Tok.sXxl), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = TypeTok.body,
            color = Tok.slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
