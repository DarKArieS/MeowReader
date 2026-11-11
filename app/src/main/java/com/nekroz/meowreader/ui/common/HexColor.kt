package com.nekroz.meowreader.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 把 #RRGGBB 轉成顏色。格式不對時回傳 null，由呼叫端退回預設顏色。
 */
fun parseHexColor(text: String?): Color? {
    val value = text?.trim() ?: return null
    if (value.length != 7 || value[0] != '#') return null
    val rgb = value.substring(1).toIntOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or rgb)
}

/** 轉成 #RRGGBB（忽略透明度）。 */
fun Color.toHexString(): String = "#%06X".format(toArgb() and 0xFFFFFF)
