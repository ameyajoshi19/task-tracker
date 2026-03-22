package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
                WHERE sb3.taskId = t.id AND sb3.status = 'CONFIRMED') AS blockCount,
               tag.name AS tagName,
               tag.color AS tagColor
        FROM tasks t
        LEFT JOIN scheduled_blocks sb
            ON sb.taskId = t.id AND sb.status = 'CONFIRMED'
            AND sb.startTime = (
                SELECT MIN(sb2.startTime) FROM scheduled_blocks sb2
                WHERE sb2.taskId = t.id AND sb2.status = 'CONFIRMED'
            )
        LEFT JOIN tags tag ON tag.id = t.tagId
        ORDER BY t.createdAt DESC
    """)
    fun observeAllWithNextBlock(): Flow<List<TaskWithNextBlockTuple>>

    @Query("""
        SELECT t.*,
               sb.startTime AS nextBlockStart,
               sb.endTime AS nextBlockEnd,
               (SELECT COUNT(*) FROM scheduled_blocks sb3
                WHERE sb3.taskId = t.id AND sb3.status = 'CONFIRMED') AS blockCount,
               tag.name AS tagName,
               tag.color AS tagColor
        FROM tasks t
        LEFT JOIN scheduled_blocks sb
            ON sb.taskId = t.id AND sb.status = 'CONFIRMED'
            AND sb.startTime = (
                SELECT MIN(sb2.startTime) FROM scheduled_blocks sb2
                WHERE sb2.taskId = t.id AND sb2.status = 'CONFIRMED'
            )
        LEFT JOIN tags tag ON tag.id = t.tagId
        WHERE t.tagId = :tagId
        ORDER BY t.createdAt DESC
    """)
    fun observeByTagId(tagId: Long): Flow<List<TaskWithNextBlockTuple>>

    @Query("SELECT * FROM tasks WHERE recurringTaskId = :recurringTaskId")
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE recurringTaskId = :recurringTaskId AND instanceDate >= :startDate AND instanceDate < :endDate")
    suspend fun getByRecurringTaskIdAndDateRange(
        recurringTaskId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE recurringTaskId = :recurringTaskId AND instanceDate >= :fromDate")
    suspend fun deleteByRecurringTaskIdFromDate(recurringTaskId: Long, fromDate: LocalDate)

    @Query("UPDATE tasks SET tagId = :tagId WHERE recurringTaskId = :recurringTaskId")
    suspend fun updateTagByRecurringTaskId(recurringTaskId: Long, tagId: Long?)
}
