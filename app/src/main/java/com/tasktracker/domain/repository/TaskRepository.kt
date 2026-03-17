package com.tasktracker.domain.repository

import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun getById(id: Long): Task?
    fun observeAll(): Flow<List<Task>>
    suspend fun getByStatus(status: TaskStatus): List<Task>
    suspend fun getByStatuses(statuses: List<TaskStatus>): List<Task>
    suspend fun updateStatus(id: Long, status: TaskStatus)
}
