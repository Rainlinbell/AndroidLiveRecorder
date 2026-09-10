package com.liverecorder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.liverecorder.app.platform.PlatformAdapter
import com.liverecorder.app.ui.MainViewModel
import com.liverecorder.app.ui.screens.*
import com.liverecorder.app.ui.theme.LiveRecorderTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "需要存储和通知权限才能正常工作", Toast.LENGTH_LONG).show()
        }
    }

    /** 目录选择器（用于长按修改存储路径） */
    private val dirPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 委托给 ViewModel 处理权限持久化与 prefs 写入
            activeViewModel?.updateStoragePath(uri)
            Toast.makeText(this, "存储路径已更新", Toast.LENGTH_SHORT).show()
        }
    }

    /** 当前活跃的 ViewModel 引用（用于存储路径更新） */
    private var activeViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            LiveRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = hiltViewModel()
                    activeViewModel = viewModel
                    val navController = rememberNavController()
                    val rooms by viewModel.rooms.collectAsState()
                    val uiState by viewModel.uiState.collectAsState()
                    val recordFiles by viewModel.recordFiles.collectAsState()
                    val storagePath by viewModel.storagePath.collectAsState()
                    var qrLoginAdapter by remember { mutableStateOf<PlatformAdapter?>(null) }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                rooms = rooms,
                                isServiceRunning = uiState.isServiceRunning,
                                isRefreshing = uiState.isRefreshing,
                                onToggleService = { viewModel.toggleService() },
                                onAddRoom = { navController.navigate("add_room") },
                                onSettings = { navController.navigate("settings") },
                                onToggleRoom = { id, enabled -> viewModel.toggleRoom(id, enabled) },
                                onDeleteRoom = { id -> viewModel.deleteRoom(id) },
                                onPauseRoom = { id -> viewModel.pauseRoom(id) },
                                onResumeRoom = { id -> viewModel.resumeRoom(id) },
                                onUpdateQuality = { id, quality -> viewModel.updateRoomQuality(id, quality) },
                                onToggleSpecialFocus = { id -> viewModel.toggleSpecialFocus(id) },
                                onRefresh = { viewModel.loadRecordFiles() }
                            )
                        }

                        composable("add_room") {
                            AddRoomScreen(
                                adapters = viewModel.adapters,
                                onAddRoom = { url, _, _ ->
                                    viewModel.addRoom(url)
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                monitorInterval = uiState.monitorInterval,
                                onIntervalChange = { viewModel.updateMonitorInterval(it) },
                                isRootAvailable = uiState.isRootAvailable,
                                isRootGranted = uiState.isRootGranted,
                                onRequestRoot = { viewModel.requestRoot() },
                                storagePath = storagePath,
                                onOpenPath = { openFileManager(storagePath) },
                                onChangePath = { dirPickerLauncher.launch(null) },
                                adapters = viewModel.adapters,
                                getCookie = { platformId -> viewModel.getCookie(platformId) },
                                onClearCookie = { platformId -> viewModel.clearCookie(platformId) },
                                onShowQRLogin = { adapter -> qrLoginAdapter = adapter },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("history") {
                            RecordHistoryScreen(
                                recordFiles = recordFiles,
                                onDeleteFile = { viewModel.deleteRecordFile(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // 二维码登录弹窗
                    qrLoginAdapter?.let { adapter ->
                        QRLoginDialog(
                            adapter = adapter,
                            onCookieReceived = { cookie ->
                                viewModel.saveQRLoginCookie(adapter.platformId, cookie)
                            },
                            onOpenBrowser = { url -> openBrowser(url) },
                            onDismiss = { qrLoginAdapter = null }
                        )
                    }
                }
            }
        }
    }

    /** 打开浏览器登录平台 */
    private fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            Toast.makeText(this, "登录后Cookie将自动保存", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    /** 打开文件管理器跳转到指定目录 */
    private fun openFileManager(path: String) {
        try {
            val uri = Uri.fromFile(File(path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // 尝试打开文件管理器
            val chooser = Intent.createChooser(intent, "打开目录")
            startActivity(chooser)
        } catch (e: Exception) {
            // 如果没有文件管理器支持，尝试用 DocumentsUI
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra("android.content.extra.SHOW_ADVANCED", true)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "无法打开文件管理器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // 存储权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 引导用户开启管理所有文件权限
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }
}
