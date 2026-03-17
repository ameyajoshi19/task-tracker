package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.domain.model.BlockStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: ScheduledBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduledBlockEntity>): List<Long>

    @Update
    suspend fun update(block: ScheduledBlockEntity)

    @Delete
    suspend fun delete(block: ScheduledBlockEntity)

    @Query("SELECT * FROM scheduled_blocks WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: Long): List<ScheduledBlockEntity>

    @Query("SELECT * FROM scheduled_blocks WHERE taskId = :taskId")
    fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlockEntity>>

    @Query("SELECT * FROM scheduled_blocks WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlockEntity>

    @Query("SELECT * FROM scheduled_blocks WHERE startTime >= :start AND endTime <= :end")
    fun observeInRange(start: Long, end: Long): Flow<List<ScheduledBlockEntity>>

    @Query("UPDATE scheduled_blocks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: BlockStatus)

    @Query("DELETE FROM scheduled_blocks WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("DELETE FROM scheduled_blocks WHERE status = :status")
    suspend fun deleteByStatus(status: BlockStatus)
}
