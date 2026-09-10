package com.liverecorder.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liverecorder.app.data.db.entity.LiveRoomEntity
import com.liverecorder.app.data.repository.LiveRoomRepository
import com.liverecorder.app.recorder.StreamExtractor
import com.liverecorder.app.root.RootManager
import com.liverecorder.app.service.RecorderService
import com.liverecorder.app.storage.OutputStorageManager
import com.liverecorder.app.storage.RecordFileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: LiveRoomRepository,
    private val streamExtractor: StreamExtractor,
    private val rootManager: RootManager,
    private val outputStorage: OutputStorageManager
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val rooms: StateFlow<List<LiveRoomEntity>> = repository.getAllRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordFiles = MutableStateFlow<List<RecordFileEntry>>(emptyList())

    val adapters = streamExtractor.getAllAdapters()

    /** 当前存储路径显示文本（File 路径或 [SAF] URI 描述） */
    val storagePath = MutableStateFlow(outputStorage.displayStoragePath)

    init {
        checkRootStatus()
        loadSettings()
        loadRecordFiles()
    }

    private fun loadSettings() {
        val interval = prefs.getInt("monitor_interval", 30)
        _uiState.update { it.copy(monitorInterval = interval) }
    }

    private fun checkRootStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRootAvailable = rootManager.isRooted()
            // 从 SharedPreferences 恢复 Root 授权状态
            val savedRootGranted = prefs.getBoolean("root_granted", false)
            _uiState.update {
                it.copy(
                    isRootAvailable = isRootAvailable,
                    isRootGranted = if (isRootAvailable) savedRootGranted else false
                )
            }
        }
    }

    fun toggleService() {
        val context = getApplication<Application>()
        if (_uiState.value.isServiceRunning) {
            RecorderService.stopMonitoring(context)
            _uiState.update { it.copy(isServiceRunning = false) }
        } else {
            RecorderService.startMonitoring(context)
            _uiState.update { it.copy(isServiceRunning = true) }
        }
    }

    fun addRoom(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adapter = streamExtractor.findAdapter(url)
                if (adapter == null) {
                    _uiState.update { it.copy(errorMessage = "不支持的平台链接") }
                    return@launch
                }

                val roomId = adapter.getRoomId(url)
                val existing = repository.getRoomByPlatformAndId(adapter.platformId, roomId)
                if (existing != null) {
                    _uiState.update { it.copy(errorMessage = "该直播间已添加") }
                    return@launch
                }

                // 立刻获取直播间状态
                val cookie = prefs.getString("cookie_${adapter.platformId}", null)
                val title = adapter.getRoomTitle(roomId, cookie)
                val isLive = adapter.isLive(roomId, cookie)
                val entity = LiveRoomEntity(
                    platform = adapter.platformId,
                    roomId = roomId,
                    roomUrl = url,
                    roomTitle = title,
                    enabled = true,
                    isLive = isLive,
                    lastChecked = System.currentTimeMillis(),
                    lastLive = if (isLive) System.currentTimeMillis() else 0L
                )
                repository.insert(entity)
                _uiState.update { it.copy(errorMessage = null) }
                Log.d(TAG, "添加直播间成功: ${adapter.platformName} $roomId, isLive=$isLive")
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "添加失败: ${e.message}") }
                Log.e(TAG, "添加直播间失败", e)
            }
        }
        }

    /** 刷新直播间标题 */
    fun refreshRoomTitle(roomId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val room = repository.getRoomById(roomId) ?: return@launch
                val adapter = streamExtractor.findAdapterById(room.platform) ?: return@launch
                val cookie = prefs.getString("cookie_${room.platform}", null)
                val newTitle = adapter.getRoomTitle(room.roomId, cookie)
                if (newTitle.isNotEmpty()) {
                    repository.updateRoomTitle(roomId, newTitle)
                    Log.d(TAG, "直播间标题已更新: ${room.platform} ${room.roomId} -> $newTitle")
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新直播间标题失败", e)
            }
        }
    }

    fun toggleRoom(roomId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabled(roomId, enabled)
        }
    }

    fun deleteRoom(roomId: Long) {
        viewModelScope.launch {
            repository.deleteById(roomId)
        }
    }

    fun pauseRoom(roomId: Long) {
        val context = getApplication<Application>()
        RecorderService.pauseRecording(context, roomId)
    }

    fun resumeRoom(roomId: Long) {
        val context = getApplication<Application>()
        RecorderService.resumeRecording(context, roomId)
    }

    fun updateRoomQuality(roomId: Long, quality: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateQuality(roomId, quality)
            Log.d(TAG, "更新画质: $roomId -> $quality")
        }
    }

    /** 保存平台 Cookie */
    fun saveCookie(platformId: String, cookie: String) {
        prefs.edit().putString("cookie_$platformId", cookie).apply()
        Log.d(TAG, "已保存 $platformId Cookie")
    }

    /** 获取平台 Cookie */
    fun getCookie(platformId: String): String? {
        return prefs.getString("cookie_$platformId", null)
    }

    /** 清除平台 Cookie */
    fun clearCookie(platformId: String) {
        prefs.edit().remove("cookie_$platformId").apply()
    }

    /** 保存二维码登录返回的 Cookie */
    fun saveQRLoginCookie(platformId: String, cookie: String) {
        saveCookie(platformId, cookie)
        Log.d(TAG, "二维码登录成功: $platformId")
    }

    /** 获取平台适配器实例 */
    fun getAdapter(platformId: String): com.liverecorder.app.platform.PlatformAdapter? {
        return streamExtractor.findAdapterById(platformId)
    }

    /** 切换特别关注状态 */
    fun toggleSpecialFocus(roomId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val room = repository.getRoomById(roomId)
            room?.let {
                repository.updateSpecialFocus(roomId, !room.isSpecialFocus)
                Log.d(TAG, "特别关注状态已更新: ${room.roomTitle} -> ${!room.isSpecialFocus}")
            }
        }
    }

    /** 获取特别关注的房间列表 */
    suspend fun getSpecialFocusRooms(): List<LiveRoomEntity> {
        return repository.getSpecialFocusRooms()
    }

    fun requestRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = rootManager.requestRoot()
            if (granted) {
                val packageName = getApplication<Application>().packageName
                rootManager.disableBatteryOptimization(packageName)
                // 持久化 Root 授权状态
                prefs.edit().putBoolean("root_granted", true).apply()
            }
            _uiState.update { it.copy(isRootGranted = granted) }
        }
    }

    fun updateMonitorInterval(interval: Int) {
        _uiState.update { it.copy(monitorInterval = interval) }
        prefs.edit().putInt("monitor_interval", interval).apply()
    }

    /** 更新存储路径（用户通过 SAF 目录选择器选择后调用） */
    fun updateStoragePath(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                // 持久化 URI 权限
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                // 仅保存 URI，不再写入 storage_path 字段（避免 /tree/ 路径被当作 File 路径使用）
                prefs.edit()
                    .putString("storage_path_uri", uri.toString())
                    .remove("storage_path") // 清除可能存在的非法 File 路径
                    .apply()
                // OutputStorageManager 内部已读取最新 prefs，重新构建后通过 displayStoragePath 反映
                storagePath.value = outputStorage.displayStoragePath
                Log.d(TAG, "存储路径已更新 (SAF): $uri")
                loadRecordFiles()
            } catch (e: Exception) {
                Log.e(TAG, "更新存储路径失败", e)
                _uiState.update { it.copy(errorMessage = "更新存储路径失败: ${e.message}") }
            }
        }
    }

    /** 从 SharedPreferences 重新加载存储路径（目录选择器返回后调用） */
    fun refreshStoragePath() {
        storagePath.value = outputStorage.displayStoragePath
        loadRecordFiles()
        Log.d(TAG, "存储路径已刷新: ${storagePath.value}")
    }

    fun loadRecordFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = outputStorage.listRecordFiles(setOf("flv", "mp4", "ts"))
            recordFiles.value = files
            Log.d(TAG, "扫描录制文件完成: ${files.size} 个")
        }
    }

    fun deleteRecordFile(recordFile: RecordFileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = when {
                recordFile.file != null -> recordFile.file.delete()
                recordFile.uri != null -> {
                    try {
                        val resolver = getApplication<Application>().contentResolver
                        android.provider.DocumentsContract.deleteDocument(resolver, recordFile.uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "SAF 删除失败: ${recordFile.uri}", e)
                        false
                    }
                }
                else -> false
            }
            Log.d(TAG, "删除录制文件: ${recordFile.displayName}, 成功=$deleted")
            loadRecordFiles()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

data class MainUiState(
    val isServiceRunning: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isRootGranted: Boolean = false,
    val monitorInterval: Int = 30,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)
