package com.nekroz.meowreader.repo.chapter

import kotlinx.serialization.Serializable

/**
 * 一組章節抓取設定：Regex 清單、標題字數上限、開頭跳過行數。
 * 可能是全域預設值，也可能是單一檔案的專屬設定。
 */
@Serializable
data class ChapterRegexSetting(
    val chapterRegexList: List<String>? = null,
    val chapterTitleMaxLength: Int? = null,
    val chapterSkipLines: Int? = null,
) {
    /** 沒設定過時給預設 Regex；使用者刻意清空則尊重其設定，不再塞回預設值。 */
    fun effectiveRegexList(): List<String> = chapterRegexList ?: DEFAULT_REGEX_LIST

    /** 章節標題的字數上限，超過這個字數的行不視為章節。 */
    fun effectiveTitleMaxLength(): Int =
        (chapterTitleMaxLength ?: DEFAULT_TITLE_MAX_LENGTH)
            .coerceIn(MIN_TITLE_MAX_LENGTH, MAX_TITLE_MAX_LENGTH)

    /** 章節掃描要跳過的開頭行數。 */
    fun effectiveSkipLines(): Int =
        (chapterSkipLines ?: DEFAULT_SKIP_LINES).coerceIn(MIN_SKIP_LINES, MAX_SKIP_LINES)

    companion object {
        /** 使用者沒設定過時採用的章節 Regex。 */
        val DEFAULT_REGEX_LIST: List<String> = listOf(
            "第([0-9一二三四五六七八九十]+)話",
            "第([0-9一二三四五六七八九十]+)章",
        )

        const val MIN_TITLE_MAX_LENGTH = 8
        const val MAX_TITLE_MAX_LENGTH = 300
        const val DEFAULT_TITLE_MAX_LENGTH = 60

        const val MIN_SKIP_LINES = 0
        const val MAX_SKIP_LINES = 1000
        const val DEFAULT_SKIP_LINES = 0
    }
}
