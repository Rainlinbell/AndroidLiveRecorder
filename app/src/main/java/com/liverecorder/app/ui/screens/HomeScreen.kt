package com.liverecorder.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liverecorder.app.data.db.entity.LiveRoomEntity
import com.liverecorder.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    rooms: List<LiveRoomEntity>,
    isServiceRunning: Boolean,
    isRefreshing: Boolean,
    onToggleService: () -> Unit,
    onAddRoom: () -> Unit,
    onSettings: () -> Unit,
    onToggleRoom: (Long, Boolean) -> Unit,
    onDeleteRoom: (Long) -> Unit,
    onPauseRoom: (Long) -> Unit,
    onResumeRoom: (Long) -> Unit,
    onUpdateQuality: (Long, String) -> Unit,
    onToggleSpecialFocus: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                onRefresh()
                delay(1000)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "直播录制助手",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRoom,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加直播间")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 服务状态卡片
                ServiceStatusCard(
                    isRunning = isServiceRunning,
                    onToggle = onToggleService,
                    recordingCount = rooms.count { it.isRecording }
                )

                // 直播间列表
                if (rooms.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rooms, key = { it.id }) { room ->
                            LiveRoomCard(
                                room = room,
                                onToggle = { onToggleRoom(room.id, !room.enabled) },
                                onDelete = { onDeleteRoom(room.id) },
                                onPause = { onPauseRoom(room.id) },
                                onResume = { onResumeRoom(room.id) },
                                onQualityChange = { quality -> onUpdateQuality(room.id, quality) },
                                onToggleSpecialFocus = { onToggleSpecialFocus(room.id) }
                            )
                        }
                    }
                }
            }

            // 下拉刷新指示器
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun ServiceStatusCard(
    isRunning: Boolean,
    onToggle: () -> Unit,
    recordingCount: Int
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val gradientBrush = if (isRunning) {
        Brush.horizontalGradient(
            colors = listOf(
                LiveGreen.copy(alpha = 0.15f),
                RecordingBlue.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 呼吸动画状态指示器
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) LiveGreen else Color.Gray
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning) "监控服务运行中" else "监控服务已停止",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (recordingCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "正在录制 $recordingCount 个直播",
                            style = MaterialTheme.typography.bodySmall,
                            color = RecordingBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = onToggle,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) LiveRed else LiveGreen
                    )
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRunning) "停止" else "启动")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LiveRoomCard(
    room: LiveRoomEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onQualityChange: (String) -> Unit,
    onToggleSpecialFocus: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { showLongPressMenu = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(50.dp)
                ) {
                    // 状态圆点
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    room.isPaused -> PausedAmber
                                    room.isRecording -> RecordingBlue
                                    room.isLive -> LiveGreen
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            room.isPaused -> "已暂停"
                            room.isRecording -> "录制中"
                            room.isLive -> "直播中"
                            else -> "离线"
                        },
                        fontSize = 10.sp,
                        color = when {
                            room.isPaused -> PausedAmber
                            room.isRecording -> RecordingBlue
                            room.isLive -> LiveGreen
                            else -> Color.Gray
                        }
                    )
                }

                // 房间信息
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = room.roomTitle.ifEmpty { "直播间 ${room.roomId}" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${getPlatformName(room.platform)} · ${room.roomId} · ${getQualityLabel(room.quality)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (room.lastChecked > 0) {
                        Text(
                            text = "上次检测: ${formatTime(room.lastChecked)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 开关 + 删除
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = room.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 操作按钮行
            if (room.isLive || room.isRecording || room.isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 画质选择按钮
                    TextButton(onClick = { showQualityMenu = true }) {
                        Icon(
                            Icons.Default.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(getQualityLabel(room.quality))
                    }

                    // 暂停/恢复按钮
                    if (room.isPaused) {
                        TextButton(onClick = onResume) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "恢复录制",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复")
                        }
                    } else if (room.isLive || room.isRecording) {
                        TextButton(onClick = onPause) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "暂停录制",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("暂停")
                        }
                    }
                }
            }
        }
    }

    // 长按菜单
    if (showLongPressMenu) {
        LongPressMenu(
            room = room,
            onDismiss = { showLongPressMenu = false },
            onQualityChange = onQualityChange,
            onToggleSpecialFocus = onToggleSpecialFocus
        )
    }

    // 画质选择弹窗
    if (showQualityMenu) {
        QualitySelectionDialog(
            currentQuality = room.quality,
            onConfirm = { quality ->
                onQualityChange(quality)
                showQualityMenu = false
            },
            onDismiss = { showQualityMenu = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个直播间吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun QualitySelectionDialog(
    currentQuality: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val qualityOptions = listOf(
        "original" to "原画",
        "high" to "高清",
        "medium" to "标清",
        "low" to "流畅"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择录制画质") },
        text = {
            Column {
                qualityOptions.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentQuality == value,
                            onClick = { onConfirm(value) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun LongPressMenu(
    room: LiveRoomEntity,
    onDismiss: () -> Unit,
    onQualityChange: (String) -> Unit,
    onToggleSpecialFocus: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主播卡片菜单") },
        text = {
            Column {
                // 画质设置项
                ListItem(
                    headlineContent = { Text("设置画质") },
                    supportingContent = { Text("当前: ${getQualityLabel(room.quality)}") },
                    leadingContent = {
                        Icon(Icons.Default.HighQuality, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onQualityChange(room.quality) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 特别关注开关
                ListItem(
                    headlineContent = { Text(if (room.isSpecialFocus) "取消特别关注" else "设为特别关注") },
                    supportingContent = { 
                        Text(if (room.isSpecialFocus) "该主播为特别关注，会自动启动录制" else "特别关注的主播会自动启动录制") 
                    },
                    leadingContent = {
                        Icon(
                            if (room.isSpecialFocus) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (room.isSpecialFocus) Color.Yellow else Color.Unspecified
                        )
                    },
                    modifier = Modifier.clickable { 
                        onToggleSpecialFocus()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.VideocamOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无直播间",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角 + 按钮添加直播间",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun getPlatformColor(platformId: String): Color {
    return when (platformId) {
        "bilibili" -> BilibiliPink
        "douyin" -> DouyinRed
        "douyu" -> DouyuBlue
        "huya" -> HuyaOrange
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
fun getPlatformEmoji(platformId: String): String {
    return when (platformId) {
        "bilibili" -> "B"
        "douyin" -> "抖"
        "douyu" -> "鱼"
        "huya" -> "虎"
        else -> "?"
    }
}

private fun getPlatformName(platformId: String): String {
    return when (platformId) {
        "bilibili" -> "B站"
        "douyin" -> "抖音"
        "douyu" -> "斗鱼"
        "huya" -> "虎牙"
        else -> platformId
    }
}

private fun getQualityLabel(quality: String): String {
    return when (quality) {
        "original" -> "原画"
        "high" -> "高清"
        "medium" -> "标清"
        "low" -> "流畅"
        else -> "原画"
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
