package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.PendingSyncOperationDao
import com.tasktracker.data.local.entity.PendingSyncOperationEntity
import com.tasktracker.domain.model.SyncOperation
import com.tasktracker.domain.repository.SyncOperationRepository
import javax.inject.Inject

class SyncOperationRepositoryImpl @Inject constructor(
    private val dao: PendingSyncOperationDao,
) : SyncOperationRepository {

    override suspend fun enqueue(operation: SyncOperation): Long =
        dao.insert(PendingSyncOperationEntity.fromDomain(operation))

    override suspend fun dequeue(operation: SyncOperation) =
        dao.delete(PendingSyncOperationEntity.fromDomain(operation))

    override suspend fun getAll(): List<SyncOperation> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clear() = dao.deleteAll()

    override suspend fun hasPending(): Boolean = dao.count() > 0
}
