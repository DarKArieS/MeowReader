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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * @param onCountMatches 全文有幾處符合的文字
 * @param onReplaceAll 對整個檔案做取代並寫回
 */
@Composable
fun LineEditDialog(
    lineNumber: Int,
    initialText: String,
    hasRawFile: Boolean,
    loadRawText: suspend () -> String?,
    onSave: suspend (String) -> String?,
    onCountMatches: suspend (target: String) -> Int,
    onReplaceAll: suspend (target: String, replacement: String) -> ReplaceAllResult,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    // 游標放在結尾而不是選取整行，避免一打字就整行被蓋掉。
    var textValue by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    /** 上一次全文取代換掉了幾處，顯示給使用者確認。 */
    var replacedCount by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }

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

                val hasText = textValue.text.isNotEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.line_edit_text),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // 取代的對象是整個檔案，不限這一行。
                    TextButton(onClick = { showReplace = true }, enabled = !isSaving) {
                        Text(stringResource(R.string.action_replace))
                    }
                }
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
                replacedCount?.let { count ->
                    Text(
                        text = stringResource(R.string.line_replace_done, count),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
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

    if (showReplace) {
        ReplaceDialog(
            // 正文裡有選取文字就直接帶進「被取代文字」，省得再打一次。
            initialFind = textValue.text.substring(textValue.selection.min, textValue.selection.max),
            onCountMatches = onCountMatches,
            onReplaceAll = onReplaceAll,
            onReplaced = { target, replacement, count ->
                // 檔案裡這一行已經換好了，輸入框裡還沒存的內容也套用同樣的取代，兩邊才一致；
                // 使用者正在改的字不會因此不見。
                val replaced = textValue.text.replace(target, replacement)
                textValue = TextFieldValue(replaced, TextRange(replaced.length))
                errorMessage = null
                replacedCount = count
                showReplace = false
            },
            onDismiss = { showReplace = false },
        )
    }
}

/**
 * 把整個檔案裡所有的「被取代文字」換成「取代文字」，按下取代就直接寫回檔案。
 * 找不到或寫入失敗時對話框留著並提示，讓使用者改完再試。
 */
@Composable
private fun ReplaceDialog(
    initialFind: String,
    onCountMatches: suspend (target: String) -> Int,
    onReplaceAll: suspend (target: String, replacement: String) -> ReplaceAllResult,
    onReplaced: (target: String, replacement: String, count: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var find by remember { mutableStateOf(TextFieldValue(initialFind, TextRange(initialFind.length))) }
    var replacement by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    /** 按「搜尋」數到的處數；被取代文字一改就清掉，免得顯示的是舊字串的結果。 */
    var foundCount by remember { mutableStateOf<Int?>(null) }
    var isReplacing by remember { mutableStateOf(false) }
    val findFocus = remember { FocusRequester() }
    val notFoundMessage = stringResource(R.string.line_replace_not_found)

    LaunchedEffect(Unit) { findFocus.requestFocus() }

    fun search() {
        val target = find.text
        if (target.isEmpty() || isReplacing) return
        scope.launch {
            val count = onCountMatches(target)
            // 數完之前使用者可能又改了字，那這次的結果就作廢。
            if (find.text != target) return@launch
            if (count == 0) {
                foundCount = null
                errorMessage = notFoundMessage
            } else {
                foundCount = count
                errorMessage = null
            }
        }
    }

    fun apply() {
        val target = find.text
        if (target.isEmpty() || isReplacing) return
        isReplacing = true
        scope.launch {
            val result = onReplaceAll(target, replacement)
            isReplacing = false
            when (result) {
                is ReplaceAllResult.Replaced ->
                    if (result.count == 0) errorMessage = notFoundMessage
                    else onReplaced(target, replacement, result.count)
                is ReplaceAllResult.Failed -> errorMessage = result.message
            }
        }
    }

    ImeSafeAlertDialog(
        // 寫檔途中不讓對話框被關掉，理由同編輯視窗。
        onDismissRequest = { if (!isReplacing) onDismiss() },
        title = { Text(stringResource(R.string.line_replace_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = find,
                    onValueChange = {
                        // 比對只在行內進行，貼上的內容如果夾著換行就去掉。
                        val text = it.text.withoutLineBreaks()
                        if (text != find.text) {
                            errorMessage = null
                            foundCount = null
                        }
                        find = it.copy(text = text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(findFocus),
                    enabled = !isReplacing,
                    label = { Text(stringResource(R.string.line_replace_find)) },
                    isError = errorMessage != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it.withoutLineBreaks() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isReplacing,
                    label = { Text(stringResource(R.string.line_replace_with)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { apply() }),
                )
                foundCount?.let { count ->
                    Text(
                        text = stringResource(R.string.line_replace_found, count),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
            TextButton(onClick = ::apply, enabled = find.text.isNotEmpty() && !isReplacing) {
                Text(stringResource(R.string.line_replace_all))
            }
        },
        // 按鈕列是一個 Row，這裡放兩個按鈕會依序排在「全部取代」左邊。
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isReplacing) {
                Text(stringResource(R.string.action_cancel))
            }
            TextButton(onClick = ::search, enabled = find.text.isNotEmpty() && !isReplacing) {
                Text(stringResource(R.string.action_search))
            }
        },
    )
}

private fun String.withoutLineBreaks(): String = filterNot { it == '\r' || it == '\n' }

private val CLEAR_BUTTON_SIZE = 48.dp

private const val RAW_CLIP_LABEL = "raw"
