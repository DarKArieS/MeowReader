package com.nekroz.meowreader.ui.mainpage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekroz.meowreader.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPageScreen(
    onOpenReader: () -> Unit,
    viewModel: MainPageViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showConfigCorruptNotice by viewModel.showConfigCorruptNotice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noAppToOpenMessage = stringResource(R.string.main_no_app_to_open)

    var showGlobalSetting by rememberSaveable { mutableStateOf(false) }
    var showRawConfig by rememberSaveable { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }
    // 選擇器預設開在目前的資料夾，換資料夾時不必從頭找起。
    val pickFolder = { folderPicker.launch(state.folderUri?.toUri()) }

    // 頁面回到前景（含從閱讀頁返回）時重新整理清單。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }

    // 系統的返回手勢在子資料夾內是「回上一層」，到了根資料夾才是離開 App。
    BackHandler(enabled = state.canGoBack) { viewModel.backToParent() }

    Scaffold(
        topBar = {
            MainTopBar(
                title = if (state.hasFolder) state.folderName else stringResource(R.string.app_name),
                canGoBack = state.canGoBack,
                sortMode = state.sortMode,
                onBack = viewModel::backToParent,
                onSortModeChange = viewModel::setSortMode,
                onSelectFolder = pickFolder,
                onGlobalSetting = { showGlobalSetting = true },
                onRawConfig = { showRawConfig = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 底部的系統列留給清單自己用 contentPadding 處理，內容才能延伸到手勢列底下。
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
        ) {
            if (state.canGoBack) {
                Text(
                    text = state.breadcrumb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val folderUri = state.folderUri
            when {
                folderUri == null -> MessagePanel(
                    title = stringResource(R.string.main_no_folder_title),
                    message = stringResource(R.string.main_no_folder_message),
                    onSelectFolder = pickFolder,
                )

                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.loadFailed -> MessagePanel(
                    message = stringResource(R.string.main_load_failed),
                    onSelectFolder = pickFolder,
                )

                state.items.isEmpty() -> MessagePanel(
                    message = stringResource(R.string.main_empty_folder),
                    onSelectFolder = null,
                )

                // 每個資料夾各自一份捲動狀態：切換資料夾時舊的那份會先存檔再丟掉。
                else -> key(folderUri) {
                    FolderList(
                        items = state.items,
                        initialIndex = remember { viewModel.getSavedScrollIndex(folderUri) },
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                        onSaveScrollPosition = { index -> viewModel.saveScrollPosition(folderUri, index) },
                        onItemClick = { item ->
                            if (item.isFolder) {
                                viewModel.enterFolder(item)
                            } else {
                                viewModel.openFile(item)
                                onOpenReader()
                            }
                        },
                        onTogglePin = viewModel::togglePin,
                        onOpenWithOtherApp = { item ->
                            if (!openWithOtherApp(context, item)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(noAppToOpenMessage)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showGlobalSetting) {
        GlobalSettingDialog(
            theme = state.theme,
            onThemeChange = viewModel::setTheme,
            initialChapterSetting = viewModel.globalChapterSetting,
            onSave = { setting ->
                viewModel.saveGlobalChapterSetting(setting)
                showGlobalSetting = false
            },
            onDismiss = { showGlobalSetting = false },
        )
    }

    if (showRawConfig) {
        RawConfigDialog(
            json = remember { viewModel.configJson() },
            onDismiss = { showRawConfig = false },
        )
    }

    if (showConfigCorruptNotice) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfigCorruptNotice,
            title = { Text(stringResource(R.string.main_config_corrupt_title)) },
            text = { Text(stringResource(R.string.main_config_corrupt_message, viewModel.corruptBackupFileName)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissConfigCorruptNotice) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    canGoBack: Boolean,
    sortMode: FileSortMode,
    onBack: () -> Unit,
    onSortModeChange: (FileSortMode) -> Unit,
    onSelectFolder: () -> Unit,
    onGlobalSetting: () -> Unit,
    onRawConfig: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.main_back_to_parent),
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.main_sort))
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    SortMenuItem(
                        label = stringResource(R.string.main_sort_by_name),
                        selected = sortMode == FileSortMode.Name,
                        onClick = {
                            onSortModeChange(FileSortMode.Name)
                            sortMenuExpanded = false
                        },
                    )
                    SortMenuItem(
                        label = stringResource(R.string.main_sort_by_modified_date),
                        selected = sortMode == FileSortMode.ModifiedDate,
                        onClick = {
                            onSortModeChange(FileSortMode.ModifiedDate)
                            sortMenuExpanded = false
                        },
                    )
                }
            }
            IconButton(onClick = onSelectFolder) {
                Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.main_select_folder))
            }
            Box {
                IconButton(onClick = { moreMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.main_more))
                }
                DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_global_setting)) },
                        onClick = {
                            moreMenuExpanded = false
                            onGlobalSetting()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.main_raw_config)) },
                        onClick = {
                            moreMenuExpanded = false
                            onRawConfig()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SortMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            // 沒選中的項目也保留圖示的位置，兩列文字才會對齊。
            if (selected) Icon(Icons.Default.Check, contentDescription = null) else Spacer(Modifier.size(24.dp))
        },
    )
}

/** 清單以外的狀態：還沒選資料夾、讀取失敗、空資料夾。 */
@Composable
private fun MessagePanel(
    message: String,
    onSelectFolder: (() -> Unit)?,
    title: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onSelectFolder != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSelectFolder) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.main_select_folder))
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun FolderList(
    items: List<FileItem>,
    initialIndex: Int,
    contentPadding: PaddingValues,
    onSaveScrollPosition: (Int) -> Unit,
    onItemClick: (FileItem) -> Unit,
    onTogglePin: (FileItem) -> Unit,
    onOpenWithOtherApp: (FileItem) -> Unit,
) {
    // 這個 composable 只在清單已經載入後才會出現，初始索引不會因為清單還是空的而被夾回 0。
    val listState = remember { LazyListState(firstVisibleItemIndex = initialIndex) }

    // 長按選單開在哪一列。狀態放在清單這層而不是各列裡：
    // 釘選後清單會重排，放在列裡的話選單狀態會跟著位置跑到別的項目上。
    var menuItemUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .drop(1)
            .debounce(SCROLL_SAVE_DEBOUNCE_MS)
            .collect { onSaveScrollPosition(it) }
    }
    // 離開（切換資料夾或開啟檔案）時立即寫入，否則最後一次捲動若落在 debounce 視窗內就會遺失。
    DisposableEffect(listState) {
        onDispose { onSaveScrollPosition(listState.firstVisibleItemIndex) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        // 刻意不給 key：釘選或換排序方式時畫面停在原本的捲動位置，
        // 而不是跟著被搬走的那一列一起跳走。
        items(items) { item ->
            FileItemRow(
                item = item,
                menuExpanded = menuItemUri == item.uri,
                onClick = { onItemClick(item) },
                onLongClick = { menuItemUri = item.uri },
                onDismissMenu = { menuItemUri = null },
                onTogglePin = {
                    menuItemUri = null
                    onTogglePin(item)
                },
                onOpenWithOtherApp = {
                    menuItemUri = null
                    onOpenWithOtherApp(item)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

private const val SCROLL_SAVE_DEBOUNCE_MS = 1000L

@Composable
private fun FileItemRow(
    item: FileItem,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenWithOtherApp: () -> Unit,
) {
    Box {
        ListItem(
            headlineContent = {
                Text(
                    text = item.name,
                    fontWeight = if (item.isFolder) FontWeight.SemiBold else FontWeight.Normal,
                )
            },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            leadingContent = {
                Icon(
                    imageVector = if (item.isFolder) Icons.Default.Folder else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (item.isFolder) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.main_item_pinned),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // 有閱讀紀錄的檔案才顯示進度環。
                    item.progressPercent?.let { ReadingProgress(it) }
                }
            },
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (item.isPinned) R.string.action_unpin else R.string.action_pin))
                },
                onClick = onTogglePin,
            )
            if (!item.isFolder) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.main_open_with_other_app)) },
                    onClick = onOpenWithOtherApp,
                )
            }
        }
    }
}

@Composable
private fun ReadingProgress(percent: Double) {
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

/** 交給其他 App（例如文字編輯器）開啟；沒有 App 能處理時回傳 false。 */
private fun openWithOtherApp(context: Context, item: FileItem): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri.toUri(), "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(intent, item.name))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
