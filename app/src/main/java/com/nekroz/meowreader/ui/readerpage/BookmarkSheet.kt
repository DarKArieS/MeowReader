package com.nekroz.meowreader.ui.readerpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.bottomSheetContentMaxHeight
import com.nekroz.meowreader.ui.common.keepScrollInsideSheet
import kotlinx.coroutines.launch

/**
 * 書籤清單。以底部面板呈現，標題列右邊是「加入目前位置」。
 * 每一列顯示該行的開頭文字與行號，點一下跳過去，右邊可以刪除。
 *
 * @param bookmarks 書籤的行索引（0-based），由小到大
 * @param lines 檔案目前的內容，用來顯示每個書籤的預覽文字
 * @param currentLineIndex 「加入目前位置」要加的那一行（打開清單當下最上方可見的那一行）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    bookmarks: List<Int>,
    lines: List<String>,
    currentLineIndex: Int,
    onAddCurrent: () -> Unit,
    onBookmarkClick: (lineIndex: Int) -> Unit,
    onRemove: (lineIndex: Int) -> Unit,
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
                    text = stringResource(R.string.bookmark_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                // 目前這一行已經是書籤就不必再加。
                IconButton(onClick = onAddCurrent, enabled = currentLineIndex !in bookmarks) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.bookmark_add_current))
                }
            }

            if (bookmarks.isEmpty()) {
                Text(
                    text = stringResource(R.string.bookmark_empty),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .keepScrollInsideSheet(),
            ) {
                items(bookmarks, key = { it }) { lineIndex ->
                    BookmarkRow(
                        lineIndex = lineIndex,
                        preview = lines.getOrNull(lineIndex).orEmpty(),
                        onClick = {
                            // 先讓面板滑下去再通知外面，否則面板會直接消失而沒有收合動畫。
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onBookmarkClick(lineIndex) }
                        },
                        onRemove = { onRemove(lineIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(lineIndex: Int, preview: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // 空白行沒有內容可以預覽，改顯示提示，不然整列看起來是空的。
                text = preview.trim().ifEmpty { stringResource(R.string.bookmark_blank_line) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (preview.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.reader_line_number, lineIndex + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.bookmark_remove))
        }
    }
}
