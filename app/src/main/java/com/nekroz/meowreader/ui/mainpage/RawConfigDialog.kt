package com.nekroz.meowreader.ui.mainpage

import android.content.ClipData
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nekroz.meowreader.R
import kotlinx.coroutines.launch

/**
 * 以全螢幕對話框檢視設定檔（appConfig.json）的原始內容。
 * 設定檔放在 App 的私有目錄，其他 App 看不到，所以直接在這裡顯示，需要時可以整份複製出去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawConfigDialog(
    json: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // 設定檔含章節快取時可能有上萬行，逐行放進 LazyColumn 才不會一次排版整份文字。
    val lines = remember(json) { json.lines() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.raw_config_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText(CLIP_LABEL, json))
                                        )
                                    } catch (e: Exception) {
                                        // 內容太大時系統剪貼簿可能拒收，不能讓它把 App 弄當。
                                        Log.w(TAG, "複製設定內容失敗", e)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                    },
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private const val TAG = "RawConfigDialog"
private const val CLIP_LABEL = "appConfig.json"
