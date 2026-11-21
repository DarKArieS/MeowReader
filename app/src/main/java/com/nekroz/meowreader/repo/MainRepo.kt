package com.nekroz.meowreader.repo

import android.util.Log
import com.nekroz.meowreader.repo.chapter.ChapterItem
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.repo.model.AppConfig
import com.nekroz.meowreader.repo.model.AppPage
import com.nekroz.meowreader.repo.model.AppTheme
import com.nekroz.meowreader.repo.model.FolderRef
import com.nekroz.meowreader.repo.model.FolderScrollHistoryItem
import com.nekroz.meowreader.repo.model.HistoryItem
import com.nekroz.meowreader.repo.model.OpenFile
import com.nekroz.meowreader.repo.model.ReaderSetting
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 設定檔（appConfig.json）的唯一擁有者。所有設定、閱讀紀錄、章節快取都從這裡讀寫。
 *
 * 記憶體中的設定是一份不可變的 [AppConfig]，以 [config] 這個 StateFlow 對外公開，
 * ViewModel 直接訂閱就能在設定變更時更新畫面。寫檔則交給背景 writer 合併處理。
 */
class MainRepo(private val saveFile: File) {

    private val json = Json {
        // 讓設定檔縮排、保留真實字元，方便在 RawConfig 畫面檢視。
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        // 新舊版本欄位不一致時不要整份讀取失敗。
        ignoreUnknownKeys = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 只保護「實際落地」這個動作，讓背景 writer 與 [flush] 不會同時寫同一個檔。 */
    private val fileLock = Any()

    /** CONFLATED：writer 忙的時候進來再多次變更，也只會多寫一次。 */
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    /** 已經寫進檔案的那一份設定。設定是不可變物件，比對參考就知道有沒有待寫的變更。 */
    @Volatile
    private var lastWritten: AppConfig? = null

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    /** 目前的設定快照。 */
    val current: AppConfig get() = _config.value

    /**
     * 啟動時設定檔讀取失敗（損毀）。壞掉的檔案已另存成 [corruptBackupFile]，
     * 目前使用的是全新的預設設定；畫面層據此提示使用者一次。
     */
    var configLoadFailed: Boolean = false
        private set

    val corruptBackupFile: File get() = File(saveFile.parentFile, CORRUPT_BACKUP_NAME)

    init {
        loadConfig()
        scope.launch {
            for (signal in saveSignal) {
                // 合併視窗：UI 層（捲動位置）已各有 1 秒 debounce，這裡再壓一層，
                // 把「開檔時連寫兩次」「顏色輸入框每個按鍵一次」這類連發合併成單次寫入。
                delay(COALESCE_DELAY_MS)
                writeIfDirty()
            }
        }
    }

    // region 資料夾 / 開啟中的檔案

    /** 使用者重新選了一個根資料夾。 */
    fun setRootFolder(root: FolderRef) = update { it.copy(folderStack = listOf(root)) }

    fun enterFolder(folder: FolderRef) = update { it.copy(folderStack = it.folderStack + folder) }

    /** 回到上一層資料夾。已經在根資料夾時不動作並回傳 false。 */
    fun backToParent(): Boolean {
        if (current.folderStack.size <= 1) return false
        update { if (it.folderStack.size > 1) it.copy(folderStack = it.folderStack.dropLast(1)) else it }
        return true
    }

    fun setOpenFile(file: OpenFile) = update { it.copy(openFile = file) }

    fun setLastPage(page: AppPage) = update { it.copy(lastPage = page) }

    // endregion

    // region 閱讀設定 / 主題

    fun setFontSize(value: Double) = updateReaderSetting {
        it.copy(fontSize = value.coerceIn(ReaderSetting.MIN_FONT_SIZE, ReaderSetting.MAX_FONT_SIZE))
    }

    fun setLineSpacing(value: Double) = updateReaderSetting {
        val clamped = value.coerceIn(ReaderSetting.MIN_LINE_SPACING, ReaderSetting.MAX_LINE_SPACING)
        // 以 0.1 為單位，避免浮點數加減累積出 1.6000000000000003 這種值。
        it.copy(lineSpacing = (clamped * 10).roundToInt() / 10.0)
    }

    /** ReaderPage 底部面板的顯示狀態，離開頁面後仍保留，下次進來沿用。 */
    fun setShowBottomPanel(show: Boolean) = updateReaderSetting { it.copy(showBottomPanel = show) }

    /** 背景與文字顏色共用一個「系統預設 / 自訂」開關。關掉時保留已填的色碼，之後還能切回來。 */
    fun setUseCustomColors(useCustom: Boolean) = updateReaderSetting {
        it.copy(useCustomBackgroundColor = useCustom, useCustomForegroundColor = useCustom)
    }

    fun setCustomBackgroundColor(color: String?) = updateReaderSetting { it.copy(customBackgroundColor = color) }

    fun setCustomForegroundColor(color: String?) = updateReaderSetting { it.copy(customForegroundColor = color) }

    fun setTheme(theme: AppTheme) = update { it.copy(theme = theme) }

    private inline fun updateReaderSetting(crossinline transform: (ReaderSetting) -> ReaderSetting) =
        update { it.copy(readerSetting = transform(it.readerSetting)) }

    // endregion

    // region 章節

    /** 全域章節抓取設定。單一檔案沒有專屬設定時，以此為預設值。 */
    val globalChapterSetting: ChapterRegexSetting
        get() = current.chapterSetting ?: ChapterRegexSetting()

    fun setGlobalChapterSetting(setting: ChapterRegexSetting) = update { it.copy(chapterSetting = setting) }

    /**
     * 取得指定檔案生效中的章節抓取設定：檔案關掉「使用預設值」且填過自己的設定時用它，
     * 否則退回全域預設值。
     */
    fun getChapterSetting(fileName: String): ChapterRegexSetting {
        val history = getHistoryItem(fileName)
        if (history != null && !history.useDefaultChapterSetting && history.chapterSetting != null) {
            return history.chapterSetting
        }
        return globalChapterSetting
    }

    /**
     * 取得這個檔案「已經存起來」的章節抓取設定，不管「使用預設值」開關的狀態。
     * 給章節設定對話框初始化編輯畫面用：即使目前開著「使用預設值」，
     * 使用者之前填過的自訂設定也要顯示出來，不能被開關狀態蓋掉。
     */
    fun getStoredChapterSetting(fileName: String): ChapterRegexSetting =
        getHistoryItem(fileName)?.chapterSetting ?: globalChapterSetting

    /** 寫入指定檔案專屬的章節抓取設定，並關閉這個檔案的「使用預設值」開關。 */
    fun setChapterSetting(fileName: String, setting: ChapterRegexSetting) {
        if (fileName.isEmpty()) return
        updateHistoryItem(fileName) { it.copy(chapterSetting = setting, useDefaultChapterSetting = false) }
    }

    /**
     * 切換指定檔案的「使用預設值」開關，不動到底下已經存的自訂設定資料，
     * 之後關掉開關還能拿回原本填的值。
     */
    fun setChapterSettingUseDefault(fileName: String, useDefault: Boolean) {
        if (fileName.isEmpty()) return
        // 本來就沒紀錄等於已經在使用預設值，不必為了它新增一筆空的 HistoryItem。
        updateHistoryItem(fileName, createIfMissing = !useDefault) {
            it.copy(useDefaultChapterSetting = useDefault)
        }
    }

    /** 記下掃描出來的章節，下次開同一個檔案直接取用。 */
    fun updateChapters(fileName: String, chapters: List<ChapterItem>, cacheKey: String) {
        if (fileName.isEmpty()) return
        updateHistoryItem(fileName) { it.copy(chapters = chapters, chapterCacheKey = cacheKey) }
    }

    // endregion

    // region 釘選

    fun isFolderPinned(folderUri: String): Boolean = folderUri in current.pinnedFolders

    /** 釘選／取消釘選一個資料夾。資料夾以 document URI 辨識。 */
    fun setFolderPinned(folderUri: String, pinned: Boolean) {
        if (folderUri.isEmpty()) return
        update { config ->
            val alreadyPinned = folderUri in config.pinnedFolders
            when {
                pinned && !alreadyPinned -> config.copy(pinnedFolders = config.pinnedFolders + folderUri)
                !pinned && alreadyPinned -> config.copy(pinnedFolders = config.pinnedFolders - folderUri)
                else -> config
            }
        }
    }

    /** 檔案是否被釘選。fileName 與閱讀紀錄同樣是「不含副檔名的檔名」。 */
    fun isFilePinned(fileName: String): Boolean = getHistoryItem(fileName)?.isPinned == true

    /**
     * 釘選／取消釘選一個檔案。還沒有閱讀紀錄的檔案會為它補一筆，
     * 取消釘選則不刪紀錄：裡面可能還有閱讀進度與章節快取。
     */
    fun setFilePinned(fileName: String, pinned: Boolean) {
        if (fileName.isEmpty()) return
        updateHistoryItem(fileName, createIfMissing = pinned) { it.copy(isPinned = pinned) }
    }

    // endregion

    // region 閱讀紀錄 / 資料夾瀏覽位置

    fun getHistoryItem(fileName: String): HistoryItem? = current.history.firstOrNull { it.fileName == fileName }

    /**
     * 記錄閱讀位置。以行索引為錨點，不儲存像素偏移量。
     * readLines/totalLines 只用來算閱讀進度，不影響位置還原。
     */
    fun updateHistory(fileName: String, lineIndex: Int, lineFraction: Double, readLines: Int, totalLines: Int) {
        if (fileName.isEmpty()) return
        updateHistoryItem(fileName) { item ->
            val unchanged = item.lineIndex == lineIndex
                    && abs(item.lineFraction - lineFraction) < 0.01
                    && item.readLines == readLines
                    && item.totalLines == totalLines
            // 位置沒有實質變化，不需要重寫設定檔。
            if (unchanged) item
            else item.copy(
                lineIndex = lineIndex,
                lineFraction = lineFraction,
                readLines = readLines,
                totalLines = totalLines,
            )
        }
    }

    /** 記下檔案在 ReaderPage 被開啟的位置與時間，閱讀歷史依此列出並重新開啟檔案。 */
    fun recordFileOpened(fileName: String, file: OpenFile, openedAt: Long = System.currentTimeMillis()) {
        if (fileName.isEmpty()) return
        updateHistoryItem(fileName) { it.copy(lastFile = file, lastOpenedAt = openedAt) }
    }

    /**
     * 從閱讀歷史移除指定的檔案：只清掉開啟位置與時間，閱讀進度、釘選與章節資料都保留，
     * 之後再開這個檔案就會接著上次的進度，並重新出現在閱讀歷史裡。
     */
    fun clearOpenRecords(fileNames: Collection<String>) {
        if (fileNames.isEmpty()) return
        val targets = fileNames.toHashSet()
        update { config ->
            if (config.history.none { it.fileName in targets && it.lastFile != null }) return@update config
            config.copy(
                history = config.history.map {
                    if (it.fileName in targets) it.copy(lastFile = null, lastOpenedAt = null) else it
                }
            )
        }
    }

    /** 記錄某個資料夾在 MainPage 的瀏覽位置。 */
    fun updateFolderScrollPosition(folderUri: String, itemIndex: Int) {
        if (folderUri.isEmpty()) return
        update { config ->
            val positions = config.folderScrollPositions
            val index = positions.indexOfFirst { it.folderUri == folderUri }
            when {
                index < 0 -> config.copy(
                    folderScrollPositions = positions + FolderScrollHistoryItem(folderUri, itemIndex)
                )

                positions[index].itemIndex == itemIndex -> config

                else -> config.copy(
                    folderScrollPositions = positions.toMutableList().also {
                        it[index] = it[index].copy(itemIndex = itemIndex)
                    }
                )
            }
        }
    }

    fun getFolderScrollPosition(folderUri: String): FolderScrollHistoryItem? =
        current.folderScrollPositions.firstOrNull { it.folderUri == folderUri }

    /**
     * 更新指定檔案的閱讀紀錄。[createIfMissing] 為 false 時，沒有紀錄就什麼都不做。
     */
    private fun updateHistoryItem(
        fileName: String,
        createIfMissing: Boolean = true,
        transform: (HistoryItem) -> HistoryItem,
    ) = update { config ->
        val index = config.history.indexOfFirst { it.fileName == fileName }
        when {
            index >= 0 -> {
                val updated = transform(config.history[index])
                if (updated == config.history[index]) config
                else config.copy(history = config.history.toMutableList().also { it[index] = updated })
            }

            createIfMissing -> config.copy(history = config.history + transform(HistoryItem(fileName = fileName)))

            else -> config
        }
    }

    // endregion

    // region 讀寫設定檔

    /**
     * 套用變更並標記設定待寫，實際的序列化與寫檔交給背景 writer。呼叫端（通常是主執行緒）
     * 立即返回，不會被磁碟 I/O 卡住。
     */
    private fun update(transform: (AppConfig) -> AppConfig) {
        _config.update(transform)
        if (_config.value !== lastWritten) saveSignal.trySend(Unit)
    }

    /** 目前設定的 JSON 文字，供 RawConfig 畫面顯示。 */
    fun configJson(): String = json.encodeToString(AppConfig.serializer(), current)

    /**
     * 把還沒寫出去的變更立刻同步寫入。App 進入背景時呼叫：行程隨時可能被系統回收，
     * 背景 writer 的延遲寫入不一定來得及跑完。
     */
    fun flush() = writeIfDirty()

    private fun writeIfDirty() {
        synchronized(fileLock) {
            val snapshot = _config.value
            if (snapshot === lastWritten && saveFile.exists()) return
            if (writeAtomic(json.encodeToString(AppConfig.serializer(), snapshot))) {
                lastWritten = snapshot
            }
        }
    }

    /**
     * 先寫暫存檔再原子替換，避免寫到一半中斷時把使用者的閱讀進度與章節快取一起毀掉。
     * 寫入失敗不往外拋：呼叫端有可能是捲動事件，下一次設定變更自然會再寫一次。
     */
    private fun writeAtomic(text: String): Boolean {
        val tmp = File(saveFile.parentFile, saveFile.name + ".tmp")
        return try {
            saveFile.parentFile?.mkdirs()
            tmp.writeText(text, Charsets.UTF_8)
            Files.move(
                tmp.toPath(),
                saveFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "設定檔寫入失敗", e)
            tmp.delete()
            false
        }
    }

    private fun loadConfig() {
        if (!saveFile.exists()) return
        try {
            val loaded = json.decodeFromString(AppConfig.serializer(), saveFile.readText(Charsets.UTF_8))
            _config.value = loaded
            lastWritten = loaded
        } catch (e: Exception) {
            // 設定檔壞掉：把原檔另存一份再改用預設值，不直接覆蓋，讓使用者還有機會救回資料。
            Log.e(TAG, "設定檔讀取失敗，改用預設設定", e)
            configLoadFailed = true
            try {
                saveFile.copyTo(corruptBackupFile, overwrite = true)
            } catch (copyError: Exception) {
                Log.e(TAG, "損毀的設定檔備份失敗", copyError)
            }
        }
    }

    // endregion

    companion object {
        private const val TAG = "MainRepo"
        private const val COALESCE_DELAY_MS = 500L
        private const val CORRUPT_BACKUP_NAME = "appConfig.corrupt.json"

        const val CONFIG_FILE_NAME = "appConfig.json"

        // 分隔符用不會出現在 Regex 設定裡的控制字元，避免不同清單湊出同一把 key。
        private const val CACHE_KEY_SEPARATOR = '\u0001'

        /**
         * 章節快取的有效性條件：Regex 清單 + 標題字數上限 + 起始行數 + 檔案行數。
         * 任一改變就代表舊的章節資料不能用了。
         */
        fun buildChapterCacheKey(
            patterns: List<String>,
            titleMaxLength: Int,
            skipLines: Int,
            lineCount: Int,
        ): String = buildString {
            patterns.forEach { append(it).append(CACHE_KEY_SEPARATOR) }
            append(titleMaxLength).append(CACHE_KEY_SEPARATOR)
            append(skipLines).append(CACHE_KEY_SEPARATOR)
            append(lineCount)
        }
    }
}
