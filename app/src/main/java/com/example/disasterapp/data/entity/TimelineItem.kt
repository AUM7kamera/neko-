package com.example.disasterapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timeline")
data class TimelineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val timestamp: Long
)
