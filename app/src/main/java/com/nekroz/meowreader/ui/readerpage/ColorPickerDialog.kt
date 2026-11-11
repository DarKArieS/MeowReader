package com.nekroz.meowreader.ui.readerpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.toHexString

/**
 * 簡單的選色器：常用的閱讀配色 + 色相／飽和度／亮度三條滑桿。
 * Compose 沒有內建選色器，閱讀器只需要挑背景色與文字顏色，不必為此多引入一個函式庫。
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = color.toHexString(),
                        // 色碼疊在預覽色塊上，依底色深淺換成看得清楚的字色。
                        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (preset in PRESET_COLORS) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable(onClickLabel = preset.toHexString()) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(preset.toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    brightness = hsv[2]
                                },
                        )
                    }
                }

                ChannelSlider(
                    label = stringResource(R.string.color_picker_hue),
                    value = hue,
                    valueRange = 0f..360f,
                    onValueChange = { hue = it },
                )
                ChannelSlider(
                    label = stringResource(R.string.color_picker_saturation),
                    value = saturation,
                    valueRange = 0f..1f,
                    onValueChange = { saturation = it },
                )
                ChannelSlider(
                    label = stringResource(R.string.color_picker_brightness),
                    value = brightness,
                    valueRange = 0f..1f,
                    onValueChange = { brightness = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.semantics { contentDescription = label },
        valueRange = valueRange,
    )
}

/** 常見的閱讀配色：白、紙色、米黃、護眼綠、淺灰、深灰、近黑、黑。 */
private val PRESET_COLORS = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF5F0E1),
    Color(0xFFE8DCC0),
    Color(0xFFC7EDCC),
    Color(0xFFD0D0D0),
    Color(0xFF333333),
    Color(0xFF1E1E1E),
    Color(0xFF000000),
)
