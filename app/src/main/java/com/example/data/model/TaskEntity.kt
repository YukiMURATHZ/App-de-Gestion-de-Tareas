package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val category: String = "Personal",
    val priority: String = "MEDIA", // "ALTA", "MEDIA", "BAJA"
    val isCompleted: Boolean = false,
    val isStarred: Boolean = false,
    val dueDate: Long? = null, // Epoch millis at start of day
    val dueTime: String? = null, // e.g. "14:30"
    val isDailyReminder: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
