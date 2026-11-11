package com.nekroz.meowreader.repo.chapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    private val defaultPatterns = ChapterRegexSetting.DEFAULT_REGEX_LIST

    @Test
    fun `parse finds chapter titles and trims them`() {
        val lines = listOf(
            "書名",
            "",
            "　第一章 開始　",
            "內文內文內文",
            "第2話 繼續",
            "更多內文",
        )

        val chapters = ChapterParser.parse(lines, defaultPatterns, maxTitleLength = 60)

        assertEquals(
            listOf(ChapterItem("第一章 開始", 2), ChapterItem("第2話 繼續", 4)),
            chapters,
        )
        assertEquals(3, chapters[0].lineNumber)
    }

    @Test
    fun `parse skips lines longer than the title limit`() {
        val lines = listOf("第一章", "他想起第三章提到的那件事，於是決定回頭再把整本書從頭到尾讀過一遍。")

        val chapters = ChapterParser.parse(lines, defaultPatterns, maxTitleLength = 10)

        assertEquals(listOf(ChapterItem("第一章", 0)), chapters)
    }

    @Test
    fun `parse ignores the leading skipped lines`() {
        val lines = listOf("第一章", "第二章", "第三章")

        val chapters = ChapterParser.parse(lines, defaultPatterns, maxTitleLength = 60, skipLines = 2)

        assertEquals(listOf(ChapterItem("第三章", 2)), chapters)
    }

    @Test
    fun `parse yields one chapter per line even when several patterns match`() {
        val chapters = ChapterParser.parse(listOf("第一章"), listOf("第", "章"), maxTitleLength = 60)

        assertEquals(1, chapters.size)
    }

    @Test
    fun `parse ignores blank and invalid patterns`() {
        val chapters = ChapterParser.parse(listOf("第一章"), listOf("", "(", "第.+章"), maxTitleLength = 60)

        assertEquals(listOf(ChapterItem("第一章", 0)), chapters)
        assertTrue(ChapterParser.parse(listOf("第一章"), listOf("("), maxTitleLength = 60).isEmpty())
        assertTrue(ChapterParser.parse(listOf("第一章"), null, maxTitleLength = 60).isEmpty())
    }

    @Test
    fun `isValidPattern rejects blank and malformed patterns`() {
        assertTrue(ChapterParser.isValidPattern("第([0-9]+)章"))
        assertFalse(ChapterParser.isValidPattern(""))
        assertFalse(ChapterParser.isValidPattern("   "))
        assertFalse(ChapterParser.isValidPattern(null))
        assertFalse(ChapterParser.isValidPattern("([0-9"))
    }

    @Test
    fun `findChapterIndexForLine returns the nearest chapter at or before the line`() {
        val chapters = listOf(ChapterItem("一", 10), ChapterItem("二", 20), ChapterItem("三", 30))

        assertEquals(-1, ChapterParser.findChapterIndexForLine(chapters, 0))
        assertEquals(-1, ChapterParser.findChapterIndexForLine(chapters, 9))
        assertEquals(0, ChapterParser.findChapterIndexForLine(chapters, 10))
        assertEquals(0, ChapterParser.findChapterIndexForLine(chapters, 19))
        assertEquals(1, ChapterParser.findChapterIndexForLine(chapters, 20))
        assertEquals(2, ChapterParser.findChapterIndexForLine(chapters, 999))
        assertEquals(-1, ChapterParser.findChapterIndexForLine(emptyList(), 5))
    }
}
