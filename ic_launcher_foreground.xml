package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val email: String,
    val fullName: String,
    val studentId: String = "",
    val role: String = "STUDENT", // "ADMIN" or "STUDENT"
    val registrationDate: Long = System.currentTimeMillis(),
    val isApproved: Boolean = true
)
