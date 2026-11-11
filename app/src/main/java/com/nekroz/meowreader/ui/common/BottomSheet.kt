package com.nekroz.meowreader.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** 底部面板最高佔螢幕高度的比例。 */
private const val BOTTOM_SHEET_MAX_HEIGHT_FRACTION = 0.8f

/** ModalBottomSheet 預設拖曳把手佔的高度（上下各 22dp 留白 + 4dp 把手）。 */
private val DRAG_HANDLE_HEIGHT = 48.dp

/**
 * 底部面板「內容」的高度上限，讓整個面板（把手 + 內容 + 導覽列留白）最高約佔螢幕八成。
 * 內容再多也不會把整個畫面蓋滿，上方永遠留一截看得到底下的內文，超出的部分在面板裡捲動。
 *
 * 要加在面板內容上，不能加在 ModalBottomSheet 的 modifier：那樣面板會被釘在螢幕頂端，
 * 底下露出一截遮罩。
 */
/**
 * 面板裡可捲動的內容捲到頭之後，剩下的拖動和甩動不要交給面板。
 *
 * ModalBottomSheet 預設會把清單捲到頂後多出來的手勢拿去拖動面板，甩一下就可能把面板整個收掉，
 * 跟捲動軸互相搶手勢。加了這個以後，在內容上滑動只會捲動內容；要拖動或下拉關閉面板，
 * 改從上方的把手或標題列拖。
 *
 * 要加在可捲動元件的外層（modifier 鏈裡放在 verticalScroll 之前，或包住 LazyColumn 的容器上）。
 */
fun Modifier.keepScrollInsideSheet(): Modifier = nestedScroll(ConsumeRemainingScroll)

private object ConsumeRemainingScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

@Composable
fun bottomSheetContentMaxHeight(): Dp {
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(LocalDensity.current) { windowHeightPx.toDp() }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return (windowHeight * BOTTOM_SHEET_MAX_HEIGHT_FRACTION - DRAG_HANDLE_HEIGHT - navigationBar)
        .coerceAtLeast(0.dp)
}
