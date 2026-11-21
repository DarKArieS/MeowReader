package com.nekroz.meowreader.repo

import com.nekroz.meowreader.repo.chapter.ChapterItem
import com.nekroz.meowreader.repo.chapter.ChapterRegexSetting
import com.nekroz.meowreader.repo.model.AppConfig
import com.nekroz.meowreader.repo.model.AppPage
import com.nekroz.meowreader.repo.model.AppTheme
import com.nekroz.meowreader.repo.model.FolderRef
import com.nekroz.meowreader.repo.model.OpenFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainRepoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var saveFile: File

    @Before
    fun setUp() {
        saveFile = File(tempFolder.root, MainRepo.CONFIG_FILE_NAME)
    }

    @Test
    fun `starts with defaults when there is no config file`() {
        val repo = MainRepo(saveFile)

        assertEquals(AppConfig(), repo.current)
        assertFalse(repo.configLoadFailed)
    }

    @Test
    fun `flush persists everything and a new instance reads it back`() {
        val repo = MainRepo(saveFile)
        repo.setRootFolder(FolderRef("content://tree/root", "小說"))
        repo.enterFolder(FolderRef("content://tree/root/sub", "子資料夾"))
        repo.setOpenFile(OpenFile("content://tree/root/sub/a.txt", "第1章 開始.txt", "content://tree/root/sub"))
        repo.setLastPage(AppPage.ReaderPage)
        repo.setTheme(AppTheme.Dark)
        repo.setFontSize(24.0)
        repo.setCustomBackgroundColor("#F5F0E1")
        repo.setUseCustomColors(true)
        repo.setFolderPinned("content://tree/root/sub", true)
        repo.setFilePinned("第1章 開始", true)
        repo.updateHistory("第1章 開始", lineIndex = 42, lineFraction = 0.25, readLines = 60, totalLines = 400)
        repo.updateChapters("第1章 開始", listOf(ChapterItem("第一話", 3), ChapterItem("第二話", 90)), "key")
        repo.setChapterSetting("第1章 開始", ChapterRegexSetting(listOf("^Chapter \\d+"), 40, 5))
        repo.updateFolderScrollPosition("content://tree/root", 7)
        repo.setGlobalChapterSetting(ChapterRegexSetting(chapterRegexList = emptyList()))

        repo.flush()

        assertTrue(saveFile.exists())
        val reloaded = MainRepo(saveFile)
        assertFalse(reloaded.configLoadFailed)
        assertEquals(repo.current, reloaded.current)
        assertEquals(15.0, reloaded.getHistoryItem("第1章 開始")!!.progressPercent!!, 0.0001)
        // 中文要以真實字元存檔，而不是 \uXXXX。
        assertTrue(saveFile.readText().contains("第1章 開始"))
    }

    @Test
    fun `changes are written by the background writer without an explicit flush`() {
        val repo = MainRepo(saveFile)

        repo.setTheme(AppTheme.Light)
        // 合併視窗是 500ms，留足夠的餘裕給背景執行緒。
        val deadline = System.currentTimeMillis() + 5_000
        while (!saveFile.exists() && System.currentTimeMillis() < deadline) Thread.sleep(50)

        assertEquals(AppTheme.Light, MainRepo(saveFile).current.theme)
    }

    @Test
    fun `a corrupt config file is backed up and replaced by defaults`() {
        saveFile.writeText("{ this is not json")

        val repo = MainRepo(saveFile)

        assertTrue(repo.configLoadFailed)
        assertEquals(AppConfig(), repo.current)
        assertEquals("{ this is not json", repo.corruptBackupFile.readText())
    }

    @Test
    fun `unknown keys in the config file are ignored`() {
        saveFile.writeText("""{ "theme": "Dark", "someFutureField": 123 }""")

        val repo = MainRepo(saveFile)

        assertFalse(repo.configLoadFailed)
        assertEquals(AppTheme.Dark, repo.current.theme)
    }

    @Test
    fun `saving an unchanged reading position does not produce a new config`() {
        val repo = MainRepo(saveFile)
        repo.updateHistory("book", lineIndex = 10, lineFraction = 0.5, readLines = 30, totalLines = 100)
        val before = repo.current

        repo.updateHistory("book", lineIndex = 10, lineFraction = 0.505, readLines = 30, totalLines = 100)
        assertSame(before, repo.current)

        repo.updateHistory("book", lineIndex = 11, lineFraction = 0.0, readLines = 31, totalLines = 100)
        assertNotEquals(before, repo.current)
        assertEquals(11, repo.getHistoryItem("book")!!.lineIndex)
    }

    @Test
    fun `folder navigation keeps the path from the root`() {
        val repo = MainRepo(saveFile)
        assertFalse(repo.backToParent())

        repo.setRootFolder(FolderRef("root", "根"))
        repo.enterFolder(FolderRef("a", "甲"))
        repo.enterFolder(FolderRef("b", "乙"))
        assertEquals("b", repo.current.currentFolder!!.uri)

        assertTrue(repo.backToParent())
        assertEquals("a", repo.current.currentFolder!!.uri)
        assertTrue(repo.backToParent())
        // 已經在根資料夾就不能再往上。
        assertFalse(repo.backToParent())
        assertEquals("root", repo.current.currentFolder!!.uri)

        repo.setRootFolder(FolderRef("other", "另一個"))
        assertEquals(listOf(FolderRef("other", "另一個")), repo.current.folderStack)
    }

    @Test
    fun `pinning a file creates a history record but unpinning an unknown file does not`() {
        val repo = MainRepo(saveFile)

        repo.setFilePinned("unknown", false)
        assertNull(repo.getHistoryItem("unknown"))

        repo.setFilePinned("book", true)
        assertTrue(repo.isFilePinned("book"))

        // 取消釘選不刪紀錄：裡面可能還有閱讀進度與章節快取。
        repo.updateHistory("book", 5, 0.0, 6, 10)
        repo.setFilePinned("book", false)
        assertFalse(repo.isFilePinned("book"))
        assertEquals(5, repo.getHistoryItem("book")!!.lineIndex)
    }

    @Test
    fun `folders are pinned by uri without duplicates`() {
        val repo = MainRepo(saveFile)

        repo.setFolderPinned("folder", true)
        repo.setFolderPinned("folder", true)
        assertEquals(listOf("folder"), repo.current.pinnedFolders)
        assertTrue(repo.isFolderPinned("folder"))

        repo.setFolderPinned("folder", false)
        assertFalse(repo.isFolderPinned("folder"))
    }

    @Test
    fun `a file falls back to the global chapter setting until it gets its own`() {
        val repo = MainRepo(saveFile)
        val global = ChapterRegexSetting(listOf("global"), 50, 0)
        val custom = ChapterRegexSetting(listOf("custom"), 30, 2)
        repo.setGlobalChapterSetting(global)

        assertEquals(global, repo.getChapterSetting("book"))
        // 本來就沒紀錄等於已經在用預設值，不必為此新增一筆空紀錄。
        repo.setChapterSettingUseDefault("book", true)
        assertNull(repo.getHistoryItem("book"))

        repo.setChapterSetting("book", custom)
        assertEquals(custom, repo.getChapterSetting("book"))
        assertFalse(repo.getHistoryItem("book")!!.useDefaultChapterSetting)

        // 切回「使用預設值」只是暫時不用，自訂的內容要留著。
        repo.setChapterSettingUseDefault("book", true)
        assertEquals(global, repo.getChapterSetting("book"))
        assertEquals(custom, repo.getStoredChapterSetting("book"))

        repo.setChapterSettingUseDefault("book", false)
        assertEquals(custom, repo.getChapterSetting("book"))
    }

    @Test
    fun `recording an open keeps the rest of the history item and survives a reload`() {
        val repo = MainRepo(saveFile)
        repo.updateHistory("book", lineIndex = 10, lineFraction = 0.5, readLines = 20, totalLines = 100)
        repo.setFilePinned("book", true)
        val file = OpenFile("content://tree/root/book.txt", "book.txt", "content://tree/root", "小說")

        repo.recordFileOpened("book", file, openedAt = 1_700_000_000_000)
        repo.flush()

        val item = MainRepo(saveFile).getHistoryItem("book")!!
        assertEquals(file, item.lastFile)
        assertEquals(1_700_000_000_000, item.lastOpenedAt)
        assertEquals(10, item.lineIndex)
        assertTrue(item.isPinned)
    }

    @Test
    fun `clearing open records keeps the reading progress`() {
        val repo = MainRepo(saveFile)
        val root = "content://tree/root"
        repo.updateHistory("a", lineIndex = 10, lineFraction = 0.0, readLines = 20, totalLines = 100)
        repo.recordFileOpened("a", OpenFile("$root/a.txt", "a.txt", root), openedAt = 1)
        repo.recordFileOpened("b", OpenFile("$root/b.txt", "b.txt", root), openedAt = 2)

        repo.clearOpenRecords(listOf("a"))

        val a = repo.getHistoryItem("a")!!
        assertNull(a.lastFile)
        assertNull(a.lastOpenedAt)
        assertEquals(10, a.lineIndex)
        assertEquals(20, a.readLines)
        assertEquals(2L, repo.getHistoryItem("b")!!.lastOpenedAt)
    }

    @Test
    fun `clearing records that are already gone does not touch the config`() {
        val repo = MainRepo(saveFile)
        repo.updateHistory("a", lineIndex = 1, lineFraction = 0.0, readLines = 2, totalLines = 10)
        val before = repo.current

        repo.clearOpenRecords(listOf("a", "missing"))

        assertSame(before, repo.current)
    }

    @Test
    fun `reader settings are clamped to their valid range`() {
        val repo = MainRepo(saveFile)

        repo.setLineSpacing(1.5 + 0.1)
        assertEquals(1.6, repo.current.readerSetting.lineSpacing, 0.0)
        repo.setLineSpacing(0.5)
        assertEquals(1.2, repo.current.readerSetting.lineSpacing, 0.0)
        repo.setLineSpacing(9.0)
        assertEquals(3.0, repo.current.readerSetting.lineSpacing, 0.0)

        repo.setFontSize(1.0)
        assertEquals(8.0, repo.current.readerSetting.fontSize, 0.0)
        repo.setFontSize(500.0)
        assertEquals(72.0, repo.current.readerSetting.fontSize, 0.0)
    }

    @Test
    fun `the chapter cache key changes with every scanning condition`() {
        val base = MainRepo.buildChapterCacheKey(listOf("a", "b"), 60, 0, 100)

        assertEquals(base, MainRepo.buildChapterCacheKey(listOf("a", "b"), 60, 0, 100))
        assertNotEquals(base, MainRepo.buildChapterCacheKey(listOf("ab"), 60, 0, 100))
        assertNotEquals(base, MainRepo.buildChapterCacheKey(listOf("a", "b"), 61, 0, 100))
        assertNotEquals(base, MainRepo.buildChapterCacheKey(listOf("a", "b"), 60, 1, 100))
        assertNotEquals(base, MainRepo.buildChapterCacheKey(listOf("a", "b"), 60, 0, 101))
    }
}
