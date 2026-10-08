package com.local.wahelper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: CapturedMessage)

    @Query("SELECT * FROM captured_messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CapturedMessage>>

    @Query("DELETE FROM captured_messages")
    suspend fun clearAll()

    @Query("DELETE FROM captured_messages WHERE id = :id")
    suspend fun delete(id: Long)
}
