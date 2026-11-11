package com.nekroz.meowreader.repo

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 檔案不是合法的 UTF-8／UTF-16，照樣寫回去會毀掉整個檔案。 */
class UndecodableFileException : IOException()

/** 要改的行號超出檔案範圍，檔案可能已被其他程式改過。 */
class LineOutOfRangeException : IOException()

/**
 * 文字檔內容（位元組）與「行」之間的轉換。純運算、不碰任何 Android API，
 * 讓「改一行再寫回去」這種寫壞了會毀掉使用者檔案的邏輯可以單獨測試。
 *
 * 編碼判斷：有 BOM 就照 BOM（UTF-8 / UTF-16 LE / UTF-16 BE），沒有則視為 UTF-8。
 * 換行：CR / LF / CRLF 都算換行，檔案結尾的換行不會多產生一個空行。
 */
object TextFileCodec {

    /** 把檔案內容切成行。解不出來的位元組以 U+FFFD 代替，至少其餘內容還讀得到。 */
    fun readLines(bytes: ByteArray): List<String> =
        splitKeepingLineEndings(decode(bytes, strict = false).text).map { it.text }

    /**
     * 把指定的一行換成 [newText]，回傳要寫回檔案的完整內容。只重組被編輯的那一行，
     * 其餘內容與原本的編碼、BOM、換行字元原封不動保留，避免改一行就把整個檔案的格式換掉。
     *
     * @throws UndecodableFileException 檔案不是 UTF-8／UTF-16，解碼後再寫回會毀掉內容
     * @throws LineOutOfRangeException 行號超出檔案範圍
     */
    fun replaceLine(bytes: ByteArray, lineIndex: Int, newText: String): ByteArray {
        // 嚴格解碼：不是 UTF-8 的檔案（例如 Big5）寬鬆解碼會被換成一堆 U+FFFD，
        // 照樣寫回去就是把整個檔案毀掉，而不只是改壞被編輯的那一行。
        val decoded = decode(bytes, strict = true)

        val lines = splitKeepingLineEndings(decoded.text)
        if (lineIndex !in lines.indices) throw LineOutOfRangeException()
        lines[lineIndex] = Line(newText, lines[lineIndex].ending)

        val content = buildString(decoded.text.length + newText.length) {
            for (line in lines) append(line.text).append(line.ending)
        }
        return decoded.bom + content.toByteArray(decoded.charset)
    }

    private class Decoded(val text: String, val charset: Charset, val bom: ByteArray)

    private class Line(val text: String, val ending: String)

    private fun decode(bytes: ByteArray, strict: Boolean): Decoded {
        val (charset, bomLength) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }

        val action = if (strict) CodingErrorAction.REPORT else CodingErrorAction.REPLACE
        val decoder = charset.newDecoder()
            .onMalformedInput(action)
            .onUnmappableCharacter(action)

        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes, bomLength, bytes.size - bomLength)).toString()
        } catch (_: CharacterCodingException) {
            throw UndecodableFileException()
        }
        return Decoded(text, charset, bytes.copyOfRange(0, bomLength))
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it].toByte() }
    }

    /**
     * 依 CR / LF / CRLF 切行，並把每一行後面接的換行字元一起留著，重組時才能還原成原本的樣子。
     */
    private fun splitKeepingLineEndings(content: String): MutableList<Line> {
        val result = ArrayList<Line>()
        var start = 0
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c != '\r' && c != '\n') {
                i++
                continue
            }

            val isCrLf = c == '\r' && i + 1 < content.length && content[i + 1] == '\n'
            val ending = if (isCrLf) "\r\n" else c.toString()
            result.add(Line(content.substring(start, i), ending))
            i += ending.length
            start = i
        }

        if (start < content.length) result.add(Line(content.substring(start), ""))
        return result
    }
}
