package com.nekroz.meowreader.repo.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderSetting(
    /** 字級（sp）。 */
    val fontSize: Double = DEFAULT_FONT_SIZE,

    /** 行距倍率，實際行高為 fontSize * lineSpacing。 */
    val lineSpacing: Double = DEFAULT_LINE_SPACING,

    /** #RRGGBB */
    val customBackgroundColor: String? = null,
    val useCustomBackgroundColor: Boolean = false,

    /** #RRGGBB */
    val customForegroundColor: String? = null,
    val useCustomForegroundColor: Boolean = false,

    /**
     * ReaderPage 底部面板（返回、進度條、設定）是否顯示。
     * 使用者點兩下收合後，下次進 ReaderPage 維持同樣狀態。
     */
    val showBottomPanel: Boolean = true,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 20.0
        const val MIN_FONT_SIZE = 8.0
        const val MAX_FONT_SIZE = 72.0

        // 下限不設 1.0：行高等於字級時中文字的上下會被裁掉。
        const val MIN_LINE_SPACING = 1.2
        const val MAX_LINE_SPACING = 3.0
        const val LINE_SPACING_STEP = 0.1
        const val DEFAULT_LINE_SPACING = 1.5
    }
}
