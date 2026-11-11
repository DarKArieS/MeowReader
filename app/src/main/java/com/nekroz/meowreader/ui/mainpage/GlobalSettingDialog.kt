package com.nekroz.meowreader.ui.mainpage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.repo.model.AppTheme
import com.nekroz.meowreader.ui.chaptersetting.ChapterRegexSettingEditor
import com.nekroz.meowreader.ui.chaptersetting.rememberChapterRegexSettingState

/**
 * 不綁定單一檔案的全域設定。目前有外觀（淺色／深色主題）與章節抓取設定，
 * 單一檔案沒有專屬設定時以這裡的章節設定為預設值。
 */
@Composable
fun GlobalSettingDialog(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    initialChapterSetting: ChapterRegexSetting,
    onSave: (ChapterRegexSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val chapterState = rememberChapterRegexSettingState(initialChapterSetting)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.global_setting_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.global_setting_appearance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.global_setting_appearance_hint),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 主題切換即時套用，不需要等按「儲存」。
                ThemeSelector(theme = theme, onThemeChange = onThemeChange)

                Spacer(Modifier.height(24.dp))
                ChapterRegexSettingEditor(state = chapterState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Regex 寫錯就把對話框留著，讓使用者照著錯誤訊息修正。
                    chapterState.trySave()?.let(onSave)
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

@Composable
private fun ThemeSelector(theme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    val options = listOf(
        AppTheme.Default to R.string.theme_default,
        AppTheme.Light to R.string.theme_light,
        AppTheme.Dark to R.string.theme_dark,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, labelRes) ->
            SegmentedButton(
                selected = theme == option,
                onClick = { onThemeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                // 三個選項擠在對話框寬度內，拿掉勾選圖示才放得下「跟隨系統」。
                icon = {},
            ) {
                Text(stringResource(labelRes), maxLines = 1)
            }
        }
    }
}
