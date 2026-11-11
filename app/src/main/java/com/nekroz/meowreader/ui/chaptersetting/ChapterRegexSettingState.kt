package com.nekroz.meowreader.ui.chaptersetting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nekroz.meowreader.repo.chapter.ChapterParser
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting

/**
 * 章節抓取設定區塊（Regex 清單、標題字數上限、開頭跳過行數）的編輯狀態與驗證邏輯。
 *
 * 不知道自己編輯的是全域預設值還是單一檔案的專屬設定，由呼叫端決定初始值與存檔目標，
 * 讓全域設定與單一檔案的章節設定對話框可以共用同一份 UI。
 */
@Stable
class ChapterRegexSettingState(initial: ChapterRegexSetting) {

    /**
     * 清單上的一列 Regex。用物件而非裸字串，內容重複時也還認得出是哪一列。
     */
    @Stable
    class Entry(pattern: String = "") {
        var pattern by mutableStateOf(pattern)
    }

    val entries = mutableStateListOf<Entry>().apply {
        initial.effectiveRegexList().forEach { add(Entry(it)) }
    }

    /** 章節標題字數上限。輸入中的文字原樣保留，存檔時才解析並夾進合法範圍。 */
    var titleMaxLengthText by mutableStateOf(initial.effectiveTitleMaxLength().toString())

    /** 開頭要跳過的行數，跳過的行不參與章節比對。 */
    var skipLinesText by mutableStateOf(initial.effectiveSkipLines().toString())

    /** 驗證失敗的那條 Regex，顯示在畫面底部；null 表示沒有錯誤。 */
    var invalidPattern by mutableStateOf<String?>(null)
        private set

    private val titleMaxLength: Int
        get() = (titleMaxLengthText.toIntOrNull() ?: ChapterRegexSetting.DEFAULT_TITLE_MAX_LENGTH)
            .coerceIn(ChapterRegexSetting.MIN_TITLE_MAX_LENGTH, ChapterRegexSetting.MAX_TITLE_MAX_LENGTH)

    private val skipLines: Int
        get() = (skipLinesText.toIntOrNull() ?: ChapterRegexSetting.DEFAULT_SKIP_LINES)
            .coerceIn(ChapterRegexSetting.MIN_SKIP_LINES, ChapterRegexSetting.MAX_SKIP_LINES)

    fun add() {
        entries.add(Entry())
    }

    fun remove(entry: Entry) {
        entries.remove(entry)
        invalidPattern = null
    }

    /** 把預設的章節 Regex 補回清單（已存在的不重複加）。 */
    fun restoreDefaults() {
        for (pattern in ChapterRegexSetting.DEFAULT_REGEX_LIST) {
            if (entries.none { it.pattern.trim() == pattern }) entries.add(Entry(pattern))
        }
        invalidPattern = null
    }

    fun stepTitleMaxLength(direction: Int) {
        titleMaxLengthText = (titleMaxLength + direction * TITLE_MAX_LENGTH_STEP)
            .coerceIn(ChapterRegexSetting.MIN_TITLE_MAX_LENGTH, ChapterRegexSetting.MAX_TITLE_MAX_LENGTH)
            .toString()
    }

    fun stepSkipLines(direction: Int) {
        skipLinesText = (skipLines + direction)
            .coerceIn(ChapterRegexSetting.MIN_SKIP_LINES, ChapterRegexSetting.MAX_SKIP_LINES)
            .toString()
    }

    /**
     * 驗證並打包成 [ChapterRegexSetting]。Regex 寫錯就回傳 null，
     * 讓呼叫端把畫面留在原地讓使用者照著錯誤訊息修正。
     */
    fun trySave(): ChapterRegexSetting? {
        val patterns = ArrayList<String>()
        for (entry in entries) {
            val pattern = entry.pattern.trim()
            if (pattern.isEmpty()) continue // 空白列視為使用者還沒填，直接忽略

            if (!ChapterParser.isValidPattern(pattern)) {
                invalidPattern = pattern
                return null
            }
            if (pattern !in patterns) patterns.add(pattern)
        }

        invalidPattern = null
        return ChapterRegexSetting(
            chapterRegexList = patterns,
            chapterTitleMaxLength = titleMaxLength,
            chapterSkipLines = skipLines,
        )
    }

    private companion object {
        const val TITLE_MAX_LENGTH_STEP = 5
    }
}

@Composable
fun rememberChapterRegexSettingState(initial: ChapterRegexSetting): ChapterRegexSettingState =
    remember { ChapterRegexSettingState(initial) }
