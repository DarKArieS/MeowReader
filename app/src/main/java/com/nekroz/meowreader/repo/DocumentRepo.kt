package com.nekroz.meowreader.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.nekroz.meowreader.repo.model.FolderRef
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 資料夾底下的一個項目（子資料夾或 txt 檔）。 */
data class DocumentEntry(
    val uri: Uri,
    val name: String,
    val isFolder: Boolean,
    /** 最後修改時間（epoch 毫秒），提供者沒回報時為 0。 */
    val lastModified: Long,
)

/**
 * 透過 Storage Access Framework 存取使用者選的資料夾。
 *
 * Android 沒辦法像桌面版那樣直接用檔案路徑讀寫，所有操作都以 document URI 進行；
 * 這個類別把 ContentResolver / DocumentsContract 的細節包起來，ViewModel 不必碰它們。
 */
class DocumentRepo(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /**
     * 讓系統在重開機後仍保留這個資料夾的讀寫權限，並回傳對應的根資料夾。
     * [treeUri] 是資料夾選擇器回傳的 tree URI。
     */
    suspend fun adoptTree(treeUri: Uri): FolderRef = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(treeUri, PERMISSION_FLAGS)

        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        FolderRef(uri = rootUri.toString(), name = queryDisplayName(rootUri) ?: rootUri.lastPathSegment.orEmpty())
    }

    /** 放掉不再使用的資料夾權限。系統對可保留的權限數量有上限，換資料夾時順手清掉舊的。 */
    fun releaseTree(documentUri: Uri) {
        try {
            val treeUri = DocumentsContract.buildTreeDocumentUri(
                documentUri.authority, DocumentsContract.getTreeDocumentId(documentUri)
            )
            resolver.releasePersistableUriPermission(treeUri, PERMISSION_FLAGS)
        } catch (_: Exception) {
            // 權限本來就不在了也無妨。
        }
    }

    /**
     * 列出資料夾底下的子資料夾與 txt 檔（不含隱藏項目），順序未排序。
     * 名稱、類型與修改時間由同一次查詢帶回，不必再對每個項目多查一次。
     *
     * @throws SecurityException 資料夾的存取權限已失效
     */
    suspend fun listFolder(folderUri: Uri): List<DocumentEntry> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri, DocumentsContract.getDocumentId(folderUri)
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )

        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("無法列出資料夾內容：$folderUri")

        cursor.use {
            val result = ArrayList<DocumentEntry>(it.count)
            while (it.moveToNext()) {
                val documentId = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                if (name.startsWith('.')) continue

                val isFolder = it.getString(2) == Document.MIME_TYPE_DIR
                if (!isFolder && !name.endsWith(TXT_EXTENSION, ignoreCase = true)) continue

                result.add(
                    DocumentEntry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId),
                        name = name,
                        isFolder = isFolder,
                        lastModified = if (it.isNull(3)) 0L else it.getLong(3),
                    )
                )
            }
            result
        }
    }

    /** 讀取整個文字檔並切成行。編碼與換行的處理方式見 [TextFileCodec]。 */
    suspend fun readLines(fileUri: Uri): List<String> = withContext(Dispatchers.IO) {
        TextFileCodec.readLines(readBytes(fileUri))
    }

    /**
     * 找出翻譯前的原文檔案。慣例是把原文放在同資料夾的 raw 子資料夾、檔名相同。
     * 找不到時回傳 null。
     */
    suspend fun findRawFile(folderUri: Uri, fileName: String): Uri? {
        return try {
            val rawFolder = listFolder(folderUri)
                .firstOrNull { it.isFolder && it.name.equals(RAW_FOLDER_NAME, ignoreCase = true) }
                ?: return null
            listFolder(rawFolder.uri)
                .firstOrNull { !it.isFolder && it.name.equals(fileName, ignoreCase = true) }
                ?.uri
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * 把檔案中指定的一行換成新內容並寫回。只重組被編輯的那一行，其餘內容與原本的編碼、
     * BOM、換行字元原封不動保留，避免改一行就把整個檔案的格式換掉。
     *
     * @throws UndecodableFileException 檔案不是 UTF-8／UTF-16，解碼後再寫回會毀掉內容
     * @throws LineOutOfRangeException 行號超出檔案範圍
     */
    suspend fun updateLine(fileUri: Uri, lineIndex: Int, newText: String) = withContext(Dispatchers.IO) {
        // 先在記憶體裡把整份內容準備好才打開輸出串流：SAF 沒有「寫暫存檔再原子替換」可用，
        // 只能直接覆寫原檔，至少把檔案處於半截狀態的時間壓到最短。
        val bytes = TextFileCodec.replaceLine(readBytes(fileUri), lineIndex, newText)
        val output = resolver.openOutputStream(fileUri, "wt")
            ?: throw FileNotFoundException(fileUri.toString())
        output.use { it.write(bytes) }
    }

    private fun readBytes(fileUri: Uri): ByteArray {
        val input = resolver.openInputStream(fileUri) ?: throw FileNotFoundException(fileUri.toString())
        return input.use { it.readBytes() }
    }

    private fun queryDisplayName(documentUri: Uri): String? = try {
        resolver.query(documentUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TXT_EXTENSION = ".txt"
        private const val RAW_FOLDER_NAME = "raw"
        private const val PERMISSION_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
