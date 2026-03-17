package com.tasktracker.data.calendar

import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncManager @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val syncOperationRepository: SyncOperationRepository,
) {
    private suspend fun getTaskCalendarId(): String =
        calendarRepository.getOrCreateTaskCalendar()

    suspend fun pushNewBlock(block: ScheduledBlock) {
        val task = taskRepository.getById(block.taskId) ?: return
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.CREATE_EVENT, block, task)
            return
        }

        try {
            val eventId = calendarRepository.createEvent(calendarId, block, task.title)
            blockRepository.update(block.copy(googleCalendarEventId = eventId))
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.CREATE_EVENT, block, task)
        }
    }

    suspend fun markTaskCompleted(taskId: Long) {
        val task = taskRepository.getById(taskId) ?: return
        val blocks = blockRepository.getByTaskId(taskId)
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            blocks.filter { it.googleCalendarEventId != null }.forEach { block ->
                enqueueOperation(SyncOperationType.MARK_COMPLETED, block, task)
            }
            return
        }

        for (block in blocks) {
            val eventId = block.googleCalendarEventId ?: continue
            try {
                calendarRepository.updateEvent(
                    calendarId = calendarId,
                    eventId = eventId,
                    block = block,
                    taskTitle = task.title,
                    completed = true,
                )
            } catch (e: IOException) {
                enqueueOperation(SyncOperationType.MARK_COMPLETED, block, task)
            }
        }
    }

    suspend fun deleteTaskEvents(taskId: Long) {
        val blocks = blockRepository.getByTaskId(taskId)
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            blocks.filter { it.googleCalendarEventId != null }.forEach { block ->
                val task = taskRepository.getById(taskId) ?: return
                enqueueOperation(SyncOperationType.DELETE_EVENT, block, task)
            }
            return
        }

        for (block in blocks) {
            val eventId = block.googleCalendarEventId ?: continue
            try {
                calendarRepository.deleteEvent(calendarId, eventId)
            } catch (e: IOException) {
                val task = taskRepository.getById(taskId) ?: return
                enqueueOperation(SyncOperationType.DELETE_EVENT, block, task)
            }
        }
    }

    suspend fun updateBlockEvent(block: ScheduledBlock) {
        val task = taskRepository.getById(block.taskId) ?: return
        val eventId = block.googleCalendarEventId ?: return
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.UPDATE_EVENT, block, task)
            return
        }

        try {
            calendarRepository.updateEvent(calendarId, eventId, block, task.title)
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.UPDATE_EVENT, block, task)
        }
    }

    suspend fun processPendingOperations() {
        val pending = syncOperationRepository.getAll()
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            return // Still offline
        }
        for (op in pending) {
            try {
                val task = taskRepository.getById(op.taskId) ?: continue
                val block = blockRepository.getByTaskId(op.taskId)
                    .find { it.id == op.blockId }
                val resolvedCalendarId = op.calendarId.ifEmpty { calendarId }
                when (op.type) {
                    SyncOperationType.CREATE_EVENT -> {
                        if (block != null) {
                            val eventId = calendarRepository.createEvent(
                                resolvedCalendarId, block, task.title
                            )
                            blockRepository.update(
                                block.copy(googleCalendarEventId = eventId)
                            )
                        }
                    }
                    SyncOperationType.UPDATE_EVENT -> {
                        if (block != null && op.eventId != null) {
                            calendarRepository.updateEvent(
                                resolvedCalendarId, op.eventId, block, task.title
                            )
                        }
                    }
                    SyncOperationType.DELETE_EVENT -> {
                        if (op.eventId != null) {
                            calendarRepository.deleteEvent(resolvedCalendarId, op.eventId)
                        }
                    }
                    SyncOperationType.MARK_COMPLETED -> {
                        if (block != null && op.eventId != null) {
                            calendarRepository.updateEvent(
                                resolvedCalendarId, op.eventId, block, task.title,
                                completed = true,
                            )
                        }
                    }
                }
                syncOperationRepository.dequeue(op)
            } catch (e: IOException) {
                break // Still offline, stop processing
            }
        }
    }

    private suspend fun enqueueOperation(
        type: SyncOperationType,
        block: ScheduledBlock,
        task: Task,
    ) {
        // Store empty calendarId — processPendingOperations resolves it fresh
        syncOperationRepository.enqueue(
            SyncOperation(
                type = type,
                blockId = block.id,
                taskId = task.id,
                calendarId = "",
                eventId = block.googleCalendarEventId,
            )
        )
    }
}
