package com.nekroz.meowreader.ui.readerpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.repo.model.ReaderSetting
import com.nekroz.meowreader.ui.common.ValueStepper
import com.nekroz.meowreader.ui.common.parseHexColor
import com.nekroz.meowreader.ui.common.toHexString
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 閱讀設定：文字大小、行距、背景與文字顏色。每一項都是改了立刻生效，沒有「儲存」按鈕。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    setting: ReaderSetting,
    onFontSizeChange: (delta: Double) -> Unit,
    onLineSpacingChange: (steps: Int) -> Unit,
    onUseCustomColorsChange: (Boolean) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onTextColorChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val useCustomColors = setting.useCustomBackgroundColor
    val themeBackground = MaterialTheme.colorScheme.background
    val themeTextColor = MaterialTheme.colorScheme.onBackground

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 不要遮罩：調整字級與顏色時要看得到後面內文的實際效果。
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_setting_title),
                style = MaterialTheme.typography.titleMedium,
            )

            SettingRow(label = stringResource(R.string.reader_setting_font_size)) {
                ValueStepper(
                    valueText = setting.fontSize.roundToInt().toString(),
                    onDecrease = { onFontSizeChange(-1.0) },
                    onIncrease = { onFontSizeChange(1.0) },
                    decreaseEnabled = setting.fontSize > ReaderSetting.MIN_FONT_SIZE,
                    increaseEnabled = setting.fontSize < ReaderSetting.MAX_FONT_SIZE,
                )
            }

            SettingRow(label = stringResource(R.string.reader_setting_line_spacing)) {
                ValueStepper(
                    valueText = String.format(Locale.ROOT, "%.1f", setting.lineSpacing),
                    onDecrease = { onLineSpacingChange(-1) },
                    onIncrease = { onLineSpacingChange(1) },
                    decreaseEnabled = setting.lineSpacing > ReaderSetting.MIN_LINE_SPACING,
                    increaseEnabled = setting.lineSpacing < ReaderSetting.MAX_LINE_SPACING,
                )
            }

            Text(
                text = stringResource(R.string.reader_setting_colors),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !useCustomColors,
                    onClick = { onUseCustomColorsChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.reader_setting_color_system))
                }
                SegmentedButton(
                    selected = useCustomColors,
                    onClick = {
                        // 第一次切到自訂時還沒有填過色碼，先帶入目前主題的顏色當起點，
                        // 畫面不會突然變樣，欄位也不會是空的。
                        if (parseHexColor(setting.customBackgroundColor) == null) {
                            onBackgroundColorChange(themeBackground.toHexString())
                        }
                        if (parseHexColor(setting.customForegroundColor) == null) {
                            onTextColorChange(themeTextColor.toHexString())
                        }
                        onUseCustomColorsChange(true)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.reader_setting_color_custom))
                }
            }

            if (useCustomColors) {
                ColorSettingRow(
                    label = stringResource(R.string.reader_setting_background_color),
                    pickerTitle = stringResource(R.string.reader_setting_pick_background_color),
                    colorText = setting.customBackgroundColor,
                    fallbackColor = themeBackground,
                    onColorChange = onBackgroundColorChange,
                )
                ColorSettingRow(
                    label = stringResource(R.string.reader_setting_text_color),
                    pickerTitle = stringResource(R.string.reader_setting_pick_text_color),
                    colorText = setting.customForegroundColor,
                    fallbackColor = themeTextColor,
                    onColorChange = onTextColorChange,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        content()
    }
}

/**
 * 一個顏色設定：色塊預覽 + #RRGGBB 輸入框 + 選色器。
 *
 * @param colorText 設定檔裡目前存的色碼
 * @param fallbackColor 色碼無效時色塊顯示的顏色（也就是內文實際會用的顏色）
 */
@Composable
private fun ColorSettingRow(
    label: String,
    pickerTitle: String,
    colorText: String?,
    fallbackColor: Color,
    onColorChange: (String) -> Unit,
) {
    // 輸入中的文字先留在本地：打到一半的色碼不是合法顏色，不能每個按鍵都寫進設定檔。
    // 設定檔裡的值從別處被改掉（例如用選色器選了新顏色）時再同步回來。
    var text by remember(colorText) { mutableStateOf(colorText.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }
    val color = parseHexColor(text) ?: parseHexColor(colorText) ?: fallbackColor

    Text(
        text = label,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable(onClickLabel = pickerTitle) { showPicker = true },
        )
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value.trim().take(HEX_LENGTH)
                parseHexColor(text)?.let { onColorChange(it.toHexString()) }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            isError = parseHexColor(text) == null,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text(stringResource(R.string.reader_setting_color_hex_hint)) },
        )
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.reader_setting_pick_color))
        }
    }
    Spacer(Modifier.height(4.dp))

    if (showPicker) {
        ColorPickerDialog(
            title = pickerTitle,
            initialColor = color,
            onConfirm = { picked ->
                onColorChange(picked.toHexString())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** "#RRGGBB" 的長度。 */
private const val HEX_LENGTH = 7
