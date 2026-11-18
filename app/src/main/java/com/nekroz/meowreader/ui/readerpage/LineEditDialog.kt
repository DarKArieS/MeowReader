package com.nekroz.meowreader.ui.readerpage

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.ImeSafeAlertDialog
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

private val CLEAR_BUTTON_SIZE = 48.dp

private const val RAW_CLIP_LABEL = "raw"
