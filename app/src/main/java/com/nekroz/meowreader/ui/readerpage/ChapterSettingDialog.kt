package com.nekroz.meowreader.ui.readerpage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.ui.chaptersetting.ChapterRegexSettingEditor
import com.nekroz.meowreader.ui.chaptersetting.rememberChapterRegexSettingState

/**
 * 單一檔案專屬的章節抓取設定。內容與全域設定共用同一個 [ChapterRegexSettingEditor]，
 * 差別只在存檔目標是這個檔案的閱讀紀錄而非全域預設值。
 *
 * @param initialSetting 這個檔案已經存起來的自訂設定（不管開關狀態）。使用者關掉「使用預設值」時，
 * 才不會看到空白或全域預設值蓋掉原本填的內容。
 */
@Composable
fun ChapterSettingDialog(
    initialUseDefault: Boolean,
    initialSetting: ChapterRegexSetting,
    onUseDefault: () -> Unit,
    onSave: (ChapterRegexSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    var useDefault by remember { mutableStateOf(initialUseDefault) }
    val chapterState = rememberChapterRegexSettingState(initialSetting)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chapter_setting_file_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = useDefault,
                            role = Role.Checkbox,
                            onValueChange = { useDefault = it },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = useDefault, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.chapter_setting_use_default),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (!useDefault) {
                    ChapterRegexSettingEditor(
                        state = chapterState,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (useDefault) {
                        onUseDefault()
                    } else {
                        // Regex 寫錯就把對話框留著，讓使用者照著錯誤訊息修正。
                        chapterState.trySave()?.let(onSave)
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
