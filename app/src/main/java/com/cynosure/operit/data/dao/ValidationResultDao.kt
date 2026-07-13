package com.cynosure.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cynosure.operit.data.model.ValidationResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ValidationResultDao {
    @Insert
    suspend fun insert(result: ValidationResultEntity): Long

    @Query("SELECT * FROM validation_results WHERE taskTraceId = :taskTraceId ORDER BY createdAt DESC")
    fun observeForTask(taskTraceId: Long): Flow<List<ValidationResultEntity>>

    @Query("SELECT * FROM validation_results ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ValidationResultEntity>>

    @Query("DELETE FROM validation_results WHERE id NOT IN (SELECT id FROM validation_results ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 200)
}
