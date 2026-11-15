package com.c520.likexiang

import java.util.Locale

/**
 * 介面語言：手機是中文就顯示中文，其他語言一律英文。
 * 中英文寫在同一行（L.t("中文", "English")），改字時才不會漏改一邊。
 */
object L {
    val zh: Boolean get() = Locale.getDefault().language == "zh"
    fun t(zh: String, en: String): String = if (this.zh) zh else en

    /** 數量加單位：「3 天」／"3 days"、"1 day"。 */
    fun n(count: Int, zhUnit: String, enUnit: String): String =
        if (zh) "$count $zhUnit" else "$count $enUnit" + if (count == 1) "" else "s"
}
