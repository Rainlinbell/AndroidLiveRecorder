package com.liverecorder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.liverecorder.app.storage.RecordFileEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordHistoryScreen(
    recordFiles: List<RecordFileEntry>,
    onDeleteFile: (RecordFileEntry) -> Unit,
    onBack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<RecordFileEntry?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录制历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (recordFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无录制记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordFiles, key = { it.displayName }) { recordFile ->
                    RecordFileCard(
                        recordFile = recordFile,
                        onPlay = { playRecord(context, recordFile) },
                        onDelete = { showDeleteDialog = recordFile },
                        onShare = { shareRecord(context, recordFile) }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个录制文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFile(file)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 播放录制文件：File 用 FileProvider，SAF Uri 直接用 */
private fun playRecord(context: android.content.Context, recordFile: RecordFileEntry) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(getPlayableUri(context, recordFile), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "无法播放: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 分享录制文件 */
private fun shareRecord(context: android.content.Context, recordFile: RecordFileEntry) {
    try {
        val uri = getShareableUri(context, recordFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享录制文件"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "无法分享: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 获取可被其他应用读取的 Uri：File 通过 FileProvider，SAF Uri 直接使用 */
private fun getPlayableUri(context: android.content.Context, recordFile: RecordFileEntry): Uri {
    val file = recordFile.file
    return if (file != null) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } else {
        recordFile.uri ?: throw IllegalStateException("RecordFile 既无 File 也无 Uri")
    }
}

private fun getShareableUri(context: android.content.Context, recordFile: RecordFileEntry): Uri {
    return getPlayableUri(context, recordFile)
}

@Composable
fun RecordFileCard(
    recordFile: RecordFileEntry,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${getPlatformDisplayName(recordFile.platform)} - ${recordFile.roomId}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = recordFile.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDateTime(recordFile.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(recordFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "播放")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "分享")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun getPlatformDisplayName(platformId: String): String {
    return when (platformId) {
        "bilibili" -> "B站"
        "douyin" -> "抖音"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        else -> platformId
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    }
}
