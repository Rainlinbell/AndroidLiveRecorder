package com.liverecorder.app.data.db

import androidx.room.*
import com.liverecorder.app.data.db.entity.LiveRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveRoomDao {

    @Query("SELECT * FROM live_rooms ORDER BY createdAt DESC")
    fun getAllRooms(): Flow<List<LiveRoomEntity>>

    @Query("SELECT * FROM live_rooms WHERE enabled = 1")
    suspend fun getEnabledRooms(): List<LiveRoomEntity>

    @Query("SELECT * FROM live_rooms WHERE id = :id")
    suspend fun getRoomById(id: Long): LiveRoomEntity?

    @Query("SELECT * FROM live_rooms WHERE platform = :platform AND roomId = :roomId")
    suspend fun getRoomByPlatformAndId(platform: String, roomId: String): LiveRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: LiveRoomEntity): Long

    @Update
    suspend fun update(room: LiveRoomEntity)

    @Delete
    suspend fun delete(room: LiveRoomEntity)

    @Query("DELETE FROM live_rooms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE live_rooms SET isLive = :isLive, lastChecked = :lastChecked WHERE id = :id")
    suspend fun updateLiveStatus(id: Long, isLive: Boolean, lastChecked: Long)

    @Query("UPDATE live_rooms SET isRecording = :isRecording WHERE id = :id")
    suspend fun updateRecordingStatus(id: Long, isRecording: Boolean)

    @Query("UPDATE live_rooms SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE live_rooms SET lastLive = :lastLive WHERE id = :id")
    suspend fun updateLastLive(id: Long, lastLive: Long)

    @Query("UPDATE live_rooms SET isPaused = :isPaused WHERE id = :id")
    suspend fun updatePausedStatus(id: Long, isPaused: Boolean)

    @Query("UPDATE live_rooms SET quality = :quality WHERE id = :id")
    suspend fun updateQuality(id: Long, quality: String)

    @Query("UPDATE live_rooms SET roomTitle = :title WHERE id = :id")
    suspend fun updateRoomTitle(id: Long, title: String)

    @Query("UPDATE live_rooms SET isSpecialFocus = :isSpecialFocus WHERE id = :id")
    suspend fun updateSpecialFocus(id: Long, isSpecialFocus: Boolean)

    @Query("SELECT * FROM live_rooms WHERE isSpecialFocus = 1")
    suspend fun getSpecialFocusRooms(): List<LiveRoomEntity>

    @Query("SELECT COUNT(*) FROM live_rooms WHERE isRecording = 1")
    fun getRecordingCount(): Flow<Int>
}
