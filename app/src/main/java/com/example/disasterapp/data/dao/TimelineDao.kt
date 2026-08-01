package com.example.disasterapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.disasterapp.data.entity.TimelineItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TimelineItem>>

    @Insert
    suspend fun insert(item: TimelineItem): Long

    @Update
    suspend fun update(item: TimelineItem)

    @Query("DELETE FROM timeline WHERE id = :id")
    suspend fun deleteById(id: Long)
}
