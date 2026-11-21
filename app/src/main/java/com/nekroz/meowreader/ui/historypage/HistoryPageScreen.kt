package com.nekroz.meowreader.ui.historypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.ReadingProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 閱讀歷史：依上次開啟時間由新到舊列出讀過的檔案，點一下直接回到閱讀頁。
 * 編輯模式可以勾選部分或全部項目從歷史中清除，閱讀進度不受影響。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPageScreen(
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
    viewModel: HistoryPageViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    // 編輯模式下的返回是離開編輯模式，不是離開頁面。
    BackHandler(enabled = state.isEditing) { viewModel.stopEditing() }

    Scaffold(
        topBar = {
            if (state.isEditing) {
                EditTopBar(
                    selectedCount = state.selectedKeys.size,
                    allSelected = state.allSelected,
                    onClose = viewModel::stopEditing,
                    onToggleSelectAll = viewModel::toggleSelectAll,
                    onClear = { showClearConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        if (state.entries.isNotEmpty()) {
                            IconButton(onClick = { viewModel.startEditing() }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (state.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        val layoutDirection = LocalLayoutDirection.current
        val timeFormatter = remember { DateTimeFormatter.ofPattern(TIME_PATTERN).withZone(ZoneId.systemDefault()) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 底部的系統列留給清單自己用 contentPadding 處理，內容才能延伸到手勢列底下。
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
        ) {
            items(state.entries, key = { it.key }) { entry ->
                HistoryRow(
                    entry = entry,
                    openedAt = timeFormatter.format(Instant.ofEpochMilli(entry.lastOpenedAt)),
                    isEditing = state.isEditing,
                    selected = entry.key in state.selectedKeys,
                    onClick = {
                        if (state.isEditing) {
                            viewModel.toggleSelected(entry.key)
                        } else {
                            viewModel.openFile(entry)
                            onOpenReader()
                        }
                    },
                    // 長按直接進編輯模式並勾選這一列，跟一般清單 App 的習慣一致。
                    onLongClick = {
                        if (state.isEditing) viewModel.toggleSelected(entry.key) else viewModel.startEditing(entry.key)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (showClearConfirm) {
        val count = state.selectedKeys.size
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSelected()
                        showClearConfirm = false
                    },
                    enabled = count > 0,
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.history_selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_exit_edit))
            }
        },
        actions = {
            TextButton(onClick = onToggleSelectAll) {
                Text(stringResource(if (allSelected) R.string.history_deselect_all else R.string.history_select_all))
            }
            IconButton(onClick = onClear, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_clear))
            }
        },
    )
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    openedAt: String,
    isEditing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.file.name) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        supportingContent = {
            // 舊版開啟紀錄沒有資料夾路徑，只顯示時間。
            val folderPath = entry.file.folderPath
            Text(
                text = if (folderPath.isEmpty()) openedAt else "$openedAt · $folderPath",
                // 路徑可能很長，多給一行，最底層的資料夾名稱才不會被截掉。
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (isEditing) {
                // 點整列就會切換勾選，勾選框本身不另外接點擊，免得兩者搶事件。
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.main_item_pinned),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                entry.progressPercent?.let { ReadingProgress(it) }
            }
        },
    )
}

private const val TIME_PATTERN = "yyyy/MM/dd HH:mm"
