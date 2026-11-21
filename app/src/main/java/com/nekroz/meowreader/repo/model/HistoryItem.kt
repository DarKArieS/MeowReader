package com.nekroz.meowreader.repo.model

import com.nekroz.meowreader.repo.chapter.ChapterItem
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import kotlinx.serialization.Serializable

/**
 * 單一檔案的閱讀紀錄。以「不含副檔名的檔名」為 key。
 */
@Serializable
data class HistoryItem(
    val fileName: String,

    /** 是否釘選。釘選的檔案在 MainPage 會排到清單最上方。 */
    val isPinned: Boolean = false,

    /**
     * 最上方可見行的索引（0-based）。這是位置的絕對錨點，不受字體大小、
     * 螢幕尺寸或旋轉影響。
     */
    val lineIndex: Int? = null,

    /** 在 lineIndex 該行內的細部偏移比例，範圍 [0, 1)。 */
    val lineFraction: Double = 0.0,

    /** 已讀到的行數（最下方可見行 +1），與 ReaderPage 標題上的百分比同義。 */
    val readLines: Int = 0,

    /** 檔案總行數。0 表示還沒有這項資訊。 */
    val totalLines: Int = 0,

    /** 上次掃描出來的章節清單。掃描結果存在這裡，下次開同一個檔案就不必重掃。 */
    val chapters: List<ChapterItem>? = null,

    /** [chapters] 是用什麼條件掃出來的。條件對不上就代表快取過期，必須重掃。 */
    val chapterCacheKey: String? = null,

    /** 這個檔案專屬的章節抓取設定。是否生效由 [useDefaultChapterSetting] 決定。 */
    val chapterSetting: ChapterRegexSetting? = null,

    /**
     * 是否使用全域預設的章節抓取設定。true 時即使 [chapterSetting] 已經填過資料也不採用，
     * 只是暫時關閉不用；關掉這個開關就能拿回原本填的設定，不必重新輸入。
     */
    val useDefaultChapterSetting: Boolean = true,

    /**
     * 上次在 ReaderPage 開啟時的檔案位置，閱讀歷史靠它重新開啟檔案。
     * 舊版設定檔沒有這一項，要等檔案再被開過一次才會補上。
     */
    val lastFile: OpenFile? = null,

    /** 上次在 ReaderPage 開啟的時間（epoch 毫秒），閱讀歷史依此由新到舊排序。 */
    val lastOpenedAt: Long? = null,

    /**
     * 書籤，存的是行索引（0-based），由小到大排列、不重複。
     * 只存行號不存內文：單行編輯與全文取代都不會改變行數，行號一直對得上，
     * 預覽文字直接從目前的內容取，改過字也會顯示新的內容。
     */
    val bookmarks: List<Int> = emptyList(),
) {
    /** 閱讀進度百分比（0~100）。資訊不足時回傳 null。 */
    val progressPercent: Double?
        get() {
            if (totalLines <= 0) return null
            val read = if (readLines > 0) readLines else (lineIndex?.plus(1) ?: 0)
            if (read <= 0) return null
            return (read.toDouble() / totalLines * 100.0).coerceIn(0.0, 100.0)
        }
}

/** MainPage 各資料夾的瀏覽位置，以項目索引為錨點。 */
@Serializable
data class FolderScrollHistoryItem(
    val folderUri: String,
    /** 最上方可見項目的索引（0-based）。 */
    val itemIndex: Int = 0,
)
