package com.cynosure.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cynosure.operit.data.model.TaskTraceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTraceDao {
    @Insert
    suspend fun insert(trace: TaskTraceEntity): Long

    @Query("SELECT * FROM task_traces ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TaskTraceEntity>>

    @Query("SELECT * FROM task_traces WHERE success = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeFailed(limit: Int = 20): Flow<List<TaskTraceEntity>>

    @Query("DELETE FROM task_traces WHERE id NOT IN (SELECT id FROM task_traces ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 200)
}
