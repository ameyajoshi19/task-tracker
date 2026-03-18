package com.tasktracker.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.CalendarRepository
import com.tasktracker.domain.repository.CalendarSelectionRepository
import com.tasktracker.domain.repository.ScheduledBlockRepository
import com.tasktracker.domain.repository.TaskRepository
import com.tasktracker.domain.repository.UserAvailabilityRepository
import com.tasktracker.domain.scheduler.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>> = emptyMap(),
    val completedTasks: List<TaskWithScheduleInfo> = emptyList(),
    val dueTodayTasks: List<TaskWithScheduleInfo> = emptyList(),
    val isLoading: Boolean = true,
    val rescheduleError: String? = null,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val syncManager: CalendarSyncManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val taskScheduler: TaskScheduler,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _rescheduleError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeAllWithScheduleInfo(),
        _rescheduleError,
    ) { tasks, rescheduleErr ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val active = tasks.filter { it.task.status != TaskStatus.COMPLETED }
        val completed = tasks.filter { it.task.status == TaskStatus.COMPLETED }

        val dueToday = active.filter { info ->
            info.task.deadline?.let { deadline ->
                deadline.atZone(zoneId).toLocalDate() == today
            } ?: false
        }

        TaskListUiState(
            tasksByQuadrant = active.groupBy { it.task.quadrant },
            completedTasks = completed,
            dueTodayTasks = dueToday,
            isLoading = false,
            rescheduleError = rescheduleErr,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState(),
    )

    fun completeTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateStatus(task.id, TaskStatus.COMPLETED)
            val blocks = blockRepository.getByTaskId(task.id)
            for (block in blocks) {
                blockRepository.updateStatus(block.id, BlockStatus.COMPLETED)
            }
            syncManager.markTaskCompleted(task.id)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            syncManager.deleteTaskEvents(task.id)
            taskRepository.delete(task)
        }
    }

    fun rescheduleTask(taskId: Long) {
        viewModelScope.launch {
            _rescheduleError.value = null

            val task = taskRepository.getById(taskId) ?: return@launch
            val oldBlocks = blockRepository.getByTaskId(taskId)
                .filter { it.status == BlockStatus.CONFIRMED }

            if (oldBlocks.isEmpty()) return@launch

            val blockedSlots = oldBlocks.map { TimeSlot(it.startTime, it.endTime) }

            val availability = availabilityRepository.getAll()
            val existingBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            ).filter { it.taskId != taskId }
            val allTasks = taskRepository.getByStatuses(
                listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
            )
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val busySlots = try {
                val now = Instant.now()
                calendarRepository.getFreeBusySlots(
                    calendarIds = enabledCalendars.map { it.googleCalendarId },
                    timeMin = now,
                    timeMax = now.plusSeconds(14 * 24 * 3600),
                )
            } catch (_: Exception) {
                emptyList()
            }

            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val taskForScheduling = task.copy(status = TaskStatus.PENDING)

            val result = taskScheduler.scheduleWithConflictResolution(
                newTask = taskForScheduling,
                allTasks = allTasks,
                existingBlocks = existingBlocks,
                availability = availability,
                busySlots = busySlots + blockedSlots,
                startDate = today,
                endDate = today.plusDays(14),
                zoneId = zoneId,
                now = Instant.now(),
            )

            appPreferences.setLastSyncTimestamp(Instant.now())

            when (result) {
                is SchedulingResult.Scheduled -> {
                    syncManager.deleteTaskEvents(taskId)
                    blockRepository.deleteByTaskId(taskId)
                    val newBlocks = result.blocks.map { it.copy(taskId = taskId) }
                    val insertedIds = blockRepository.insertAll(newBlocks)
                    taskRepository.updateStatus(taskId, TaskStatus.SCHEDULED)
                    newBlocks.zip(insertedIds).forEach { (block, id) ->
                        syncManager.pushNewBlock(block.copy(id = id))
                    }
                }
                is SchedulingResult.NeedsReschedule -> {
                    syncManager.deleteTaskEvents(taskId)
                    blockRepository.deleteByTaskId(taskId)
                    val newBlocks = result.newBlocks.map { it.copy(taskId = taskId) }
                    val movedBlocks = result.movedBlocks.map { it.second }
                    blockRepository.insertAll(newBlocks + movedBlocks)
                    taskRepository.updateStatus(taskId, TaskStatus.SCHEDULED)
                    newBlocks.forEach { syncManager.pushNewBlock(it) }
                }
                is SchedulingResult.DeadlineAtRisk -> {
                    _rescheduleError.value = result.message
                }
                is SchedulingResult.NoSlotsAvailable -> {
                    _rescheduleError.value = result.message
                }
            }
        }
    }

    fun clearRescheduleError() {
        _rescheduleError.value = null
    }
}
