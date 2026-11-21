package com.nekroz.meowreader.ui.historypage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nekroz.meowreader.MeowReaderApp
import com.nekroz.meowreader.repo.model.AppConfig
import com.nekroz.meowreader.repo.model.OpenFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 閱讀歷史的一列。 */
data class HistoryEntry(
    /** 閱讀紀錄的 key（不含副檔名的檔名）。 */
    val key: String,
    val file: OpenFile,
    /** 上次開啟的時間（epoch 毫秒）。 */
    val lastOpenedAt: Long,
    val isPinned: Boolean,
    /** 閱讀進度百分比（0~100）；資訊不足時為 null。 */
    val progressPercent: Double?,
)

data class HistoryPageUiState(
    val entries: List<HistoryEntry> = emptyList(),
    /** 編輯模式：點一列是勾選／取消勾選，而不是開啟檔案。 */
    val isEditing: Boolean = false,
    /** 勾選中的列（[HistoryEntry.key]）。只會包含目前還在清單上的項目。 */
    val selectedKeys: Set<String> = emptySet(),
) {
    val allSelected: Boolean get() = entries.isNotEmpty() && selectedKeys.size == entries.size
}

class HistoryPageViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as MeowReaderApp).mainRepo

    private data class EditState(val isEditing: Boolean = false, val selectedKeys: Set<String> = emptySet())

    private val editState = MutableStateFlow(EditState())

    private val entries: StateFlow<List<HistoryEntry>> =
        repo.config
            .map(::buildEntries)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildEntries(repo.current))

    /** 閱讀進度、釘選狀態都直接跟著設定檔更新。 */
    val uiState: StateFlow<HistoryPageUiState> =
        combine(entries, editState, ::buildUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                buildUiState(entries.value, editState.value),
            )

    /** 記下要開啟的檔案；跳轉到 ReaderPage 由畫面層負責。 */
    fun openFile(entry: HistoryEntry) {
        repo.setOpenFile(entry.file)
    }

    /** 進入編輯模式；從長按進來時順便勾選那一列。 */
    fun startEditing(initialKey: String? = null) {
        editState.value = EditState(isEditing = true, selectedKeys = setOfNotNull(initialKey))
    }

    fun stopEditing() {
        editState.value = EditState()
    }

    fun toggleSelected(key: String) = editState.update {
        it.copy(selectedKeys = if (key in it.selectedKeys) it.selectedKeys - key else it.selectedKeys + key)
    }

    /** 已經全選就全部取消，否則全選。 */
    fun toggleSelectAll() = editState.update { state ->
        val keys = entries.value.mapTo(HashSet()) { it.key }
        state.copy(selectedKeys = if (state.selectedKeys.containsAll(keys)) emptySet() else keys)
    }

    /** 從閱讀歷史移除勾選的項目（閱讀進度保留），並離開編輯模式。 */
    fun clearSelected() {
        repo.clearOpenRecords(uiState.value.selectedKeys)
        stopEditing()
    }

    private fun buildUiState(entries: List<HistoryEntry>, edit: EditState): HistoryPageUiState {
        // 清單空了就沒有東西可編輯，自動離開編輯模式。
        if (!edit.isEditing || entries.isEmpty()) return HistoryPageUiState(entries = entries)
        val visibleKeys = entries.mapTo(HashSet()) { it.key }
        return HistoryPageUiState(
            entries = entries,
            isEditing = true,
            selectedKeys = edit.selectedKeys.filterTo(HashSet()) { it in visibleKeys },
        )
    }

    /**
     * 只列出記得檔案位置的紀錄：沒有位置就沒辦法重新開啟。
     * 單純釘選過、或是舊版設定檔留下來的紀錄沒有位置，等那個檔案再被開過一次才會出現。
     */
    private fun buildEntries(config: AppConfig): List<HistoryEntry> =
        config.history
            .mapNotNull { item ->
                val file = item.lastFile ?: return@mapNotNull null
                val openedAt = item.lastOpenedAt ?: return@mapNotNull null
                HistoryEntry(
                    key = item.fileName,
                    file = file,
                    lastOpenedAt = openedAt,
                    isPinned = item.isPinned,
                    progressPercent = item.progressPercent,
                )
            }
            .sortedByDescending { it.lastOpenedAt }
}
