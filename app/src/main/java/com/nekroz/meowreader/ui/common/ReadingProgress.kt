package com.nekroz.meowreader.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import java.util.Locale
import kotlin.math.roundToInt

/** 閱讀進度：百分比文字加進度環。[percent] 範圍 0~100。 */
@Composable
fun ReadingProgress(percent: Double) {
    val description = stringResource(
        R.string.main_progress_description,
        String.format(Locale.ROOT, "%.2f", percent),
    )
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.main_progress_label, percent.roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CircularProgressIndicator(
            progress = { (percent / 100.0).toFloat() },
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
    }
}
