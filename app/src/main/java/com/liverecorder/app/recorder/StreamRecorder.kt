package com.liverecorder.app.recorder

import android.util.Log
import com.liverecorder.app.storage.OutputTarget
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

/**
 * 基于 OkHttp 的直播流录制器
 * 直接下载直播流（FLV/TS）到本地文件，无需 FFmpeg
 *
 * 写入目标通过 [OutputTarget] 抽象，兼容 File 路径与 SAF URI。
 */
class StreamRecorder(
    private val okHttpClient: OkHttpClient
) {
    @Volatile
    private var isRecording = false
    @Volatile
    private var isPaused = false
    @Volatile
    private var currentCall: okhttp3.Call? = null
    private var streamUrl: String? = null
    private var outputTarget: OutputTarget? = null
    private var callback: RecordCallback? = null

    @Volatile
    private var recordThread: Thread? = null

    /** 持有 outputStream 引用，便于超时后强制关闭 */
    @Volatile
    private var activeOutputStream: OutputStream? = null
    @Volatile
    private var activeResponse: okhttp3.Response? = null

    fun startRecord(
        streamUrl: String,
        outputTarget: OutputTarget,
        callback: RecordCallback
    ) {
        if (isRecording) {
            callback.onError("已在录制中")
            return
        }

        this.streamUrl = streamUrl
        this.outputTarget = outputTarget
        this.callback = callback

        isRecording = true
        isPaused = false
        callback.onStart()

        doRecord(streamUrl, outputTarget, callback)
    }

    private fun doRecord(
        streamUrl: String,
        outputTarget: OutputTarget,
        callback: RecordCallback
    ) {
        Thread {
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            var response: okhttp3.Response? = null
            var totalBytesRead = 0L
            try {
                Log.d(TAG, "开始连接直播流: ${streamUrl.take(100)}..., 目标: ${outputTarget.displayName}")
                // 根据流地址域名设置正确的 Referer
                val referer = when {
                    streamUrl.contains("bilivideo.com") -> "https://live.bilibili.com/"
                    streamUrl.contains("douyucdn") || streamUrl.contains("douyu") -> "https://www.douyu.com/"
                    streamUrl.contains("huya") -> "https://www.huya.com/"
                    streamUrl.contains("douyin") || streamUrl.contains("bytedance") -> "https://live.douyin.com/"
                    else -> streamUrl
                }
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Origin", referer.trimEnd('/'))
                    .build()

                val call = okHttpClient.newCall(request)
                currentCall = call

                response = call.execute()
                if (!response.isSuccessful) {
                    isRecording = false
                    Log.e(TAG, "HTTP错误: ${response.code}")
                    callback.onError("HTTP错误: ${response.code}")
                    return@Thread
                }

                val body = response.body
                if (body == null) {
                    isRecording = false
                    Log.e(TAG, "响应体为空")
                    callback.onError("响应体为空")
                    return@Thread
                }

                Log.d(TAG, "直播流连接成功，开始写入: ${outputTarget.displayName}")
                inputStream = body.byteStream()
                // 新录制段使用覆盖模式（append=false）
                outputStream = outputTarget.openOutputStream(append = false)
                activeOutputStream = outputStream
                activeResponse = response
                val buffer = ByteArray(8192)
                var bytesRead: Int = 0

                // 先检查状态再 read，避免 read 阻塞时无法响应暂停/停止
                while (isRecording && !isPaused && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    callback.onProgress(totalBytesRead)
                }

                Log.d(TAG, "录制循环结束: isPaused=$isPaused, isRecording=$isRecording, bytes=$totalBytesRead")

                if (isPaused) {
                    // 暂停状态，文件在 finally 中关闭
                    Log.d(TAG, "暂停录制，文件将在 finally 中保存")
                    return@Thread
                }

                isRecording = false
                currentCall = null

                if (totalBytesRead > 0) {
                    callback.onComplete(outputTarget)
                } else {
                    callback.onCancel()
                }
            } catch (e: Exception) {
                Log.d(TAG, "录制异常捕获: ${e.javaClass.simpleName}: ${e.message}, isPaused=$isPaused, isRecording=$isRecording, bytes=$totalBytesRead")
                if (isPaused) {
                    // 暂停导致的取消，文件在 finally 中关闭保存
                    Log.d(TAG, "暂停取消，文件将在 finally 中保存")
                    return@Thread
                }
                isRecording = false
                currentCall = null
                // 如果已有数据写入或文件存在且有内容，保存文件
                val fileHasData = totalBytesRead > 0 || (outputTarget.exists() && outputTarget.length() > 0)
                if (fileHasData) {
                    Log.d(TAG, "录制停止，保存文件: ${outputTarget.displayName} (bytes=$totalBytesRead, size=${outputTarget.length()})")
                    callback.onComplete(outputTarget)
                } else if (e is java.io.InterruptedIOException) {
                    Log.d(TAG, "录制取消（无数据）")
                    callback.onCancel()
                } else {
                    Log.e(TAG, "录制异常", e)
                    callback.onError("录制异常: ${e.message}")
                }
            } finally {
                // 确保所有路径都正确关闭流，防止数据丢失
                try {
                    outputStream?.flush()
                    outputStream?.close()
                } catch (_: Exception) {}
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    response?.close()
                } catch (_: Exception) {}
                activeOutputStream = null
                activeResponse = null
                Log.d(TAG, "流已关闭, 文件大小: ${outputTarget.length()} bytes, 存在: ${outputTarget.exists()}")
            }
        }.apply {
            name = "StreamRecorder-Thread"
            recordThread = this
            start()
        }
    }

    /** 等待录制线程结束（最多等待指定毫秒），超时后强制关闭流以确保文件落盘 */
    fun waitForThreadStop(timeoutMs: Long = 3000) {
        val thread = recordThread ?: return
        try {
            thread.join(timeoutMs)
        } catch (_: Exception) {}

        // 如果线程仍未退出（如 read 仍阻塞），强制关闭流触发异常并刷盘
        if (thread.isAlive) {
            Log.w(TAG, "录制线程未在 ${timeoutMs}ms 内退出，强制关闭流以保存文件")
            try {
                activeOutputStream?.let {
                    try { it.flush() } catch (_: Exception) {}
                    try { it.close() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            try {
                activeResponse?.close()
            } catch (_: Exception) {}
            // 再次取消 call（双重保险）
            currentCall?.cancel()
            // 短暂等待线程响应
            try {
                thread.join(500)
            } catch (_: Exception) {}
            if (thread.isAlive) {
                Log.e(TAG, "强制关闭后线程仍未退出，可能存在资源泄漏")
                thread.interrupt()
            }
        }
        activeOutputStream = null
        activeResponse = null
        recordThread = null
    }

    fun stopRecord() {
        isRecording = false
        isPaused = false
        currentCall?.cancel()
        currentCall = null
        // 立即刷盘，防止线程未及时退出时数据停留在 buffer
        try {
            activeOutputStream?.flush()
        } catch (_: Exception) {}
        Log.d(TAG, "stopRecord: isRecording=false, call 已 cancel")
    }

    fun pauseRecord() {
        if (isRecording && !isPaused) {
            isPaused = true
            isRecording = false
            currentCall?.cancel()
            currentCall = null
            // 立即刷盘，确保已读取的数据写入磁盘
            try {
                activeOutputStream?.flush()
            } catch (_: Exception) {}
            Log.d(TAG, "pauseRecord: isPaused=true, call 已 cancel, 等待线程 flush+close")
        }
    }

    /**
     * 恢复录制（使用新的流地址和输出目标，开始新的录制段）
     */
    fun resumeRecord(newUrl: String, newTarget: OutputTarget, newCallback: RecordCallback) {
        if (!isPaused) return

        this.streamUrl = newUrl
        this.outputTarget = newTarget
        this.callback = newCallback

        isRecording = true
        isPaused = false
        newCallback.onStart()
        Log.d(TAG, "恢复录制: ${newTarget.displayName}")

        doRecord(newUrl, newTarget, newCallback)
    }

    fun isRecording(): Boolean = isRecording
    fun isPaused(): Boolean = isPaused

    interface RecordCallback {
        fun onStart()
        fun onComplete(outputTarget: OutputTarget)
        fun onCancel()
        fun onError(message: String)
        fun onProgress(totalBytes: Long) {}
    }

    companion object {
        private const val TAG = "StreamRecorder"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
