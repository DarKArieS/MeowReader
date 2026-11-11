package com.nekroz.meowreader.repo.chapter

import java.util.regex.PatternSyntaxException

/**
 * 依使用者設定的章節 Regex 掃描文章，找出章節標題所在的行。
 */
object ChapterParser {

    /** 設定畫面用來擋下寫不出來的 Regex。 */
    fun isValidPattern(pattern: String?): Boolean {
        if (pattern.isNullOrBlank()) return false
        return try {
            Regex(pattern)
            true
        } catch (_: PatternSyntaxException) {
            false
        }
    }

    /**
     * 掃描所有行，回傳依行序排列的章節清單。一行只會產生一個章節，即使同時符合多個 Regex。
     *
     * @param maxTitleLength 章節標題的字數上限。超過這個字數的行不視為章節——內文段落裡提到
     * 「第三話」之類的字樣時，才不會被誤認成章節標題。0 或負數表示不限制。
     * @param skipLines 開頭要跳過的行數。書名頁、版權頁、目錄常有誤判成章節的字樣，
     * 跳過這些行再開始比對。0 或負數表示從頭掃描。
     */
    fun parse(
        lines: List<String>,
        patterns: List<String>?,
        maxTitleLength: Int,
        skipLines: Int = 0,
    ): List<ChapterItem> {
        val regexes = compile(patterns)
        if (regexes.isEmpty()) return emptyList()

        val result = ArrayList<ChapterItem>()
        for (i in skipLines.coerceAtLeast(0) until lines.size) {
            val text = lines[i]
            if (text.isBlank()) continue

            // 先擋字數再比對 Regex：長段落本來就不會是標題，也省掉比對成本。
            val title = text.trim()
            if (maxTitleLength > 0 && title.length > maxTitleLength) continue

            if (regexes.any { it.containsMatchIn(title) }) {
                result.add(ChapterItem(title = title, lineIndex = i))
            }
        }
        return result
    }

    private fun compile(patterns: List<String>?): List<Regex> {
        if (patterns == null) return emptyList()
        return patterns.mapNotNull { pattern ->
            if (pattern.isBlank()) return@mapNotNull null
            try {
                Regex(pattern)
            } catch (_: PatternSyntaxException) {
                // 設定檔可能被改壞，寫不出來的 Regex 直接略過而不是讓開檔失敗。
                null
            }
        }
    }

    /**
     * 指定行所屬的章節在清單中的位置（該行之前最近的一個章節）。找不到時回傳 -1。
     * [chapters] 必須依行序排列。
     */
    fun findChapterIndexForLine(chapters: List<ChapterItem>, lineIndex: Int): Int {
        var low = 0
        var high = chapters.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (chapters[mid].lineIndex <= lineIndex) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
