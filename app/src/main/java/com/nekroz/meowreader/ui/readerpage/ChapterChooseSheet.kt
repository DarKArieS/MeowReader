package com.nekroz.meowreader.ui.readerpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.repo.chapter.ChapterItem
import com.nekroz.meowreader.repo.chapter.ChapterParser
import com.nekroz.meowreader.ui.common.bottomSheetContentMaxHeight
import com.nekroz.meowreader.ui.common.keepScrollInsideSheet
import kotlinx.coroutines.launch

/**
 * 章節選擇清單。以底部面板呈現，標題列右邊是「章節抓取設定」與「重新抓取章節」。
 *
 * @param currentLineIndex 用哪一行判斷目前正在讀的章節（打開清單當下最上方可見的那一行）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterChooseSheet(
    chapters: List<ChapterItem>,
    isLoading: Boolean,
    currentLineIndex: Int,
    onChapterClick: (ChapterItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenSetting: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 不要「先開一半、再往上拉才全開」的兩段式：一打開就直接展開到內容需要的高度。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = bottomSheetContentMaxHeight()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chapter_list_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onOpenSetting) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.chapter_setting_title))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.chapter_list_refresh))
                }
            }

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                // 沒有章節時改顯示提示文字，而不是一片空白的清單。
                chapters.isEmpty() -> Text(
                    text = stringResource(R.string.chapter_list_empty),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> ChapterList(
                    chapters = chapters,
                    currentLineIndex = currentLineIndex,
                    onChapterClick = { chapter ->
                        // 先讓面板滑下去再通知外面，否則面板會直接消失而沒有收合動畫。
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onChapterClick(chapter) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<ChapterItem>,
    currentLineIndex: Int,
    onChapterClick: (ChapterItem) -> Unit,
) {
    // 標記並停在正在讀的章節：高亮讓人一眼看出目前位置，長篇文章也不必自己捲半天找。
    val currentIndex = remember(chapters, currentLineIndex) {
        ChapterParser.findChapterIndexForLine(chapters, currentLineIndex)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(chapters) {
        // 往上多留一個章節，才看得出目前章節前面還有東西。
        if (currentIndex >= 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .keepScrollInsideSheet(),
    ) {
        itemsIndexed(chapters) { index, chapter ->
            val isCurrent = index == currentIndex
            val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(chapter) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = chapter.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                    fontWeight = fontWeight,
                )
                Text(
                    text = chapter.lineNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = fontWeight,
                )
            }
        }
    }
}
