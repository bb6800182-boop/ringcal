package com.c520.likexiang.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthWidgetLanesTest {
    private val large = GridDims(1.5f) // 測試機的字級
    private val normal = GridDims(1f)

    @Test
    fun rowsFillUntilTheCellIsFull() {
        // 字級 1.5：日期列 26dp，每條車道 21.5dp（色塊 19.5＋間距 2）
        assertEquals(4, lanesForRow(120.dp, large)) // 格子剩 93dp
        assertEquals(5, lanesForRow(134.dp, large)) // 剩 107dp：5 條色塊＋中間 4 個間距剛好
        assertEquals(8, lanesForRow(200.dp, large)) // 沒有上限，高度夠就一直排
    }

    @Test
    fun lastLaneMayDropItsGap() {
        // 字級 1：日期列 18dp，每條車道 16dp（色塊 14＋間距 2）
        assertEquals(3, lanesForRow(65.dp, normal)) // 格子剩 46dp：3 條色塊＋中間 2 個間距
        assertEquals(2, lanesForRow(64.dp, normal))
    }

    @Test
    fun shortRowsFallBackToDots() {
        assertEquals(0, lanesForRow(30.dp, large)) // 只放得下日期
        assertEquals(0, lanesForRow(10.dp, large))
    }

    @Test
    fun gridIsTheWidgetMinusHeaderAndWeekdayRow() {
        // 字級 1：星期列 19dp；外距上下各 2、頂部列 40
        assertEquals(DpSize(396.dp, 737.dp), gridSize(DpSize(400.dp, 800.dp), normal))
        // 回報的大小太小也不會變成 0 或負的
        assertEquals(DpSize(120.dp, 40.dp), gridSize(DpSize(10.dp, 10.dp), normal))
    }
}
