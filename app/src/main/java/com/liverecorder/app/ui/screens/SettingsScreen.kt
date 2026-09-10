package com.liverecorder.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liverecorder.app.platform.PlatformAdapter
import com.liverecorder.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    monitorInterval: Int,
    onIntervalChange: (Int) -> Unit,
    isRootAvailable: Boolean,
    isRootGranted: Boolean,
    onRequestRoot: () -> Unit,
    storagePath: String,
    onOpenPath: () -> Unit,
    onChangePath: () -> Unit,
    adapters: Set<PlatformAdapter> = emptySet(),
    getCookie: (String) -> String? = { null },
    onClearCookie: (String) -> Unit = {},
    onShowQRLogin: (PlatformAdapter) -> Unit = { },
    onBack: () -> Unit
) {
    var intervalText by remember { mutableStateOf(monitorInterval.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
        ) {
            // 监控设置
            SettingsSection(title = "监控设置", icon = Icons.Default.Timer) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            intervalText = newValue
                            val interval = newValue.toIntOrNull()
                            if (interval != null && interval in 1..3600) {
                                onIntervalChange(interval)
                            }
                        }
                    },
                    label = { Text("检测间隔（秒）") },
                    supportingText = { Text("范围: 1-3600秒，默认30秒") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Timer, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 平台登录设置
            if (adapters.isNotEmpty()) {
                SettingsSection(title = "平台登录", icon = Icons.Default.Person) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "登录平台以获取 Cookie，用于获取高质量直播流",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        adapters.forEach { adapter ->
                            PlatformLoginCard(
                                adapter = adapter,
                                isLoggedIn = getCookie(adapter.platformId) != null,
                                onLogin = { onShowQRLogin(adapter) },
                                onClearCookie = { onClearCookie(adapter.platformId) }
                            )
                        }
                    }
                }
            }

            // Root 设置
            SettingsSection(title = "Root 权限", icon = Icons.Default.Security) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (isRootGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isRootGranted) LiveGreen else LiveOrange,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Root 状态",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (isRootAvailable) {
                                            if (isRootGranted) "已获取Root权限" else "设备已Root，未授权"
                                        } else "设备未Root",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isRootGranted)
                                            LiveGreen
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isRootAvailable && !isRootGranted) {
                                Button(
                                    onClick = onRequestRoot,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("授权")
                                }
                            }
                        }

                        if (isRootGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Root权限已启用，应用将获得以下增强能力：",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            RootFeatureItem("后台常驻 - 防止系统杀死录制服务")
                            RootFeatureItem("禁用电池优化 - 确保长时间运行")
                            RootFeatureItem("开机自启动 - 设备重启后自动恢复")
                        }
                    }
                }
            }

            // 存储设置
            SettingsSection(title = "存储设置", icon = Icons.Default.Folder) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenPath() },
                                onLongClick = { onChangePath() }
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "录制文件存储路径",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = storagePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击打开目录 · 长按修改路径",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 关于
            SettingsSection(title = "关于", icon = Icons.Default.Info) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "直播录制助手",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "版本 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "支持平台：哔哩哔哩、抖音、斗鱼、虎牙",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "直接下载直播流进行录制",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PlatformLoginCard(
    adapter: PlatformAdapter,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onClearCookie: () -> Unit
) {
    val platformColor = getPlatformColor(adapter.platformId)
    val platformEmoji = getPlatformEmoji(adapter.platformId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 平台图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(platformColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = platformEmoji,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = platformColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 平台信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adapter.platformName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isLoggedIn) "✓ 已登录" else "未登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLoggedIn) LiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            if (isLoggedIn) {
                IconButton(onClick = onClearCookie) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "清除Cookie",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Button(
                onClick = onLogin,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = platformColor
                ),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    if (isLoggedIn) "重新登录" else "登录",
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun RootFeatureItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = LiveGreen,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
