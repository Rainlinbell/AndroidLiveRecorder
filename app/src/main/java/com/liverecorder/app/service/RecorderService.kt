package com.liverecorder.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.liverecorder.app.LiveRecorderApp
import com.liverecorder.app.MainActivity
import com.liverecorder.app.R
import com.liverecorder.app.data.db.entity.LiveRoomEntity
import com.liverecorder.app.data.repository.LiveRoomRepository
import com.liverecorder.app.recorder.StreamRecorder
import com.liverecorder.app.recorder.StreamExtractor
import com.liverecorder.app.storage.OutputStorageManager
import com.liverecorder.app.storage.OutputTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RecorderService : Service() {

    @Inject
    lateinit var repository: LiveRoomRepository

    @Inject
    lateinit var streamExtractor: StreamExtractor

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var outputStorage: OutputStorageManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recorders = mutableMapOf<Long, StreamRecorder>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isMonitoring = false
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopAllRecordersAndSave() // 同时停止所有录制，保存文件
            }
            ACTION_STOP_ALL -> stopAllRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopMonitoring()
        // 停止所有录制并同步更新DB（使用 runBlocking 确保在 scope 取消前完成）
        recorders.values.forEach { it.stopRecord() }
        // 等待所有录制线程完成文件保存
        recorders.values.forEach { it.waitForThreadStop(3000) }
        recorders.clear()
        try {
            kotlinx.coroutines.runBlocking {
                val rooms = repository.getEnabledRooms()
                rooms.forEach { room ->
                    if (room.isRecording || room.isPaused) {
                        repository.updateRecordingStatus(room.id, false)
                        repository.updatePausedStatus(room.id, false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy DB清理失败", e)
        }
        releaseWakeLock()
        scope.cancel()
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, LiveRecorderApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiveRecorder::MonitorWakeLock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L) // 24小时
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.d(TAG, "开始监控直播间")

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    checkAllRooms()
                } catch (e: Exception) {
                    Log.e(TAG, "监控循环出错", e)
                }
                // 从 SharedPreferences 读取检测间隔
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val intervalSec = prefs.getInt("monitor_interval", 30)
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        Log.d(TAG, "停止监控")
    }

    private suspend fun checkAllRooms() {
        val enabledRooms = repository.getEnabledRooms()
        Log.d(TAG, "检查 ${enabledRooms.size} 个直播间")

        enabledRooms.forEach { room ->
            try {
                checkRoom(room)
            } catch (e: Exception) {
                Log.e(TAG, "检查房间 ${room.roomId} 失败", e)
            }
        }
    }

    private suspend fun checkRoom(room: LiveRoomEntity) {
        val adapter = streamExtractor.findAdapterById(room.platform) ?: return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val cookie = prefs.getString("cookie_${room.platform}", null)

        // 检测是否开播
        Log.d(TAG, "检测房间: ${room.platform} ${room.roomId}, enabled=${room.enabled}, isRecording=${room.isRecording}, isPaused=${room.isPaused}, isSpecialFocus=${room.isSpecialFocus}")
        val isLive = adapter.isLive(room.roomId, cookie)
        repository.updateLiveStatus(room.id, isLive, System.currentTimeMillis())
        Log.d(TAG, "房间状态: ${room.platform} ${room.roomId}, isLive=$isLive")

        if (isLive && !room.isLive) {
            repository.updateLastLive(room.id, System.currentTimeMillis())
            Log.d(TAG, "${room.platform} ${room.roomId} 开播了")
        }

        if (isLive && !room.isRecording && !room.isPaused) {
            // 正在直播且未录制且未暂停，开始录制
            Log.d(TAG, "准备开始录制: ${room.platform} ${room.roomId}")
            startRecording(room, adapter, cookie)
        } else if (!isLive && room.isRecording) {
            // 已下播但还在录制（包括暂停状态），停止录制
            Log.d(TAG, "停止录制（已下播）: ${room.platform} ${room.roomId}")
            stopRecording(room.id)
        } else {
            Log.d(TAG, "跳过录制: isLive=$isLive, isRecording=${room.isRecording}, isPaused=${room.isPaused}")
        }
    }

    private suspend fun startRecording(room: LiveRoomEntity, adapter: com.liverecorder.app.platform.PlatformAdapter, cookie: String?) {
        Log.d(TAG, "获取直播流地址: ${room.platform} ${room.roomId}, quality=${room.quality}")
        val streamUrl = adapter.getStreamUrl(room.roomId, room.quality, cookie)
        if (streamUrl.isNullOrEmpty()) {
            Log.w(TAG, "获取直播流地址失败: ${room.platform} ${room.roomId}")
            return
        }
        Log.d(TAG, "直播流地址获取成功: ${streamUrl.take(100)}...")
        Log.d(TAG, "存储配置: ${outputStorage.getCurrentBasePathLog()}")

        val relativePath = generateRelativeOutputPath(room)
        val target = try {
            outputStorage.createOutputTarget(relativePath)
        } catch (e: Exception) {
            Log.e(TAG, "创建输出目标失败: ${room.platform} ${room.roomId}", e)
            return
        }
        Log.d(TAG, "输出目标: ${target.displayName}")

        val recorder = StreamRecorder(okHttpClient)
        recorders[room.id] = recorder

        repository.updateRecordingStatus(room.id, true)
        Log.d(TAG, "启动录制: ${room.platform} ${room.roomId} -> ${target.displayName}")
        recorder.startRecord(streamUrl, target, createCallback(room.id))
    }

    /** 生成输出文件相对路径：{platform}/{主播名}/{yyyyMMdd}[_partN].flv */
    private fun generateRelativeOutputPath(room: LiveRoomEntity, segment: Int = 0): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val streamerName = room.roomTitle.ifEmpty { room.roomId }
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(50)
        val segmentSuffix = if (segment > 0) "_part$segment" else ""
        return "${room.platform}/${streamerName}/${dateStr}${segmentSuffix}.flv"
    }

    /** 创建录制回调 */
    private fun createCallback(roomId: Long): StreamRecorder.RecordCallback {
        return object : StreamRecorder.RecordCallback {
            override fun onStart() {
                Log.d(TAG, "开始录制: $roomId")
            }
            override fun onComplete(outputTarget: OutputTarget) {
                Log.d(TAG, "录制完成: ${outputTarget.displayName}")
                scope.launch {
                    repository.updateRecordingStatus(roomId, false)
                    repository.updatePausedStatus(roomId, false)
                    recorders.remove(roomId)
                }
            }
            override fun onCancel() {
                Log.d(TAG, "录制取消: $roomId")
                scope.launch {
                    repository.updateRecordingStatus(roomId, false)
                    repository.updatePausedStatus(roomId, false)
                    recorders.remove(roomId)
                }
            }
            override fun onError(message: String) {
                Log.e(TAG, "录制错误: $message")
                scope.launch {
                    repository.updateRecordingStatus(roomId, false)
                    repository.updatePausedStatus(roomId, false)
                    recorders.remove(roomId)
                }
            }
        }
    }

    private suspend fun stopRecording(roomId: Long) {
        val recorder = recorders[roomId]
        if (recorder != null) {
            recorder.stopRecord()
            recorder.waitForThreadStop(5000)
            Log.d(TAG, "停止录制线程结束: roomId=$roomId, isRecording=${recorder.isRecording()}")
        } else {
            Log.w(TAG, "停止录制时 recorder 不存在: roomId=$roomId")
        }
        recorders.remove(roomId)
        repository.updateRecordingStatus(roomId, false)
        repository.updatePausedStatus(roomId, false)
        Log.d(TAG, "停止录制完成: $roomId")
    }

    /**
     * 暂停录制：保存已录制的内容到文件，结束当前录制段
     */
    suspend fun pauseRecording(roomId: Long) {
        val recorder = recorders[roomId] ?: run {
            Log.w(TAG, "暂停录制失败：recorder 不存在 roomId=$roomId")
            return
        }
        Log.d(TAG, "暂停录制: $roomId")
        // 暂停录制器（保存文件、停止流下载）
        recorder.pauseRecord()
        // 等待录制线程完成文件保存（延长至 5s 确保大文件 flush 完成）
        recorder.waitForThreadStop(5000)
        // 从 map 中移除（录制段已结束）
        recorders.remove(roomId)
        // DB: isRecording 保持 true，isPaused 设为 true
        repository.updatePausedStatus(roomId, true)
        Log.d(TAG, "暂停录制完成，文件已保存: $roomId, isRecording=${recorder.isRecording()}, isPaused=${recorder.isPaused()}")
    }

    /**
     * 恢复录制：获取新流地址，开始新的录制段
     */
    suspend fun resumeRecording(roomId: Long) {
        val recorder = recorders[roomId]
        if (recorder == null || !recorder.isPaused()) {
            // 录制器不存在或不在暂停状态，仅更新 DB
            repository.updatePausedStatus(roomId, false)
            return
        }

        val room = repository.getRoomById(roomId) ?: return
        val adapter = streamExtractor.findAdapterById(room.platform) ?: return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val cookie = prefs.getString("cookie_${room.platform}", null)

        // 获取新的直播流地址
        val streamUrl = adapter.getStreamUrl(room.roomId, room.quality, cookie)
        if (streamUrl.isNullOrEmpty()) {
            Log.w(TAG, "恢复录制失败，无法获取直播流: ${room.roomId}")
            return
        }

        // 生成新的输出目标（分段命名）
        val segment = prefs.getInt("record_segment_$roomId", 0) + 1
        prefs.edit().putInt("record_segment_$roomId", segment).apply()
        val relativePath = generateRelativeOutputPath(room, segment)
        val newTarget = try {
            outputStorage.createOutputTarget(relativePath)
        } catch (e: Exception) {
            Log.e(TAG, "恢复录制时创建输出目标失败: $roomId", e)
            return
        }

        // 用新参数恢复录制
        recorder.resumeRecord(streamUrl, newTarget, createCallback(roomId))
        repository.updatePausedStatus(roomId, false)
        Log.d(TAG, "恢复录制: $roomId -> ${newTarget.displayName}")
    }

    /**
     * 停止所有录制器并保存文件（同步等待录制线程结束）
     */
    private fun stopAllRecordersAndSave() {
        Log.d(TAG, "停止所有录制器并保存文件 (${recorders.size} 个)")
        // 先停止所有录制器
        recorders.forEach { (roomId, recorder) ->
            recorder.stopRecord()
            Log.d(TAG, "已请求停止录制: roomId=$roomId")
        }
        // 等待所有录制线程完成文件保存
        recorders.forEach { (roomId, recorder) ->
            recorder.waitForThreadStop(5000)
            Log.d(TAG, "录制线程已结束: roomId=$roomId, isRecording=${recorder.isRecording()}, isPaused=${recorder.isPaused()}")
        }
        recorders.clear()
        // 同步更新DB（确保在 scope 取消前完成）
        try {
            kotlinx.coroutines.runBlocking {
                val rooms = repository.getEnabledRooms()
                rooms.forEach { room ->
                    if (room.isRecording || room.isPaused) {
                        repository.updateRecordingStatus(room.id, false)
                        repository.updatePausedStatus(room.id, false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录制DB更新失败", e)
        }
        Log.d(TAG, "所有录制器已停止，文件已保存")
    }

    private fun stopAllRecording() {
        recorders.values.forEach { it.stopRecord() }
        recorders.clear()
        scope.launch {
            val rooms = repository.getEnabledRooms()
            rooms.forEach { room ->
                if (room.isRecording) {
                    repository.updateRecordingStatus(room.id, false)
                }
                if (room.isPaused) {
                    repository.updatePausedStatus(room.id, false)
                }
            }
        }
    }

    companion object {
        private const val TAG = "RecorderService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "com.liverecorder.app.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.liverecorder.app.STOP_MONITORING"
        const val ACTION_STOP_ALL = "com.liverecorder.app.STOP_ALL"
        private const val MONITOR_INTERVAL_MS = 30000L // 默认30秒，可通过 SharedPreferences 配置

        /** 服务实例引用，用于 ViewModel 调用暂停/恢复 */
        @Volatile
        private var instance: RecorderService? = null

        fun startMonitoring(context: android.content.Context) {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startForegroundService(intent)
        }

        fun stopMonitoring(context: android.content.Context) {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun stopAll(context: android.content.Context) {
            val intent = Intent(context, RecorderService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            context.startService(intent)
        }

        /** 暂停录制（保存已录制内容） */
        fun pauseRecording(context: android.content.Context, roomId: Long) {
            val svc = instance ?: return
            svc.scope.launch {
                svc.pauseRecording(roomId)
            }
        }

        /** 恢复录制（开始新录制段） */
        fun resumeRecording(context: android.content.Context, roomId: Long) {
            val svc = instance ?: return
            svc.scope.launch {
                svc.resumeRecording(roomId)
            }
        }
    }
}
