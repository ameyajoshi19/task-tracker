package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.ScheduledBlockDao
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import com.tasktracker.domain.repository.ScheduledBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ScheduledBlockRepositoryImpl @Inject constructor(
    private val dao: ScheduledBlockDao,
) : ScheduledBlockRepository {

    override suspend fun insert(block: ScheduledBlock): Long =
        dao.insert(ScheduledBlockEntity.fromDomain(block))

    override suspend fun insertAll(blocks: List<ScheduledBlock>): List<Long> =
        dao.insertAll(blocks.map { ScheduledBlockEntity.fromDomain(it) })

    override suspend fun update(block: ScheduledBlock) =
        dao.update(ScheduledBlockEntity.fromDomain(block))

    override suspend fun delete(block: ScheduledBlock) =
        dao.delete(ScheduledBlockEntity.fromDomain(block))

    override suspend fun getByTaskId(taskId: Long): List<ScheduledBlock> =
        dao.getByTaskId(taskId).map { it.toDomain() }

    override fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlock>> =
        dao.observeByTaskId(taskId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlock> =
        dao.getByStatuses(statuses).map { it.toDomain() }

    override fun observeInRange(start: Instant, end: Instant): Flow<List<ScheduledBlock>> =
        dao.observeInRange(start.toEpochMilli(), end.toEpochMilli())
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun updateStatus(id: Long, status: BlockStatus) =
        dao.updateStatus(id, status)

    override suspend fun deleteByTaskId(taskId: Long) =
        dao.deleteByTaskId(taskId)

    override suspend fun deleteProposed() =
        dao.deleteByStatus(BlockStatus.PROPOSED)
}
