package com.liverecorder.app.data.repository

import com.liverecorder.app.data.db.LiveRoomDao
import com.liverecorder.app.data.db.entity.LiveRoomEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveRoomRepository @Inject constructor(
    private val liveRoomDao: LiveRoomDao
) {
    fun getAllRooms(): Flow<List<LiveRoomEntity>> = liveRoomDao.getAllRooms()

    suspend fun getEnabledRooms(): List<LiveRoomEntity> = liveRoomDao.getEnabledRooms()

    suspend fun getRoomById(id: Long): LiveRoomEntity? = liveRoomDao.getRoomById(id)

    suspend fun getRoomByPlatformAndId(platform: String, roomId: String): LiveRoomEntity? =
        liveRoomDao.getRoomByPlatformAndId(platform, roomId)

    suspend fun insert(room: LiveRoomEntity): Long = liveRoomDao.insert(room)

    suspend fun update(room: LiveRoomEntity) = liveRoomDao.update(room)

    suspend fun delete(room: LiveRoomEntity) = liveRoomDao.delete(room)

    suspend fun deleteById(id: Long) = liveRoomDao.deleteById(id)

    suspend fun updateLiveStatus(id: Long, isLive: Boolean, lastChecked: Long) =
        liveRoomDao.updateLiveStatus(id, isLive, lastChecked)

    suspend fun updateRecordingStatus(id: Long, isRecording: Boolean) =
        liveRoomDao.updateRecordingStatus(id, isRecording)

    suspend fun updateEnabled(id: Long, enabled: Boolean) =
        liveRoomDao.updateEnabled(id, enabled)

    suspend fun updateLastLive(id: Long, lastLive: Long) =
        liveRoomDao.updateLastLive(id, lastLive)

    suspend fun updatePausedStatus(id: Long, isPaused: Boolean) =
        liveRoomDao.updatePausedStatus(id, isPaused)

    suspend fun updateQuality(id: Long, quality: String) =
        liveRoomDao.updateQuality(id, quality)

    suspend fun updateRoomTitle(id: Long, title: String) =
        liveRoomDao.updateRoomTitle(id, title)

    suspend fun updateSpecialFocus(id: Long, isSpecialFocus: Boolean) =
        liveRoomDao.updateSpecialFocus(id, isSpecialFocus)

    suspend fun getSpecialFocusRooms(): List<LiveRoomEntity> =
        liveRoomDao.getSpecialFocusRooms()

    fun getRecordingCount(): Flow<Int> = liveRoomDao.getRecordingCount()
}
