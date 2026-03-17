package com.tasktracker.domain.repository

import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ScheduledBlockRepository {
    suspend fun insert(block: ScheduledBlock): Long
    suspend fun insertAll(blocks: List<ScheduledBlock>): List<Long>
    suspend fun update(block: ScheduledBlock)
    suspend fun delete(block: ScheduledBlock)
    suspend fun getByTaskId(taskId: Long): List<ScheduledBlock>
    fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlock>>
    suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlock>
    fun observeInRange(start: Instant, end: Instant): Flow<List<ScheduledBlock>>
    suspend fun updateStatus(id: Long, status: BlockStatus)
    suspend fun deleteByTaskId(taskId: Long)
    suspend fun deleteProposed()
}
