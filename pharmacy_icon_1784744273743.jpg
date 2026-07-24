package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lectures")
data class LectureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String,
    val stage: Int = 2,
    val stageName: String = "المرحلة الثانية",
    val semester: Int = 1,
    val doctorName: String = "",
    val fileUrl: String,
    val fileSize: String = "3.5 MB",
    val pageCount: Int = 24,
    val summary: String = "",
    val examTips: String = "",
    val isPinned: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadPath: String? = null,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val downloadCount: Int = 0,
    val viewCount: Int = 0,
    val uploadTimestamp: Long = System.currentTimeMillis()
)
