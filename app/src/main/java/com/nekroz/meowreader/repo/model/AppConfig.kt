package com.nekroz.meowreader.repo.model

import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import kotlinx.serialization.Serializable

enum class AppPage {
    MainPage,
    ReaderPage,
}

enum class AppTheme {
    /** 跟隨系統 */
    Default,
    Light,
    Dark,
}

/**
 * 一個資料夾。Android 透過 Storage Access Framework 存取檔案，沒有真正的檔案路徑，
 * 資料夾以 document URI 辨識，名稱另外存一份給畫面顯示。
 */
@Serializable
data class FolderRef(
    val uri: String,
    val name: String,
)

/** 目前（或上次）在 ReaderPage 開啟的檔案。 */
@Serializable
data class OpenFile(
    val uri: String,
    /** 含副檔名的檔名。 */
    val name: String,
    /** 檔案所在資料夾的 document URI，用來尋找 raw 子資料夾內的原文檔案。 */
    val folderUri: String,
)

/**
 * 整份設定檔（appConfig.json）。全部都是不可變資料，變更一律透過 MainRepo 產生新的一份。
 */
@Serializable
data class AppConfig(
    /**
     * 從使用者選的根資料夾一路走到目前資料夾的路徑。SAF 沒辦法由子資料夾反查上一層，
     * 所以把走過的路徑整條記下來，「返回上一層」就是退掉最後一項。
     */
    val folderStack: List<FolderRef> = emptyList(),
    val openFile: OpenFile? = null,
    val lastPage: AppPage = AppPage.MainPage,
    val readerSetting: ReaderSetting = ReaderSetting(),
    /** 全域章節抓取設定，單一檔案沒有專屬設定時的預設值。 */
    val chapterSetting: ChapterRegexSetting? = null,
    val theme: AppTheme = AppTheme.Default,
    /**
     * 釘選的資料夾（document URI）。資料夾沒有 HistoryItem 可以掛旗標，只能另外存一份清單；
     * 檔案的釘選狀態則記在 [HistoryItem.isPinned]。
     */
    val pinnedFolders: List<String> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val folderScrollPositions: List<FolderScrollHistoryItem> = emptyList(),
) {
    val rootFolder: FolderRef? get() = folderStack.firstOrNull()
    val currentFolder: FolderRef? get() = folderStack.lastOrNull()
}
