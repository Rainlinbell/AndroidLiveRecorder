package com.liverecorder.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.liverecorder.app.platform.LoginStatus
import com.liverecorder.app.platform.PlatformAdapter
import com.liverecorder.app.platform.QRCodeData
import com.liverecorder.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 二维码登录弹窗
 */
@Composable
fun QRLoginDialog(
    adapter: PlatformAdapter,
    onCookieReceived: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var qrData by remember { mutableStateOf<QRCodeData?>(null) }
    var loginStatus by remember { mutableStateOf<LoginStatus>(LoginStatus.Waiting) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 获取二维码
    LaunchedEffect(Unit) {
        try {
            qrData = adapter.getLoginQRCode()
            if (qrData == null) {
                errorMsg = "无法获取二维码，请使用浏览器登录"
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = "获取二维码失败: ${e.message}"
            isLoading = false
        }
    }

    // 轮询扫码状态
    LaunchedEffect(qrData) {
        val data = qrData ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val status = adapter.checkQRCodeStatus(data.key)
            loginStatus = status
            when (status) {
                is LoginStatus.Confirmed -> {
                    onCookieReceived(status.cookie)
                    delay(1000)
                    onDismiss()
                    return@LaunchedEffect
                }
                is LoginStatus.Expired -> {
                    // 停止轮询，等待用户刷新
                    return@LaunchedEffect
                }
                is LoginStatus.Error -> {
                    // 继续轮询
                }
                else -> { /* continue polling */ }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "${adapter.platformName} 扫码登录",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("正在获取二维码...")
                } else if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onOpenBrowser(adapter.loginUrl)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("使用浏览器登录")
                    }
                } else {
                    qrData?.let { data ->
                        // 二维码图片
                        QRCodeImage(
                            content = data.url,
                            modifier = Modifier.size(220.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 状态显示
                        when (loginStatus) {
                            is LoginStatus.Waiting -> {
                                Text(
                                    text = "请使用 ${adapter.platformName} App 扫码登录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            is LoginStatus.Scanned -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = LiveOrange
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "已扫码，请在手机上确认",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LiveOrange,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            is LoginStatus.Confirmed -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = LiveGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "登录成功！",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LiveGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is LoginStatus.Expired -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "二维码已过期",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                qrData = adapter.getLoginQRCode()
                                                loginStatus = LoginStatus.Waiting
                                                isLoading = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("刷新二维码")
                                    }
                                }
                            }
                            is LoginStatus.Error -> {
                                Text(
                                    text = "状态查询出错",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 使用 ZXing 生成并绘制二维码
 */
@Composable
fun QRCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    foregroundColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val bitmap = remember(content) {
        generateQRBitmap(content, 512)
    }

    bitmap?.let {
        val imageBitmap = it.asImageBitmap()
        Canvas(modifier = modifier) {
            drawImage(imageBitmap, topLeft = Offset.Zero)
        }
    } ?: Box(
        modifier = modifier.background(backgroundColor)
    )
}

private fun generateQRBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y))
                        android.graphics.Color.BLACK
                    else
                        android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
