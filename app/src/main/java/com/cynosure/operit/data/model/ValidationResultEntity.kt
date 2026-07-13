package com.cynosure.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "validation_results",
    foreignKeys = [
        ForeignKey(
            entity = TaskTraceEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskTraceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskTraceId"])]
)
data class ValidationResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskTraceId: Long,
    val toolName: String,
    val validatedPath: String,
    val passed: Boolean,
    val detail: String,
    val createdAt: Long
)
