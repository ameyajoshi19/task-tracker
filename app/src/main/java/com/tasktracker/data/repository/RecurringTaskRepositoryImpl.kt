package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.RecurringTaskDao
import com.tasktracker.data.local.entity.RecurringTaskEntity
import com.tasktracker.domain.model.RecurringTask
import com.tasktracker.domain.repository.RecurringTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RecurringTaskRepositoryImpl @Inject constructor(
    private val dao: RecurringTaskDao,
) : RecurringTaskRepository {
    override suspend fun insert(recurringTask: RecurringTask): Long =
        dao.insert(RecurringTaskEntity.fromDomain(recurringTask))

    override suspend fun update(recurringTask: RecurringTask) =
        dao.update(RecurringTaskEntity.fromDomain(recurringTask.copy(updatedAt = Instant.now())))

    override suspend fun delete(recurringTask: RecurringTask) =
        dao.delete(RecurringTaskEntity.fromDomain(recurringTask))

    override suspend fun getById(id: Long): RecurringTask? =
        dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<RecurringTask>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<RecurringTask> =
        dao.getAll().map { it.toDomain() }
}
