package com.nekroz.meowreader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R

/**
 * 「− 數值 ＋」的步進器，數值只顯示不能直接輸入。
 */
@Composable
fun ValueStepper(
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onDecrease, enabled = decreaseEnabled) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease))
        }
        Text(
            text = valueText,
            modifier = Modifier.widthIn(min = 48.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = onIncrease, enabled = increaseEnabled) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
        }
    }
}

/**
 * 可以直接輸入、也可以用 −／＋ 微調的整數欄位。
 *
 * 輸入過程中的文字原樣保留在 [text]（打到一半的數字不能被夾到範圍內，否則根本打不出來），
 * 範圍檢查交給呼叫端在步進與存檔時處理。
 */
@Composable
fun NumberField(
    text: String,
    onTextChange: (String) -> Unit,
    onStep: (direction: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = { onStep(-1) }) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { value -> onTextChange(value.filter { it.isDigit() }.take(MAX_DIGITS)) },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        FilledTonalIconButton(onClick = { onStep(1) }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
        }
    }
}

private const val MAX_DIGITS = 5
