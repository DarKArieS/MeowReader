package com.nekroz.meowreader.repo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextFileCodecTest {

    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    private fun utf8(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test
    fun `readLines splits on CR, LF and CRLF`() {
        assertEquals(listOf("一", "二", "三", "四"), TextFileCodec.readLines(utf8("一\r\n二\n三\r四")))
    }

    @Test
    fun `readLines keeps blank lines but not a trailing empty line`() {
        assertEquals(listOf("一", "", "二"), TextFileCodec.readLines(utf8("一\n\n二\n")))
        assertEquals(listOf("一", ""), TextFileCodec.readLines(utf8("一\n\n")))
        assertEquals(emptyList<String>(), TextFileCodec.readLines(utf8("")))
    }

    @Test
    fun `readLines honours the byte order mark`() {
        assertEquals(listOf("甲", "乙"), TextFileCodec.readLines(utf8Bom + utf8("甲\n乙")))
        assertEquals(
            listOf("甲", "乙"),
            TextFileCodec.readLines(utf16LeBom + "甲\r\n乙".toByteArray(Charsets.UTF_16LE)),
        )
        assertEquals(
            listOf("甲", "乙"),
            TextFileCodec.readLines(utf16BeBom + "甲\n乙".toByteArray(Charsets.UTF_16BE)),
        )
    }

    @Test
    fun `readLines substitutes undecodable bytes instead of failing`() {
        val big5 = "中文".toByteArray(charset("Big5"))

        val lines = TextFileCodec.readLines(big5 + utf8("\nok"))

        assertEquals(2, lines.size)
        assertEquals("ok", lines[1])
    }

    @Test
    fun `replaceLine changes only the target line and keeps every line ending`() {
        val original = utf8("第一行\r\n第二行\n第三行\r第四行")

        val updated = TextFileCodec.replaceLine(original, lineIndex = 1, newText = "改過的第二行")

        assertEquals("第一行\r\n改過的第二行\n第三行\r第四行", updated.toString(Charsets.UTF_8))
    }

    @Test
    fun `replaceLine preserves the trailing newline of the file`() {
        val updated = TextFileCodec.replaceLine(utf8("一\n二\n"), lineIndex = 1, newText = "貳")

        assertEquals("一\n貳\n", updated.toString(Charsets.UTF_8))
    }

    @Test
    fun `replaceLine can edit the last line when the file has no trailing newline`() {
        val updated = TextFileCodec.replaceLine(utf8("一\n二"), lineIndex = 1, newText = "")

        assertEquals("一\n", updated.toString(Charsets.UTF_8))
    }

    @Test
    fun `replaceLine preserves the BOM and encoding`() {
        val utf8WithBom = utf8Bom + utf8("甲\n乙")
        assertArrayEquals(
            utf8Bom + utf8("甲\n丙"),
            TextFileCodec.replaceLine(utf8WithBom, lineIndex = 1, newText = "丙"),
        )

        val utf16 = utf16LeBom + "甲\r\n乙".toByteArray(Charsets.UTF_16LE)
        assertArrayEquals(
            utf16LeBom + "丙\r\n乙".toByteArray(Charsets.UTF_16LE),
            TextFileCodec.replaceLine(utf16, lineIndex = 0, newText = "丙"),
        )
    }

    @Test
    fun `replaceLine leaves the bytes untouched when the text is unchanged`() {
        val original = utf8Bom + utf8("一\r\n\r\n二\n三\r")

        assertArrayEquals(original, TextFileCodec.replaceLine(original, lineIndex = 2, newText = "二"))
    }

    @Test
    fun `replaceLine refuses files that are not valid unicode`() {
        val big5 = "這是 Big5 編碼的內容\n第二行".toByteArray(charset("Big5"))

        assertThrows(UndecodableFileException::class.java) {
            TextFileCodec.replaceLine(big5, lineIndex = 0, newText = "x")
        }
    }

    @Test
    fun `replaceAll replaces every occurrence and keeps line endings`() {
        val original = utf8("貓貓\r\n狗\n貓\r鳥貓")

        val result = TextFileCodec.replaceAll(original, target = "貓", replacement = "喵")

        assertEquals(4, result.count)
        assertEquals("喵喵\r\n狗\n喵\r鳥喵", result.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `replaceAll counts non-overlapping matches`() {
        val result = TextFileCodec.replaceAll(utf8("aaaa\naaa"), target = "aa", replacement = "b")

        assertEquals(3, result.count)
        assertEquals("bb\nba", result.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `replaceAll returns the original bytes when nothing matches`() {
        val original = utf8Bom + utf8("一\r\n二")

        val result = TextFileCodec.replaceAll(original, target = "三", replacement = "x")

        assertEquals(0, result.count)
        assertArrayEquals(original, result.bytes)
    }

    @Test
    fun `replaceAll preserves the BOM and encoding`() {
        val utf16 = utf16LeBom + "甲乙\r\n乙".toByteArray(Charsets.UTF_16LE)

        val result = TextFileCodec.replaceAll(utf16, target = "乙", replacement = "丙")

        assertArrayEquals(utf16LeBom + "甲丙\r\n丙".toByteArray(Charsets.UTF_16LE), result.bytes)
    }

    @Test
    fun `replaceAll refuses files that are not valid unicode`() {
        val big5 = "這是 Big5 編碼的內容".toByteArray(charset("Big5"))

        assertThrows(UndecodableFileException::class.java) {
            TextFileCodec.replaceAll(big5, target = "x", replacement = "y")
        }
    }

    @Test
    fun `replaceAll rejects line breaks in the arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            TextFileCodec.replaceAll(utf8("一\n二"), target = "一\n", replacement = "x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TextFileCodec.replaceAll(utf8("一\n二"), target = "一", replacement = "x\ny")
        }
    }

    @Test
    fun `replaceLine rejects a line index outside the file`() {
        val original = utf8("一\n二\n")

        assertThrows(LineOutOfRangeException::class.java) {
            TextFileCodec.replaceLine(original, lineIndex = 2, newText = "x")
        }
        assertThrows(LineOutOfRangeException::class.java) {
            TextFileCodec.replaceLine(original, lineIndex = -1, newText = "x")
        }
    }
}
