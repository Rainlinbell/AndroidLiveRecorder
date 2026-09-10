package com.liverecorder.app.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * 录制文件存储目标抽象：兼容 File 路径与 SAF URI 两种写入方式
 *
 * - FileTarget：用于应用专属目录或可转换的 SAF 路径
 * - UriTarget：用于用户通过 SAF 选择但无法转换为 File 路径的目录（如 SD 卡、USB OTG）
 */
sealed class OutputTarget {
    /** 用于日志/UI 显示的友好名称 */
    abstract val displayName: String

    /** 打开输出流。append=true 时追加写入，false 时覆盖 */
    abstract fun openOutputStream(append: Boolean): OutputStream

    /** 文件是否存在 */
    abstract fun exists(): Boolean

    /** 文件大小（字节），无法获取时返回 0 */
    abstract fun length(): Long

    /** 删除文件 */
    abstract fun delete(): Boolean

    /** File 实现 */
    data class FileTarget(val file: File) : OutputTarget() {
        override val displayName: String get() = file.absolutePath

        override fun openOutputStream(append: Boolean): OutputStream {
            file.parentFile?.mkdirs()
            return FileOutputStream(file, append)
        }

        override fun exists(): Boolean = file.exists()
        override fun length(): Long = if (file.exists()) file.length() else 0L
        override fun delete(): Boolean = file.delete()
    }

    /** SAF Uri 实现 */
    data class UriTarget(
        private val contentResolver: ContentResolver,
        val uri: Uri
    ) : OutputTarget() {
        override val displayName: String get() = uri.toString()

        override fun openOutputStream(append: Boolean): OutputStream {
            // SAF 模式: "wa" = write + truncate（追加行为依赖 DocumentFile 的实现，新文件用 "w"）
            // 已存在的文件用 "wt" 截断重写，新文件用 "w"
            val mode = if (append && exists()) "wa" else "w"
            return contentResolver.openOutputStream(uri, mode)
                ?: throw IOException("无法打开 SAF 输出流: $uri")
        }

        override fun exists(): Boolean = try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (_: Exception) {
            false
        }

        override fun length(): Long = try {
            val cursor = contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)
            cursor?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }

        override fun delete(): Boolean = try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 录制文件条目（用于历史列表 UI）
 */
data class RecordFileEntry(
    val file: File?,
    val uri: Uri?,
    val name: String,
    val platform: String,
    val roomId: String,
    val timestamp: Long,
    val size: Long,
    val displayName: String
)

/**
 * 存储管理器：根据用户配置提供 OutputTarget
 *
 * 优先级：
 * 1. 若用户通过 SAF 选择过目录且 URI 权限有效 → 使用 SAF/DocumentFile API
 * 2. 若 storage_path 是合法的 File 路径 → 使用 File API
 * 3. 否则 → 回退到应用专属外部目录（总是可写）
 */
class OutputStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val contentResolver = context.contentResolver

    /** 默认应用专属外部目录（不会变化，可缓存） */
    private val defaultBasePath: String =
        (context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath) + "/LiveRecorder"

    /** 每次访问时重新读取 prefs（用户更新路径后立即可见，无需重建实例） */
    private val treeUri: Uri?
        get() = runCatching {
            prefs.getString(KEY_STORAGE_URI, null)?.let { Uri.parse(it) }
        }.getOrNull()

    private val fileBasePath: String?
        get() = prefs.getString(KEY_STORAGE_PATH, null)?.takeIf { isValidFilePath(it) }

    /** UI 显示的存储路径描述 */
    val displayStoragePath: String
        get() {
            val uri = treeUri
            if (uri != null && isTreeUriPersisted(uri)) {
                return "[SAF] ${uri.lastPathSegment ?: uri.toString()}"
            }
            return fileBasePath ?: defaultBasePath
        }

    /**
     * 为指定相对路径创建 OutputTarget
     * @param relativePath 形如 "bilibili/主播名_20260908_part1.flv"
     */
    suspend fun createOutputTarget(relativePath: String): OutputTarget = withContext(Dispatchers.IO) {
        // 1. SAF 模式（优先，因为用户显式选择了目录）
        val uri = treeUri
        if (uri != null && isTreeUriPersisted(uri)) {
            try {
                return@withContext createSafTarget(uri, relativePath)
            } catch (e: Exception) {
                Log.e(TAG, "SAF 创建输出目标失败，回退到 File 模式", e)
            }
        }

        // 2. File 路径模式
        val fileBase = fileBasePath
        if (fileBase != null) {
            val file = File(fileBase, relativePath)
            file.parentFile?.mkdirs()
            if (file.parentFile?.canWrite() == true) {
                return@withContext OutputTarget.FileTarget(file)
            }
            Log.w(TAG, "File 路径不可写: $fileBase, 回退到默认目录")
        }

        // 3. 默认应用专属目录
        val file = File(defaultBasePath, relativePath)
        file.parentFile?.mkdirs()
        OutputTarget.FileTarget(file)
    }

    /**
     * 扫描录制文件列表
     * @param extensions 文件扩展名集合（如 setOf("flv", "mp4", "ts")）
     */
    suspend fun listRecordFiles(extensions: Set<String>): List<RecordFileEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RecordFileEntry>()

        // SAF 模式
        val uri = treeUri
        if (uri != null && isTreeUriPersisted(uri)) {
            try {
                result.addAll(listSafFiles(uri, extensions))
            } catch (e: Exception) {
                Log.e(TAG, "SAF 扫描文件失败", e)
            }
        }

        // File 模式（无论是用户配置还是默认）
        val basePath = fileBasePath ?: defaultBasePath
        val baseDir = File(basePath)
        if (baseDir.exists()) {
            baseDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in extensions }
                .forEach { file ->
                    val platform = file.parentFile?.name ?: "unknown"
                    val roomId = file.name.substringBefore("_")
                    result.add(
                        RecordFileEntry(
                            file = file,
                            uri = null,
                            name = file.name,
                            platform = platform,
                            roomId = roomId,
                            timestamp = file.lastModified(),
                            size = file.length(),
                            displayName = file.absolutePath
                        )
                    )
                }
        }

        result.sortedByDescending { it.timestamp }
    }

    /**
     * 获取当前可写入的根目录（仅用于日志/显示）
     */
    fun getCurrentBasePathLog(): String {
        val uri = treeUri
        return when {
            uri != null && isTreeUriPersisted(uri) -> "SAF tree: $uri"
            fileBasePath != null -> "File: $fileBasePath"
            else -> "Default: $defaultBasePath"
        }
    }

    // ---- SAF 实现 ----

    private fun isTreeUriPersisted(uri: Uri): Boolean {
        return try {
            // 验证 URI 权限仍然存在
            val perms = contentResolver.persistedUriPermissions
            perms.any { it.uri == uri && it.isWritePermission }
        } catch (_: Exception) {
            false
        }
    }

    private fun createSafTarget(treeUri: Uri, relativePath: String): OutputTarget.UriTarget {
        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            throw IOException("无效的相对路径: $relativePath")
        }
        val fileName = segments.last()
        val subdirs = segments.dropLast(1)

        var current = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("无效的 SAF tree URI: $treeUri")

        // 创建/查找子目录
        for (dir in subdirs) {
            current = current.findFile(dir) ?: current.createDirectory(dir)
                ?: throw IOException("无法创建 SAF 目录: $dir")
        }

        // 创建/查找文件
        val existing = current.findFile(fileName)
        val targetDoc = existing ?: current.createFile("video/x-flv", fileName)
            ?: throw IOException("无法创建 SAF 文件: $fileName")

        return OutputTarget.UriTarget(contentResolver, targetDoc.uri)
    }

    private fun listSafFiles(treeUri: Uri, extensions: Set<String>): List<RecordFileEntry> {
        val result = mutableListOf<RecordFileEntry>()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return result

        for (platformDir in root.listFiles()) {
            if (!platformDir.isDirectory) continue
            val platform = platformDir.name ?: "unknown"

            for (fileDoc in platformDir.listFiles()) {
                if (!fileDoc.isFile) continue
                val name = fileDoc.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in extensions) continue

                val roomId = name.substringBefore("_")
                result.add(
                    RecordFileEntry(
                        file = null,
                        uri = fileDoc.uri,
                        name = name,
                        platform = platform,
                        roomId = roomId,
                        timestamp = fileDoc.lastModified(),
                        size = fileDoc.length(),
                        displayName = name
                    )
                )
            }
        }
        return result
    }

    companion object {
        private const val TAG = "OutputStorageManager"
        private const val KEY_STORAGE_URI = "storage_path_uri"
        private const val KEY_STORAGE_PATH = "storage_path"

        /** 判断字符串是否为合法的 File 路径（非 SAF URI 的 path 部分） */
        private fun isValidFilePath(path: String): Boolean {
            if (path.startsWith("/tree/")) return false
            if (path.startsWith("content://")) return false
            if (path.startsWith("/document/")) return false
            // 必须是绝对路径
            return path.startsWith("/") && !path.contains(":")
        }
    }
}
