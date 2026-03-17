package com.tasktracker.domain.repository

import com.tasktracker.domain.model.SyncOperation

interface SyncOperationRepository {
    suspend fun enqueue(operation: SyncOperation): Long
    suspend fun dequeue(operation: SyncOperation)
    suspend fun getAll(): List<SyncOperation>
    suspend fun clear()
    suspend fun hasPending(): Boolean
}
