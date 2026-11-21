package com.nekroz.meowreader.ui.readerpage

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekroz.meowreader.MeowReaderApp
import com.nekroz.meowreader.R
import com.nekroz.meowreader.repo.LineOutOfRangeException
import com.nekroz.meowreader.repo.MainRepo
import com.nekroz.meowreader.repo.TextFileCodec
import com.nekroz.meowreader.repo.UndecodableFileException
import com.nekroz.meowreader.repo.chapter.ChapterItem
import com.nekroz.meowreader.repo.chapter.ChapterParser
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.repo.model.AppConfig
import com.nekroz.meowreader.repo.model.AppPage
import com.nekroz.meowreader.repo.model.HistoryItem
import com.nekroz.meowreader.repo.model.ReaderSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderPageUiState(
    /** 不含副檔名的檔名，同時也是閱讀紀錄的 key。 */
    val fileName: String = "",
    /** 檔案的每一行。行索引就是清單索引。 */
    val lines: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** 目前檔案是否被釘選。MainPage 會據此把它排到清單最上方。 */
    val isPinned: Boolean = false,
    val setting: ReaderSetting = ReaderSetting(),
    /** 是否有對應的原文檔案（同資料夾 raw 子資料夾內的同名檔案）。 */
    val hasRawFile: Boolean = false,
    /** 目前檔案掃描出來的章節。第一次打開章節清單時才載入。 */
    val chapters: List<ChapterItem> = emptyList(),
    val chaptersLoaded: Boolean = false,
    val isLoadingChapters: Boolean = false,
)

/** 全文取代的結果。 */
sealed interface ReplaceAllResult {
    /** 寫回成功；[count] 為 0 表示全文找不到，檔案沒有動。 */
    data class Replaced(val count: Int) : ReplaceAllResult

    data class Failed(val message: String) : ReplaceAllResult
}

class ReaderPageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeowReaderApp
    private val repo = app.mainRepo
    private val documentRepo = app.documentRepo

    private val openFile = repo.current.openFile
    private val fileUri: Uri? = openFile?.uri?.toUri()

    /** 不含副檔名的檔名，閱讀紀錄以它為 key。 */
    private val fileName: String = openFile?.name?.substringBeforeLast('.').orEmpty()

    /** 跟檔案內容有關、不存在設定檔裡的狀態。 */
    private data class Content(
        val lines: List<String> = emptyList(),
        val isLoading: Boolean = true,
        val loadFailed: Boolean = false,
        val rawFileUri: Uri? = null,
        val chapters: List<ChapterItem> = emptyList(),
        val chaptersLoaded: Boolean = false,
        val isLoadingChapters: Boolean = false,
    )

    private val content = MutableStateFlow(Content())

    /** 原文檔案的內容。第一次用到才讀，避免沒用到就多讀一個檔案。 */
    private var rawLines: List<String>? = null

    val uiState: StateFlow<ReaderPageUiState> =
        combine(repo.config, content, ::buildUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                buildUiState(repo.current, content.value),
            )

    init {
        repo.setLastPage(AppPage.ReaderPage)
        viewModelScope.launch {
            val lines = loadLines()
            val rawFileUri = if (lines != null && openFile != null) {
                documentRepo.findRawFile(openFile.folderUri.toUri(), openFile.name)
            } else {
                null
            }
            content.update {
                it.copy(
                    lines = lines.orEmpty(),
                    isLoading = false,
                    loadFailed = lines == null,
                    rawFileUri = rawFileUri,
                )
            }
        }
    }

    private fun buildUiState(config: AppConfig, content: Content) = ReaderPageUiState(
        fileName = fileName,
        lines = content.lines,
        isLoading = content.isLoading,
        loadFailed = content.loadFailed,
        isPinned = config.history.firstOrNull { it.fileName == fileName }?.isPinned == true,
        setting = config.readerSetting,
        hasRawFile = content.rawFileUri != null,
        chapters = content.chapters,
        chaptersLoaded = content.chaptersLoaded,
        isLoadingChapters = content.isLoadingChapters,
    )

    /** 讀取檔案內容；讀不到時回傳 null。 */
    private suspend fun loadLines(): List<String>? {
        val uri = fileUri ?: return null
        return try {
            documentRepo.readLines(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "檔案讀取失敗：$uri", e)
            null
        }
    }

    /**
     * 重新讀取目前的 txt 檔案內容。讀取失敗時保留畫面上原本的內容。
     */
    private suspend fun reloadLines() {
        val lines = loadLines() ?: return
        content.update { it.copy(lines = lines) }
    }

    // region 閱讀位置 / 釘選 / 面板

    /** 上次的閱讀位置；沒有紀錄時為 null。 */
    fun getSavedPosition(): HistoryItem? = repo.getHistoryItem(fileName)

    /** 以行索引 + 行內比例記錄閱讀位置，同時記下已讀行數供 MainPage 顯示進度。 */
    fun saveReadingPosition(lineIndex: Int, lineFraction: Double, readLines: Int) {
        val totalLines = content.value.lines.size
        if (fileName.isEmpty() || totalLines == 0) return
        repo.updateHistory(fileName, lineIndex, lineFraction, readLines, totalLines)
    }

    fun togglePin() {
        if (fileName.isEmpty()) return
        repo.setFilePinned(fileName, !repo.isFilePinned(fileName))
    }

    /** 收合/展開是使用者的偏好，記到設定檔，下次進 ReaderPage 沿用。 */
    fun toggleBottomPanel() {
        repo.setShowBottomPanel(!repo.current.readerSetting.showBottomPanel)
    }

    // endregion

    // region 閱讀設定

    fun changeFontSize(delta: Double) = repo.setFontSize(repo.current.readerSetting.fontSize + delta)

    fun changeLineSpacing(steps: Int) =
        repo.setLineSpacing(repo.current.readerSetting.lineSpacing + steps * ReaderSetting.LINE_SPACING_STEP)

    fun setUseCustomColors(useCustom: Boolean) = repo.setUseCustomColors(useCustom)

    fun setCustomBackgroundColor(color: String) = repo.setCustomBackgroundColor(color)

    fun setCustomForegroundColor(color: String) = repo.setCustomForegroundColor(color)

    // endregion

    // region 章節

    /**
     * 第一次打開章節清單時才掃描。開檔時就掃會讓大檔案的開啟變慢，
     * 而多數時候使用者根本不會打開章節清單。
     */
    fun ensureChaptersLoaded() {
        val current = content.value
        if (current.chaptersLoaded || current.isLoadingChapters) return
        viewModelScope.launch { loadChapters(forceRefresh = false) }
    }

    /**
     * 重新整理：連同目前的 txt 檔案內容一併重讀，避免檔案已變更卻只是重新比對記憶體裡的舊行。
     */
    fun refreshChapters() {
        viewModelScope.launch {
            content.update { it.copy(isLoadingChapters = true) }
            reloadLines()
            loadChapters(forceRefresh = true)
        }
    }

    /**
     * 載入章節。有沿用得上的快取就直接用，否則重新掃描並寫回閱讀紀錄，
     * 下次開同一個檔案就不必再掃。
     */
    private suspend fun loadChapters(forceRefresh: Boolean) {
        if (fileName.isEmpty()) {
            content.update { it.copy(chapters = emptyList(), chaptersLoaded = true, isLoadingChapters = false) }
            return
        }
        content.update { it.copy(isLoadingChapters = true) }

        val lines = content.value.lines
        val setting = repo.getChapterSetting(fileName)
        val patterns = setting.effectiveRegexList()
        val titleMaxLength = setting.effectiveTitleMaxLength()
        val skipLines = setting.effectiveSkipLines()
        val cacheKey = MainRepo.buildChapterCacheKey(patterns, titleMaxLength, skipLines, lines.size)

        val history = repo.getHistoryItem(fileName)
        val cached = history?.chapters?.takeIf { !forceRefresh && history.chapterCacheKey == cacheKey }
        // 使用者可以填任意 Regex，大檔案掃起來可能要一點時間，不能卡住主執行緒。
        val chapters = cached ?: withContext(Dispatchers.Default) {
            ChapterParser.parse(lines, patterns, titleMaxLength, skipLines)
        }.also { repo.updateChapters(fileName, it, cacheKey) }

        content.update { it.copy(chapters = chapters, chaptersLoaded = true, isLoadingChapters = false) }
    }

    /** 這個檔案目前是否使用全域預設的章節抓取設定。 */
    fun usesDefaultChapterSetting(): Boolean = repo.getHistoryItem(fileName)?.useDefaultChapterSetting ?: true

    /** 章節設定對話框的初始內容：這個檔案已經存起來的自訂設定，不管開關狀態。 */
    fun getStoredChapterSetting(): ChapterRegexSetting = repo.getStoredChapterSetting(fileName)

    /** 改回使用全域預設的章節抓取設定，並重新抓取章節。 */
    fun useDefaultChapterSetting() {
        // 只切換開關，不動底下已經存的自訂設定，之後取消勾選還能拿回原本填的值。
        repo.setChapterSettingUseDefault(fileName, true)
        viewModelScope.launch { loadChapters(forceRefresh = true) }
    }

    /** 儲存這個檔案專屬的章節抓取設定，並重新抓取章節。 */
    fun saveChapterSetting(setting: ChapterRegexSetting) {
        repo.setChapterSetting(fileName, setting)
        viewModelScope.launch { loadChapters(forceRefresh = true) }
    }

    // endregion

    // region 單行編輯

    /**
     * 原文檔案中對應的那一行（0-based）。原文與譯文行號一一對應，所以直接用同一個索引取。
     * 沒有原文檔案、讀取失敗或原文行數不足時回傳 null。
     */
    suspend fun getRawLineText(lineIndex: Int): String? {
        val rawFileUri = content.value.rawFileUri ?: return null
        val lines = rawLines ?: readRawLines(rawFileUri).also { rawLines = it }
        return lines.getOrNull(lineIndex)
    }

    private suspend fun readRawLines(rawFileUri: Uri): List<String> = try {
        documentRepo.readLines(rawFileUri)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "原文檔案讀取失敗：$rawFileUri", e)
        emptyList()
    }

    /**
     * 把檔案中指定的一行換成新內容並寫回。成功回傳 null 並重讀整個檔案讓畫面跟檔案一致，
     * 失敗則回傳要顯示給使用者的錯誤訊息。
     */
    suspend fun updateLineText(lineIndex: Int, newText: String): String? {
        val uri = fileUri ?: return app.getString(R.string.line_edit_error_file_not_found)
        try {
            documentRepo.updateLine(uri, lineIndex, newText)
        } catch (e: CancellationException) {
            throw e
        } catch (_: UndecodableFileException) {
            return app.getString(R.string.line_edit_error_not_utf8)
        } catch (_: LineOutOfRangeException) {
            return app.getString(R.string.line_edit_error_out_of_range)
        } catch (e: Exception) {
            Log.w(TAG, "行編輯寫入失敗：$uri", e)
            return app.getString(R.string.line_edit_error_write_failed, e.message.orEmpty())
        }
        reloadLines()
        return null
    }

    /**
     * 全文有幾處 [target]。直接數畫面上已讀進來的內容（每次寫檔後都會重讀），不另外讀檔；
     * [target] 不含換行，所以逐行數的結果跟 [replaceAll] 實際會換掉的處數一致。
     */
    suspend fun countOccurrences(target: String): Int {
        val lines = content.value.lines
        return withContext(Dispatchers.Default) {
            lines.sumOf { TextFileCodec.countOccurrences(it, target) }
        }
    }

    /**
     * 把整個檔案裡所有的 [target] 換成 [replacement] 並寫回。有取代到東西就重讀檔案讓畫面跟檔案一致。
     */
    suspend fun replaceAll(target: String, replacement: String): ReplaceAllResult {
        val uri = fileUri ?: return ReplaceAllResult.Failed(app.getString(R.string.line_edit_error_file_not_found))
        val count = try {
            documentRepo.replaceAll(uri, target, replacement)
        } catch (e: CancellationException) {
            throw e
        } catch (_: UndecodableFileException) {
            return ReplaceAllResult.Failed(app.getString(R.string.line_edit_error_not_utf8))
        } catch (e: Exception) {
            Log.w(TAG, "全文取代寫入失敗：$uri", e)
            return ReplaceAllResult.Failed(app.getString(R.string.line_edit_error_write_failed, e.message.orEmpty()))
        }
        if (count > 0) reloadLines()
        return ReplaceAllResult.Replaced(count)
    }

    // endregion

    private companion object {
        const val TAG = "ReaderPageViewModel"
    }
}
