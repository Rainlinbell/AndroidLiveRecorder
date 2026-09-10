package com.liverecorder.app.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root权限管理器
 */
class RootManager {

    /**
     * 检测设备是否已Root
     */
    fun isRooted(): Boolean {
        return try {
            // 检查su命令是否存在
            val paths = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/data/local/su",
                "/data/local/bin/su",
                "/data/local/xbin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/vendor/bin/su"
            )
            paths.any { java.io.File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求并验证Root权限
     */
    fun requestRoot(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su -c id")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result?.contains("uid=0") == true
        } catch (e: Exception) {
            Log.e(TAG, "请求Root权限失败", e)
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * 执行Root命令
     */
    fun runRootCommand(command: String): String? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su -c '$command'")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val error = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                error.appendLine(line)
            }

            process.waitFor()

            if (error.isNotEmpty()) {
                Log.w(TAG, "命令错误输出: $error")
            }

            output.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "执行Root命令失败: $command", e)
            null
        } finally {
            process?.destroy()
        }
    }

    /**
     * 以Root方式启动服务保活
     */
    fun startServiceWithRoot(packageName: String, serviceName: String): Boolean {
        return try {
            val command = "am startservice -n $packageName/$serviceName"
            val result = runRootCommand(command)
            result != null
        } catch (e: Exception) {
            Log.e(TAG, "Root启动服务失败", e)
            false
        }
    }

    /**
     * 禁用电池优化（需要Root）
     */
    fun disableBatteryOptimization(packageName: String): Boolean {
        return try {
            runRootCommand("dumpsys deviceidle whitelist +$packageName")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 设置应用为系统级（防止被杀）
     */
    fun moveToSystem(packageName: String): Boolean {
        return try {
            runRootCommand("mount -o rw,remount /system")
            runRootCommand("cp /data/app/$packageName*.apk /system/priv-app/")
            runRootCommand("chmod 644 /system/priv-app/$packageName*.apk")
            runRootCommand("mount -o ro,remount /system")
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "RootManager"
    }
}
