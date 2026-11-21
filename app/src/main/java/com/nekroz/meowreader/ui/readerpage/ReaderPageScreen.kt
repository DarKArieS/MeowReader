package com.nekroz.meowreader.ui.readerpage

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekroz.meowreader.R
import com.nekroz.meowreader.ui.common.parseHexColor
import com.nekroz.meowreader.ui.theme.SystemBarIconsOverride
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ReaderPageScreen(
    onBack: () -> Unit,
    viewModel: ReaderPageViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setting = state.setting

    // 自訂顏色關閉或色碼寫錯時，退回主題本身的背景與文字顏色。
    val backgroundColor = parseHexColor(setting.customBackgroundColor)
        .takeIf { setting.useCustomBackgroundColor }
        ?: MaterialTheme.colorScheme.background
    val textColor = parseHexColor(setting.customForegroundColor)
        .takeIf { setting.useCustomForegroundColor }
        ?: MaterialTheme.colorScheme.onBackground

    // 內文一路延伸到狀態列底下，狀態列圖示的深淺要跟著實際的背景色走。
    SystemBarIconsOverride(darkIcons = backgroundColor.luminance() > 0.5f)

    // 閱讀頁可能就是啟動頁，底下不一定有主頁可退，所以返回一律交給 onBack 判斷。
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.loadFailed -> LoadFailedPanel(textColor = textColor, onBack = onBack)

            else -> ReaderContent(
                state = state,
                textColor = textColor,
                viewModel = viewModel,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LoadFailedPanel(textColor: Color, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.reader_load_failed),
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}

/** 長按某一行後跳出的選單：是哪一行、要開在畫面上的哪個位置。 */
private data class LineMenu(val lineIndex: Int, val offset: IntOffset)

@OptIn(FlowPreview::class)
@Composable
private fun ReaderContent(
    state: ReaderPageUiState,
    textColor: Color,
    viewModel: ReaderPageViewModel,
    onBack: () -> Unit,
) {
    val lines = state.lines
    val setting = state.setting
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

    // 位置一律以設定檔裡的紀錄為準（而不是 rememberSaveable）：存的是行索引 + 行內比例，
    // 螢幕旋轉導致重新換行之後仍然還原得到同一個地方。
    val savedPosition = remember { viewModel.getSavedPosition() }
    val listState = remember {
        LazyListState(
            firstVisibleItemIndex = (savedPosition?.lineIndex ?: 0).coerceIn(0, lines.lastIndex.coerceAtLeast(0)),
        )
    }

    /**
     * 還原閱讀位置完成前為 false。還原本身會捲動清單，
     * 若不擋掉就會把中途的位置寫回設定檔，蓋掉正確紀錄。
     */
    var restored by remember { mutableStateOf(false) }

    fun savePosition() {
        if (!restored) return
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return

        val lineIndex = listState.firstVisibleItemIndex
        // 該行已經捲出畫面頂端的比例，範圍 [0, 1)。
        val lineHeight = visibleItems.firstOrNull { it.index == lineIndex }?.size ?: 0
        val lineFraction = if (lineHeight > 0) {
            (listState.firstVisibleItemScrollOffset.toDouble() / lineHeight).coerceIn(0.0, 0.999)
        } else {
            0.0
        }
        // 已讀行數以最下方可見行為準，與標題上顯示的百分比一致。
        val lastVisibleIndex = visibleItems.lastOrNull { it.offset < layoutInfo.viewportEndOffset }?.index
            ?: lineIndex
        viewModel.saveReadingPosition(lineIndex, lineFraction, lastVisibleIndex + 1)
    }

    LaunchedEffect(listState) {
        // 行內的細部位置要等該行排版完、知道實際高度後才算得出來。
        val lineFraction = savedPosition?.lineFraction ?: 0.0
        if (lineFraction > 0.0) {
            val lineIndex = listState.firstVisibleItemIndex
            val item = withTimeoutOrNull(RESTORE_TIMEOUT_MS) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lineIndex } }
                    .filterNotNull()
                    .first()
            }
            if (item != null) listState.scrollToItem(lineIndex, (item.size * lineFraction).roundToInt())
        }
        restored = true

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .debounce(SAVE_DEBOUNCE_MS)
            .collect { savePosition() }
    }
    // 離開頁面或 App 進入背景前立即寫入，否則最後一次捲動若落在 debounce 視窗內就會遺失。
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { savePosition() }
    DisposableEffect(listState) {
        onDispose { savePosition() }
    }

    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showChapterSetting by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var lineMenu by remember { mutableStateOf<LineMenu?>(null) }
    var editingLineIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val textStyle = remember(setting.fontSize, setting.lineSpacing, textColor) {
        TextStyle(
            color = textColor,
            fontSize = setting.fontSize.sp,
            lineHeight = (setting.fontSize * setting.lineSpacing).sp,
            // 行距平均分到文字上下，第一行與最後一行也不裁掉，段落之間的間隔才會一致。
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            // 點兩下收合／展開底部面板。
                            onDoubleTap = { viewModel.toggleBottomPanel() },
                            // 長按某一行跳出該行的選單（行號、編輯、複製原文）。
                            onLongPress = { position ->
                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    position.y >= it.offset && position.y < it.offset + it.size
                                }
                                if (item != null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lineMenu = LineMenu(
                                        lineIndex = item.index,
                                        offset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                                    )
                                }
                            },
                        )
                    }
                    // 翻頁熱區要排在 detectTapGestures 後面（更內層）：這樣它先收到事件，
                    // 點擊後不必等「是不是點兩下」的判定逾時就能立刻翻頁。
                    .pointerInput(Unit) {
                        detectPageTaps { direction ->
                            scope.launch {
                                listState.animateScrollBy(
                                    direction * listState.layoutInfo.viewportSize.height * PAGE_SCROLL_FRACTION
                                )
                            }
                        }
                    },
                state = listState,
                contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues(),
            ) {
                items(count = lines.size) { index ->
                    Text(
                        text = lines[index],
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        style = textStyle,
                    )
                }
            }

            lineMenu?.let { menu ->
                // 選單錨定在長按的位置：放一個零大小的 Box 在該座標，選單就會從那裡展開。
                Box(Modifier.offset { menu.offset }) {
                    // 選單一打開就先去讀原文的那一行：這樣「複製原文」可以在原文沒有這一行時
                    // 直接顯示成不能按，而不是按了才發現沒東西可複製。
                    val rawText by produceState<String?>(initialValue = null, menu.lineIndex, state.hasRawFile) {
                        value = if (state.hasRawFile) viewModel.getRawLineText(menu.lineIndex) else null
                    }
                    DropdownMenu(expanded = true, onDismissRequest = { lineMenu = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_line_number, menu.lineIndex + 1)) },
                            onClick = {},
                            enabled = false,
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                editingLineIndex = menu.lineIndex
                                lineMenu = null
                            },
                        )
                        // 有 raw 資料夾內的原文檔案才出現這一項。
                        if (state.hasRawFile) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reader_copy_raw)) },
                                onClick = {
                                    val text = rawText
                                    if (text != null) {
                                        scope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText(state.fileName, text))
                                            )
                                        }
                                    }
                                    lineMenu = null
                                },
                                enabled = rawText != null,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = setting.showBottomPanel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ReaderBottomPanel(
                fileName = state.fileName,
                lineCount = lines.size,
                isPinned = state.isPinned,
                listState = listState,
                onBack = onBack,
                onShowChapters = { showChapters = true },
                onShowSettings = { showSettings = true },
                onTogglePin = viewModel::togglePin,
            )
        }
    }

    if (showChapters) {
        LaunchedEffect(Unit) { viewModel.ensureChaptersLoaded() }
        ChapterChooseSheet(
            chapters = state.chapters,
            isLoading = state.isLoadingChapters || !state.chaptersLoaded,
            // 以打開清單當下讀到的那一行判斷目前章節。
            currentLineIndex = remember { listState.firstVisibleItemIndex },
            onChapterClick = { chapter ->
                showChapters = false
                scope.launch {
                    listState.scrollToItem(chapter.lineIndex.coerceIn(0, lines.lastIndex.coerceAtLeast(0)))
                    savePosition()
                }
            },
            onRefresh = viewModel::refreshChapters,
            onOpenSetting = { showChapterSetting = true },
            onDismiss = { showChapters = false },
        )
    }

    if (showChapterSetting) {
        ChapterSettingDialog(
            initialUseDefault = remember { viewModel.usesDefaultChapterSetting() },
            initialSetting = remember { viewModel.getStoredChapterSetting() },
            onUseDefault = {
                viewModel.useDefaultChapterSetting()
                showChapterSetting = false
            },
            onSave = { chapterSetting ->
                viewModel.saveChapterSetting(chapterSetting)
                showChapterSetting = false
            },
            onDismiss = { showChapterSetting = false },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            setting = setting,
            onFontSizeChange = viewModel::changeFontSize,
            onLineSpacingChange = viewModel::changeLineSpacing,
            onUseCustomColorsChange = viewModel::setUseCustomColors,
            onBackgroundColorChange = viewModel::setCustomBackgroundColor,
            onTextColorChange = viewModel::setCustomForegroundColor,
            onDismiss = { showSettings = false },
        )
    }

    editingLineIndex?.let { lineIndex ->
        LineEditDialog(
            lineNumber = lineIndex + 1,
            initialText = remember(lineIndex) { lines.getOrNull(lineIndex).orEmpty() },
            hasRawFile = state.hasRawFile,
            loadRawText = { viewModel.getRawLineText(lineIndex) },
            // 存檔成功後 ViewModel 會重讀整個檔案；清單以行索引定位，捲動位置不受影響。
            onSave = { newText -> viewModel.updateLineText(lineIndex, newText) },
            onCountMatches = { target -> viewModel.countOccurrences(target) },
            onReplaceAll = { target, replacement -> viewModel.replaceAll(target, replacement) },
            onDismiss = { editingLineIndex = null },
        )
    }
}

/**
 * 底部面板：返回、標題與閱讀進度、章節、設定、釘選，以及可以拖曳跳轉的進度條。
 */
@Composable
private fun ReaderBottomPanel(
    fileName: String,
    lineCount: Int,
    isPinned: Boolean,
    listState: LazyListState,
    onBack: () -> Unit,
    onShowChapters: () -> Unit,
    onShowSettings: () -> Unit,
    onTogglePin: () -> Unit,
) {
    // 以「已讀到第幾行」計算，捲到底時剛好是 100%。
    val progressText by remember(lineCount) {
        derivedStateOf {
            if (lineCount == 0) return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo
                .lastOrNull { it.offset < layoutInfo.viewportEndOffset }?.index ?: 0
            val percent = ((lastVisibleIndex + 1).toDouble() / lineCount * 100.0).coerceIn(0.0, 100.0)
            String.format(Locale.ROOT, "%.2f", percent)
        }
    }
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 拖曳進度條期間以手指的位置為準，放開後才回到跟著清單走。
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val sliderDescription = stringResource(R.string.reader_progress_slider)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = progressText?.let { stringResource(R.string.reader_title_with_progress, fileName, it) }
                        ?: fileName,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onShowChapters) {
                    Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = stringResource(R.string.reader_chapters))
                }
                IconButton(onClick = onShowSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.reader_settings))
                }
                IconToggleButton(checked = isPinned, onCheckedChange = { onTogglePin() }) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(
                            if (isPinned) R.string.action_unpin else R.string.action_pin
                        ),
                    )
                }
            }
            Slider(
                value = draggingValue ?: firstVisibleIndex.toFloat(),
                onValueChange = { value ->
                    draggingValue = value
                    if (lineCount > 0) {
                        listState.requestScrollToItem(value.roundToInt().coerceIn(0, lineCount - 1))
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = sliderDescription },
                valueRange = 0f..(lineCount - 1).coerceAtLeast(1).toFloat(),
                onValueChangeFinished = { draggingValue = null },
            )
        }
    }
}

/**
 * 畫面左下角／右下角的翻頁熱區：點左下往回捲、點右下往下捲。
 * [onPage] 的參數是方向，-1 往回、+1 往下。
 *
 * 熱區只認「在同一區按下又放開」的點擊；手指一拖動（捲動清單）就取消，
 * 按太久也不算，留給長按選單處理。
 */
private suspend fun PointerInputScope.detectPageTaps(onPage: (direction: Int) -> Unit) {
    val zoneWidth = PAGE_ZONE_WIDTH.toPx()
    val zoneHeight = PAGE_ZONE_HEIGHT.toPx()

    fun zoneAt(position: Offset): Int = when {
        position.y < size.height - zoneHeight -> 0
        position.x < zoneWidth -> -1
        position.x > size.width - zoneWidth -> 1
        else -> 0
    }

    awaitEachGesture {
        val down = awaitFirstDown()
        val zone = zoneAt(down.position)
        if (zone == 0) return@awaitEachGesture

        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            ?: return@awaitEachGesture
        if (zoneAt(up.position) == zone) {
            // 吃掉這次放開，外層的點擊偵測就不會把連續翻頁誤判成點兩下。
            up.consume()
            onPage(zone)
        }
    }
}

private val PAGE_ZONE_WIDTH = 72.dp
private val PAGE_ZONE_HEIGHT = 220.dp

/** 每次翻頁捲動的距離佔畫面高度的比例，留一點重疊讓視線接得上。 */
private const val PAGE_SCROLL_FRACTION = 0.8f

private const val SAVE_DEBOUNCE_MS = 1000L
private const val RESTORE_TIMEOUT_MS = 1000L
