package com.liverecorder.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_rooms")
data class LiveRoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val roomId: String,
    val roomUrl: String,
    val roomTitle: String = "",
    val enabled: Boolean = true,
    val isLive: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val customInterval: Int? = null,
    /** 录制画质: original=原画, high=高清, medium=标清, low=流畅 */
    val quality: String = "original",
    /** 特别关注：即使服务未运行也会尝试录制 */
    val isSpecialFocus: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChecked: Long = 0L,
    val lastLive: Long = 0L
)
