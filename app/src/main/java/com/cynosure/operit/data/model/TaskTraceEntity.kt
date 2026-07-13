package com.cynosure.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_traces",
    indices = [Index(value = ["createdAt"])]
)
data class TaskTraceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val userMessage: String,
    val assistantReply: String,
    val toolsUsed: String,
    val success: Boolean,
    val createdAt: Long
)
