package com.tasktracker.domain.repository

import com.tasktracker.domain.model.RecurringTask
import kotlinx.coroutines.flow.Flow

interface RecurringTaskRepository {
    suspend fun insert(recurringTask: RecurringTask): Long
    suspend fun update(recurringTask: RecurringTask)
    suspend fun delete(recurringTask: RecurringTask)
    suspend fun getById(id: Long): RecurringTask?
    fun observeAll(): Flow<List<RecurringTask>>
    suspend fun getAll(): List<RecurringTask>
}
