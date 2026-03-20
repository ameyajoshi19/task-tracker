package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.RecurringTaskExceptionDao
import com.tasktracker.data.local.entity.RecurringTaskExceptionEntity
import com.tasktracker.domain.model.RecurringTaskException
import com.tasktracker.domain.repository.RecurringTaskExceptionRepository
import javax.inject.Inject

class RecurringTaskExceptionRepositoryImpl @Inject constructor(
    private val dao: RecurringTaskExceptionDao,
) : RecurringTaskExceptionRepository {
    override suspend fun insert(exception: RecurringTaskException): Long =
        dao.insert(RecurringTaskExceptionEntity.fromDomain(exception))

    override suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskException> =
        dao.getByRecurringTaskId(recurringTaskId).map { it.toDomain() }

    override suspend fun deleteByRecurringTaskId(recurringTaskId: Long) =
        dao.deleteByRecurringTaskId(recurringTaskId)
}
