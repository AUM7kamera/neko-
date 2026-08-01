package com.example.disasterapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Int = 1,
    val checked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
