package com.nekroz.meowreader.ui.readerpage

import android.content.ClipData
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nekroz.meowreader.R
import kotlinx.coroutines.launch

/** 原文那一行的讀取狀態。 */
private sealed interface RawLine {
    data object Loading : RawLine
    data class Loaded(val text: String?) : RawLine
}

/**
 * 單一行的編輯視窗，讓對照原文改字不必離開閱讀頁。
 * 上半部是 raw 資料夾內的原文（唯讀，可複製），下半部是目前檔案的正文，可以直接改完存檔。
 *
 * @param loadRawText 讀取原文檔案中對應的那一行；原文沒有這一行時回傳 null
 * @param onSave 寫回檔案。成功回傳 null，失敗回傳要顯示的錯誤訊息
 */
@Composable
fun LineEditDialog(
    lineNumber: Int,
    initialText: String,
    hasRawFile: Boolean,
    loadRawText: suspend () -> String?,
    onSave: suspend (String) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    // 游標放在結尾而不是選取整行，避免一打字就整行被蓋掉。
    var textValue by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val rawLine by produceState<RawLine>(initialValue = RawLine.Loading) {
        if (hasRawFile) value = RawLine.Loaded(loadRawText())
    }

    /**
     * 寫回檔案。成功就直接關閉；失敗則把對話框留著並顯示原因，使用者剛打的內容才不會白打。
     */
    fun save() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val error = onSave(textValue.text)
            isSaving = false
            if (error == null) onDismiss() else errorMessage = error
        }
    }

    ImeSafeAlertDialog(
        // 寫檔途中不讓對話框被關掉：關掉會連同這次存檔的後續處理（重讀檔案）一起取消。
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.line_edit_title, lineNumber)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 沒有對應的原文檔案就整段拿掉，只留正文的部分。
                if (hasRawFile) {
                    val rawText = (rawLine as? RawLine.Loaded)?.text
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.line_edit_raw),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // 取不到原文時複製沒有意義。
                        TextButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText(RAW_CLIP_LABEL, rawText.orEmpty()))
                                    )
                                }
                            },
                            enabled = rawText != null,
                        ) {
                            Text(stringResource(R.string.action_copy))
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        SelectionContainer {
                            Text(
                                text = when (val raw = rawLine) {
                                    RawLine.Loading -> ""
                                    is RawLine.Loaded -> raw.text ?: stringResource(R.string.line_edit_raw_missing)
                                },
                                modifier = Modifier
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = stringResource(R.string.line_edit_text),
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val hasText = textValue.text.isNotEmpty()
                Box {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            // 一行就是一行：換行字元不能進到內容裡，按 Enter 視為存檔。
                            if ('\n' in newValue.text) save() else textValue = newValue
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        isError = errorMessage != null,
                        // trailingIcon 會垂直置中，多行時不在右下角，所以這裡只用來讓出右側寬度，
                        // 文字才不會跑到清除按鈕底下；真正的按鈕疊在外層 Box 的右下角。
                        trailingIcon = if (hasText) {
                            { Spacer(Modifier.size(CLEAR_BUTTON_SIZE)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )
                    if (hasText) {
                        IconButton(
                            onClick = { textValue = TextFieldValue("") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(CLEAR_BUTTON_SIZE),
                            enabled = !isSaving,
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = !isSaving) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/**
 * 外觀與用法同 Material3 的 AlertDialog，差別在對話框視窗佔滿整個 App 區域，鍵盤佔掉的空間由
 * Compose 自己用 insets 讓出來，系統不會因為鍵盤去縮放視窗。
 *
 * 為什麼不用 AlertDialog：它的視窗高度是「依內容而定」，鍵盤彈出時又要被系統裁到鍵盤上方。
 * 在分割畫面這類矮視窗裡，內容依可用高度量出來比裁切後的視窗高，對話框便不斷要求較高的視窗、
 * 被裁小、再把 focus 中的輸入框捲進畫面、又重新要求……變成捲動與重新排版的無限迴圈。
 * 視窗大小固定以後，縮版面與捲動都只在 Compose 裡發生一次就收斂。
 */
@Composable
private fun ImeSafeAlertDialog(
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

private val CLEAR_BUTTON_SIZE = 48.dp

private const val RAW_CLIP_LABEL = "raw"
