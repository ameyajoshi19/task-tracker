package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.TaskDao
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
) : TaskRepository {

    override suspend fun insert(task: Task): Long =
        taskDao.insert(TaskEntity.fromDomain(task))

    override suspend fun update(task: Task) =
        taskDao.update(TaskEntity.fromDomain(task.copy(updatedAt = Instant.now())))

    override suspend fun delete(task: Task) =
        taskDao.delete(TaskEntity.fromDomain(task))

    override suspend fun getById(id: Long): Task? =
        taskDao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeAllWithScheduleInfo(): Flow<List<TaskWithScheduleInfo>> =
        taskDao.observeAllWithNextBlock().map { tuples ->
            tuples.map { t ->
                TaskWithScheduleInfo(
                    task = Task(
                        id = t.id,
                        title = t.title,
                        estimatedDurationMinutes = t.estimatedDurationMinutes,
                        quadrant = t.quadrant,
                        deadline = t.deadline,
                        dayPreference = t.dayPreference,
                        splittable = t.splittable,
                        status = t.status,
                        recurringPattern = t.recurringPattern,
                        recurringTaskId = t.recurringTaskId,
                        instanceDate = t.instanceDate,
                        fixedTime = t.fixedTime,
                        availabilitySlot = t.availabilitySlot?.let { AvailabilitySlotType.valueOf(it) },
                        tagId = t.tagId,
                        createdAt = t.createdAt,
                        updatedAt = t.updatedAt,
                    ),
                    nextBlockStart = t.nextBlockStart,
                    nextBlockEnd = t.nextBlockEnd,
                    blockCount = t.blockCount,
                    recurringTaskId = t.recurringTaskId,
                    instanceDate = t.instanceDate,
                    tagName = t.tagName,
                    tagColor = t.tagColor,
                    availabilitySlot = t.availabilitySlot?.let { AvailabilitySlotType.valueOf(it) },
                )
            }
        }

    override suspend fun getByStatus(status: TaskStatus): List<Task> =
        taskDao.getByStatus(status).map { it.toDomain() }

    override suspend fun getByStatuses(statuses: List<TaskStatus>): List<Task> =
        taskDao.getByStatuses(statuses).map { it.toDomain() }

    override suspend fun updateStatus(id: Long, status: TaskStatus) =
        taskDao.updateStatus(id, status, Instant.now().toEpochMilli())

    override suspend fun getByRecurringTaskId(recurringTaskId: Long): List<Task> =
        taskDao.getByRecurringTaskId(recurringTaskId).map { it.toDomain() }

    override suspend fun getByRecurringTaskIdAndDateRange(
        recurringTaskId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Task> =
        taskDao.getByRecurringTaskIdAndDateRange(recurringTaskId, startDate, endDate).map { it.toDomain() }

    override suspend fun deleteByRecurringTaskIdFromDate(recurringTaskId: Long, fromDate: LocalDate) =
        taskDao.deleteByRecurringTaskIdFromDate(recurringTaskId, fromDate)

    override suspend fun updateTagByRecurringTaskId(recurringTaskId: Long, tagId: Long?) =
        taskDao.updateTagByRecurringTaskId(recurringTaskId, tagId)
}
