package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status")
    suspend fun getByStatus(status: TaskStatus): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<TaskStatus>): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, updatedAt: Long)

    @Query("""
        SELECT t.*,
               sb.startTime AS nextBlockStart,
               sb.endTime AS nextBlockEnd,
               (SELECT COUNT(*) FROM scheduled_blocks sb3
                WHERE sb3.taskId = t.id AND sb3.status = 'CONFIRMED') AS blockCount
        FROM tasks t
        LEFT JOIN scheduled_blocks sb
            ON sb.taskId = t.id AND sb.status = 'CONFIRMED'
            AND sb.startTime = (
                SELECT MIN(sb2.startTime) FROM scheduled_blocks sb2
                WHERE sb2.taskId = t.id AND sb2.status = 'CONFIRMED'
            )
        ORDER BY t.createdAt DESC
    """)
    fun observeAllWithNextBlock(): Flow<List<TaskWithNextBlockTuple>>
}
