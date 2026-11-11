package com.nekroz.meowreader.repo.chapter

import kotlinx.serialization.Serializable

/**
 * 由章節 Regex 掃描出來的一個章節位置。會序列化進 appConfig.json 的閱讀紀錄。
 */
@Serializable
data class ChapterItem(
    /** 章節標題，取自比對到的整行文字（已去頭尾空白）。 */
    val title: String,
    /** 章節標題所在的 0-based 行索引。 */
    val lineIndex: Int,
) {
    /** 顯示用的 1-based 行號。 */
    val lineNumber: Int get() = lineIndex + 1
}
