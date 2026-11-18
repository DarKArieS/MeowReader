package com.nekroz.meowreader.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 外觀與用法同 Material3 的 AlertDialog，差別在對話框視窗佔滿整個 App 區域，鍵盤佔掉的空間由
 * Compose 自己用 insets 讓出來，系統不會因為鍵盤去縮放視窗。內容裡有文字輸入框的對話框都用這個。
 *
 * 為什麼不用 AlertDialog：它的視窗高度是「依內容而定」，鍵盤彈出時又要被系統裁到鍵盤上方。
 * 在分割畫面這類矮視窗裡，內容依可用高度量出來比裁切後的視窗高，對話框便不斷要求較高的視窗、
 * 被裁小、再把 focus 中的輸入框捲進畫面、又重新要求……變成捲動與重新排版的無限迴圈。
 * 視窗大小固定以後，縮版面與捲動都只在 Compose 裡發生一次就收斂。
 */
@Composable
fun ImeSafeAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 視窗佔滿整個 App 區域，系統分不出「點到對話框外面」，要自己處理。
                .pointerInput(Unit) { detectTapGestures { currentOnDismissRequest() } }
                .safeDrawingPadding()
                .padding(DIALOG_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    // 點在卡片空白處不算點到外面。
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(Modifier.padding(DIALOG_PADDING)) {
                    CompositionLocalProvider(
                        LocalContentColor provides AlertDialogDefaults.titleContentColor,
                        LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                    ) {
                        Box(Modifier.padding(bottom = 16.dp)) { title() }
                    }
                    // 內容可以縮：空間不夠（例如鍵盤彈出）時由內容自己捲動，標題與按鈕永遠看得到。
                    CompositionLocalProvider(
                        LocalContentColor provides AlertDialogDefaults.textContentColor,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) {
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .padding(bottom = DIALOG_PADDING),
                        ) { text() }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

private val DIALOG_PADDING = 24.dp
