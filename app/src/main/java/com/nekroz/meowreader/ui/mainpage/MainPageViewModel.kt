package com.nekroz.meowreader.ui.mainpage

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekroz.meowreader.MeowReaderApp
import com.nekroz.meowreader.repo.DocumentEntry
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.repo.model.AppConfig
import com.nekroz.meowreader.repo.model.AppPage
import com.nekroz.meowreader.repo.model.AppTheme
import com.nekroz.meowreader.repo.model.FolderRef
import com.nekroz.meowreader.repo.model.OpenFile
import com.nekroz.meowreader.util.NaturalStringComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 清單上的一列：子資料夾或 txt 檔。 */
data class FileItem(
    val name: String,
    val isFolder: Boolean,
    /** document URI。 */
    val uri: String,
    /** 依修改日期排序用（epoch 毫秒）。 */
    val lastModified: Long,
    /** 釘選的項目會排到清單最上方。 */
    val isPinned: Boolean,
    /** 閱讀進度百分比（0~100）；沒有閱讀紀錄時為 null，不顯示進度環。 */
    val progressPercent: Double?,
) {
    /** 閱讀紀錄是以不含副檔名的檔名為 key（見 ReaderPageViewModel）。 */
    val historyKey: String get() = name.substringBeforeLast('.')
}

enum class FileSortMode {
    Name,
    ModifiedDate,
}

data class MainPageUiState(
    /** 是否已經選過資料夾。還沒選時畫面只顯示「選擇資料夾」的引導。 */
    val hasFolder: Boolean = false,
    val folderUri: String? = null,
    val folderName: String = "",
    /** 從根資料夾到目前資料夾的路徑，用來取代桌面版的完整路徑顯示。 */
    val breadcrumb: String = "",
    /** 目前不在根資料夾，可以返回上一層。 */
    val canGoBack: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    /** 資料夾列不出來，多半是存取權限失效或資料夾被刪掉了。 */
    val loadFailed: Boolean = false,
    val sortMode: FileSortMode = FileSortMode.Name,
    val theme: AppTheme = AppTheme.Default,
)

class MainPageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MeowReaderApp
    private val repo = app.mainRepo
    private val documentRepo = app.documentRepo

    /** 某個資料夾的列舉結果。entries 為 null 代表列舉失敗。 */
    private data class Listing(val folderUri: String, val entries: List<DocumentEntry>?)

    private val listing = MutableStateFlow<Listing?>(null)

    /** 排序方式只影響當下這次瀏覽，刻意不寫進設定檔，每次開啟都回到預設的「依名稱」。 */
    private val sortMode = MutableStateFlow(FileSortMode.Name)

    val uiState: StateFlow<MainPageUiState> =
        combine(repo.config, listing, sortMode, ::buildUiState)
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                buildUiState(repo.current, listing.value, sortMode.value),
            )

    /** 啟動時設定檔損毀、已改用預設設定的一次性提示。 */
    private val _showConfigCorruptNotice = MutableStateFlow(repo.configLoadFailed)
    val showConfigCorruptNotice: StateFlow<Boolean> = _showConfigCorruptNotice

    /** 損毀的設定檔被另存成的檔名，顯示在提示訊息裡。 */
    val corruptBackupFileName: String get() = repo.corruptBackupFile.name

    init {
        viewModelScope.launch {
            repo.config
                .map { it.currentFolder?.uri }
                .distinctUntilChanged()
                .collectLatest { folderUri ->
                    if (folderUri == null) listing.value = null else loadFolder(folderUri)
                }
        }
    }

    /**
     * 頁面回到前景時呼叫（包含從 ReaderPage 返回）。資料夾內容可能在離開期間被改過，
     * 重新列舉一次；閱讀進度與釘選狀態則是直接跟著設定檔更新，不必另外處理。
     */
    fun onShown() {
        repo.setLastPage(AppPage.MainPage)
        refresh()
    }

    fun refresh() {
        val folderUri = repo.current.currentFolder?.uri ?: return
        viewModelScope.launch { loadFolder(folderUri) }
    }

    private suspend fun loadFolder(folderUri: String) {
        val entries = try {
            documentRepo.listFolder(folderUri.toUri())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "資料夾列舉失敗：$folderUri", e)
            null
        }
        // 列舉期間使用者可能已經切到別的資料夾，過期的結果不能蓋掉目前的清單。
        if (repo.current.currentFolder?.uri == folderUri) {
            listing.value = Listing(folderUri, entries)
        }
    }

    /** 使用者從系統的資料夾選擇器選了一個資料夾。 */
    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            val previousRoot = repo.current.rootFolder
            val root = try {
                documentRepo.adoptTree(treeUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "無法取得資料夾權限：$treeUri", e)
                return@launch
            }
            if (previousRoot != null && previousRoot.uri != root.uri) {
                documentRepo.releaseTree(previousRoot.uri.toUri())
            }
            repo.setRootFolder(root)
        }
    }

    fun enterFolder(item: FileItem) {
        if (!item.isFolder) return
        repo.enterFolder(FolderRef(uri = item.uri, name = item.name))
    }

    /** 記下要開啟的檔案；跳轉到 ReaderPage 由畫面層負責。 */
    fun openFile(item: FileItem) {
        val config = repo.current
        val folder = config.currentFolder ?: return
        if (item.isFolder) return
        repo.setOpenFile(
            OpenFile(uri = item.uri, name = item.name, folderUri = folder.uri, folderPath = config.folderPath)
        )
    }

    fun backToParent() {
        repo.backToParent()
    }

    /** 釘選／取消釘選。狀態寫回設定檔後清單會自動重排，釘選的項目立刻跳到最上方。 */
    fun togglePin(item: FileItem) {
        if (item.isFolder) repo.setFolderPinned(item.uri, !item.isPinned)
        else repo.setFilePinned(item.historyKey, !item.isPinned)
    }

    fun setSortMode(mode: FileSortMode) {
        sortMode.value = mode
    }

    fun saveScrollPosition(folderUri: String, itemIndex: Int) {
        repo.updateFolderScrollPosition(folderUri, itemIndex)
    }

    fun getSavedScrollIndex(folderUri: String): Int = repo.getFolderScrollPosition(folderUri)?.itemIndex ?: 0

    fun setTheme(theme: AppTheme) = repo.setTheme(theme)

    val globalChapterSetting: ChapterRegexSetting get() = repo.globalChapterSetting

    fun saveGlobalChapterSetting(setting: ChapterRegexSetting) = repo.setGlobalChapterSetting(setting)

    /** 設定檔目前的 JSON 內容，給 RawConfig 畫面檢視。 */
    fun configJson(): String = repo.configJson()

    fun dismissConfigCorruptNotice() {
        _showConfigCorruptNotice.value = false
    }

    private fun buildUiState(config: AppConfig, listing: Listing?, sortMode: FileSortMode): MainPageUiState {
        val folder = config.currentFolder
            ?: return MainPageUiState(sortMode = sortMode, theme = config.theme)

        // 剛切換資料夾時 listing 還是上一個資料夾的內容，要等新的列舉結果回來。
        val current = listing?.takeIf { it.folderUri == folder.uri }
        return MainPageUiState(
            hasFolder = true,
            folderUri = folder.uri,
            folderName = folder.name,
            breadcrumb = config.folderPath,
            canGoBack = config.folderStack.size > 1,
            items = current?.entries?.let { buildItems(it, config, sortMode) }.orEmpty(),
            isLoading = current == null,
            loadFailed = current != null && current.entries == null,
            sortMode = sortMode,
            theme = config.theme,
        )
    }

    private fun buildItems(entries: List<DocumentEntry>, config: AppConfig, sortMode: FileSortMode): List<FileItem> {
        val historyByName = config.history.associateBy { it.fileName }
        val pinnedFolders = config.pinnedFolders.toHashSet()

        val items = entries.map { entry ->
            val uri = entry.uri.toString()
            val history = if (entry.isFolder) null else historyByName[entry.name.substringBeforeLast('.')]
            FileItem(
                name = entry.name,
                isFolder = entry.isFolder,
                uri = uri,
                lastModified = entry.lastModified,
                isPinned = if (entry.isFolder) uri in pinnedFolders else history?.isPinned == true,
                progressPercent = history?.progressPercent,
            )
        }

        // 釘選的項目一律置頂（資料夾與檔案都算），其餘則不論哪一種排序方式，資料夾都排在檔案之前。
        val pinnedFirst = compareByDescending<FileItem> { it.isPinned }.thenByDescending { it.isFolder }
        val comparator = when (sortMode) {
            FileSortMode.Name -> pinnedFirst.thenBy(NaturalStringComparator) { it.name }
            // 新的排前面：會動的檔案通常才是要看的那個。同時間再用檔名決勝，順序才不會浮動。
            FileSortMode.ModifiedDate -> pinnedFirst
                .thenByDescending { it.lastModified }
                .thenBy(NaturalStringComparator) { it.name }
        }
        return items.sortedWith(comparator)
    }

    private companion object {
        const val TAG = "MainPageViewModel"
    }
}
