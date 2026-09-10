package com.liverecorder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.liverecorder.app.service.RecorderService

/**
 * 开机自启动接收器
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "设备启动完成，启动录制服务")
            RecorderService.startMonitoring(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
