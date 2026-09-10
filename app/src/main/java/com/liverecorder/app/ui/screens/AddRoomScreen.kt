package com.liverecorder.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liverecorder.app.platform.PlatformAdapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRoomScreen(
    adapters: Set<PlatformAdapter>,
    onAddRoom: (url: String, platform: String, roomId: String) -> Unit,
    onBack: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detectedPlatform by remember { mutableStateOf<PlatformAdapter?>(null) }

    // 自动检测平台
    LaunchedEffect(urlInput) {
        detectedPlatform = adapters.firstOrNull { it.matchUrl(urlInput) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加直播间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 输入框
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    errorMessage = null
                },
                label = { Text("直播间链接") },
                placeholder = { Text("请输入直播间链接，如: https://live.bilibili.com/12345") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                leadingIcon = {
                    Icon(Icons.Default.Link, contentDescription = null)
                },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                }
            )

            // 平台检测提示
            if (detectedPlatform != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已识别平台: ${detectedPlatform!!.platformName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 错误提示
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 添加按钮
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    // 实际添加逻辑由 ViewModel 处理
                    onAddRoom(urlInput, detectedPlatform?.platformId ?: "", "")
                    isLoading = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = urlInput.isNotEmpty() && detectedPlatform != null && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("添加直播间")
            }

            // 支持的平台列表
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "支持的平台",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    adapters.forEach { adapter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = adapter.platformName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = getPlatformUrlHint(adapter.platformId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 批量导入提示
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "批量导入",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "每行一个直播间链接，可一次性导入多个直播间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getPlatformUrlHint(platformId: String): String {
    return when (platformId) {
        "bilibili" -> "live.bilibili.com/xxx"
        "douyin" -> "live.douyin.com/xxx"
        "douyu" -> "douyu.com/xxx"
        "huya" -> "huya.com/xxx"
        else -> platformId
    }
}
