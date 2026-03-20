package com.tasktracker.domain.repository

import com.tasktracker.domain.model.RecurringTaskException

interface RecurringTaskExceptionRepository {
    suspend fun insert(exception: RecurringTaskException): Long
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskException>
    suspend fun deleteByRecurringTaskId(recurringTaskId: Long)
}
