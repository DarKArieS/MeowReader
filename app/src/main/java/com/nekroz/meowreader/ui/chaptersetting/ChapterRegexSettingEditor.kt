package com.nekroz.meowreader.ui.chaptersetting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.NumberField

/**
 * 章節抓取設定區塊（Regex 清單、標題字數上限、開頭跳過行數）。
 * 由全域設定（編輯全域預設值）與單一檔案的章節設定對話框共用。
 */
@Composable
fun ChapterRegexSettingEditor(
    state: ChapterRegexSettingState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.chapter_setting_title))
        SectionHint(stringResource(R.string.chapter_setting_hint))
        Spacer(Modifier.height(8.dp))

        // 列數通常只有個位數，而且外層對話框本身要能捲動，這裡不用 LazyColumn。
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (entry in state.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = entry.pattern,
                        onValueChange = { entry.pattern = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text(stringResource(R.string.chapter_setting_regex_label)) },
                    )
                    IconButton(onClick = { state.remove(entry) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.chapter_setting_remove_row),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = state::add) {
                Text(stringResource(R.string.chapter_setting_add))
            }
            OutlinedButton(onClick = state::restoreDefaults) {
                Text(stringResource(R.string.chapter_setting_restore_defaults))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.chapter_setting_title_max_length))
        SectionHint(stringResource(R.string.chapter_setting_title_max_length_hint))
        NumberField(
            text = state.titleMaxLengthText,
            onTextChange = { state.titleMaxLengthText = it },
            onStep = state::stepTitleMaxLength,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.chapter_setting_skip_lines))
        SectionHint(stringResource(R.string.chapter_setting_skip_lines_hint))
        NumberField(
            text = state.skipLinesText,
            onTextChange = { state.skipLinesText = it },
            onStep = state::stepSkipLines,
            modifier = Modifier.padding(top = 8.dp),
        )

        state.invalidPattern?.let { pattern ->
            Text(
                text = stringResource(R.string.chapter_setting_regex_error, pattern),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
