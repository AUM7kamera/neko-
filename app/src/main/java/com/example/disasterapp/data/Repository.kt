package com.example.disasterapp.data

import android.content.Context
import com.example.disasterapp.data.entity.InventoryItem
import com.example.disasterapp.data.entity.TimelineItem
import kotlinx.coroutines.flow.Flow

class Repository private constructor(private val db: AppDatabase) {

    private val timelineDao = db.timelineDao()
    private val inventoryDao = db.inventoryDao()

    fun getAllTimeline(): Flow<List<TimelineItem>> = timelineDao.getAll()

    suspend fun addTimeline(item: TimelineItem): Long = timelineDao.insert(item)
    suspend fun updateTimeline(item: TimelineItem) = timelineDao.update(item)
    suspend fun deleteTimeline(id: Long) = timelineDao.deleteById(id)

    fun getAllInventory(): Flow<List<InventoryItem>> = inventoryDao.getAll()
    suspend fun addInventory(item: InventoryItem): Long = inventoryDao.insert(item)
    suspend fun updateInventory(item: InventoryItem) = inventoryDao.update(item)
    suspend fun deleteInventory(id: Long) = inventoryDao.deleteById(id)

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(context: Context): Repository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context.applicationContext)
                val repo = Repository(db)
                INSTANCE = repo
                repo
            }
        }
    }
}
