package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "broadcast_notifications")
data class BroadcastNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val message: String,
    val lectureId: Long? = null,
    val stage: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val type: String = "LECTURE" // LECTURE or ANNOUNCEMENT
)
