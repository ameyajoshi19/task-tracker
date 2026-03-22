package com.tasktracker.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.CalendarRepository
import com.tasktracker.domain.repository.CalendarSelectionRepository
import com.tasktracker.domain.repository.RecurringTaskExceptionRepository
import com.tasktracker.domain.repository.RecurringTaskRepository
import com.tasktracker.domain.repository.ScheduledBlockRepository
import com.tasktracker.domain.repository.TagRepository
import com.tasktracker.domain.repository.TaskRepository
import com.tasktracker.domain.repository.AvailabilitySlotRepository
import com.tasktracker.domain.scheduler.ScheduledTimeComparator
import com.tasktracker.domain.scheduler.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed class ViewMode {
    data object Today : ViewMode()
    data object AllTasks : ViewMode()
    data class TagFilter(val tagId: Long, val tagName: String) : ViewMode()
}

data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>> = emptyMap(),
    val completedTasks: List<TaskWithScheduleInfo> = emptyList(),
    val overdueTasks: List<TaskWithScheduleInfo> = emptyList(),
    val todayTasks: List<TaskWithScheduleInfo> = emptyList(),
    val upcomingTasks: List<TaskWithScheduleInfo> = emptyList(),
    val completedTodayTasks: List<TaskWithScheduleInfo> = emptyList(),
    val isLoading: Boolean = true,
    val rescheduleError: String? = null,
    val reschedulingTaskIds: Set<Long> = emptySet(),
    val isRefreshing: Boolean = false,
    val recurringDeleteTask: TaskWithScheduleInfo? = null,
    val recurringDeleteTemplate: RecurringTask? = null,
    val currentViewMode: ViewMode = ViewMode.Today,
    val tags: List<Tag> = emptyList(),
)

/**
 * Drives the main task list screen.
 *
 * Derives [uiState] reactively from [TaskRepository.observeAllWithScheduleInfo], layered with
 * ephemeral UI state (reschedule errors, loading indicators, recurring-delete dialog state) via
 * [combine]. Persisted task data comes through Room's Flow; ephemeral state lives in
 * [MutableStateFlow]s so the combined state is always up to date without manual refreshes.
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val syncManager: CalendarSyncManager,
    private val availabilityRepository: AvailabilitySlotRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val taskScheduler: TaskScheduler,
    private val appPreferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val recurringTaskRepository: RecurringTaskRepository,
    private val recurringTaskExceptionRepository: RecurringTaskExceptionRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _rescheduleError = MutableStateFlow<String?>(null)
    private val _reschedulingTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    private val _recurringDeleteTask = MutableStateFlow<TaskWithScheduleInfo?>(null)
    private val _recurringDeleteTemplate = MutableStateFlow<RecurringTask?>(null)
    private val _currentViewMode = MutableStateFlow<ViewMode>(ViewMode.Today)

    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeAllWithScheduleInfo(),
        _rescheduleError,
        _reschedulingTaskIds,
        _isRefreshing,
        combine(
            _recurringDeleteTask,
            _recurringDeleteTemplate,
            _currentViewMode,
            tagRepository.observeAll(),
        ) { task, template, viewMode, tags -> Triple(task to template, viewMode, tags) },
    ) { tasks, rescheduleErr, reschedulingIds, refreshing, (recurringPair, viewMode, tags) ->
        val (recurringTask, recurringTemplate) = recurringPair
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val active = tasks.filter { it.task.status != TaskStatus.COMPLETED }
        val completed = tasks.filter { it.task.status == TaskStatus.COMPLETED }
            .sortedByDescending { it.task.updatedAt }

        val scheduledTimeComparator = ScheduledTimeComparator()
        val startOfToday = today.atStartOfDay(zoneId).toInstant()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zoneId).toInstant()
        val startOfDayAfterTomorrow = today.plusDays(2).atStartOfDay(zoneId).toInstant()

        // Overdue: nextBlockStart < startOfToday OR deadline < startOfToday, status != COMPLETED
        val overdueTasks = active.filter { info ->
            val scheduledOverdue = info.nextBlockStart?.let { it < startOfToday } ?: false
            val deadlineOverdue = info.task.deadline?.let { it < startOfToday } ?: false
            scheduledOverdue || deadlineOverdue
        }.sortedWith(scheduledTimeComparator)

        val overdueIds = overdueTasks.map { it.task.id }.toSet()

        // Today: nextBlockStart within today OR deadline is today (exclude overdue)
        val todayTasks = active.filter { info ->
            if (info.task.id in overdueIds) return@filter false
            val scheduledToday = info.nextBlockStart?.let { it >= startOfToday && it < startOfTomorrow } ?: false
            val deadlineToday = info.task.deadline?.let {
                it.atZone(zoneId).toLocalDate() == today
            } ?: false
            scheduledToday || deadlineToday
        }.sortedWith(scheduledTimeComparator)

        val todayIds = todayTasks.map { it.task.id }.toSet()

        // Upcoming: nextBlockStart within tomorrow (exclude already shown)
        val upcomingTasks = active.filter { info ->
            if (info.task.id in overdueIds || info.task.id in todayIds) return@filter false
            info.nextBlockStart?.let { it >= startOfTomorrow && it < startOfDayAfterTomorrow } ?: false
        }.sortedWith(scheduledTimeComparator)

        // Completed today: completed tasks where updatedAt is within today
        val completedTodayTasks = completed.filter { info ->
            info.task.updatedAt.let { it >= startOfToday && it < startOfTomorrow }
        }.sortedByDescending { it.task.updatedAt }

        TaskListUiState(
            tasksByQuadrant = active.groupBy { it.task.quadrant },
            completedTasks = completed,
            overdueTasks = overdueTasks,
            todayTasks = todayTasks,
            upcomingTasks = upcomingTasks,
            completedTodayTasks = completedTodayTasks,
            isLoading = false,
            rescheduleError = rescheduleErr,
            reschedulingTaskIds = reschedulingIds,
            isRefreshing = refreshing,
            recurringDeleteTask = recurringTask,
            recurringDeleteTemplate = recurringTemplate,
            currentViewMode = viewMode,
            tags = tags,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState(),
    )

    fun setViewMode(mode: ViewMode) {
        _currentViewMode.value = mode
    }

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

    fun deleteTask(taskInfo: TaskWithScheduleInfo) {
        if (taskInfo.recurringTaskId != null) {
            showRecurringDeleteDialog(taskInfo)
        } else {
            viewModelScope.launch {
                syncManager.deleteTaskEvents(taskInfo.task.id)
                taskRepository.delete(taskInfo.task)
            }
        }
    }

    fun showRecurringDeleteDialog(task: TaskWithScheduleInfo) {
        viewModelScope.launch {
            val template = task.recurringTaskId?.let { recurringTaskRepository.getById(it) }
            _recurringDeleteTask.value = task
            _recurringDeleteTemplate.value = template
        }
    }

    fun dismissRecurringDeleteDialog() {
        _recurringDeleteTask.value = null
        _recurringDeleteTemplate.value = null
    }

    /**
     * Deletes a single recurring instance and inserts an exception so [RecurrenceExpander] does
     * not regenerate it on the next sync.
     */
    fun deleteRecurringInstance(task: Task) {
        viewModelScope.launch {
            val recurringTaskId = task.recurringTaskId ?: return@launch
            val instanceDate = task.instanceDate ?: return@launch

            // Create exception so expander doesn't regenerate
            recurringTaskExceptionRepository.insert(
                RecurringTaskException(recurringTaskId = recurringTaskId, exceptionDate = instanceDate)
            )

            // Delete calendar events, then delete the task instance
            syncManager.deleteTaskEvents(task.id)
            taskRepository.delete(task)

            _recurringDeleteTask.value = null
            _recurringDeleteTemplate.value = null
        }
    }

    /**
     * Truncates the recurrence series by setting the template's [RecurringTask.endDate] to the
     * day before [task]'s instance, then deletes all instances from that date forward along with
     * their calendar events.
     */
    fun deleteRecurringInstanceAndFuture(task: Task) {
        viewModelScope.launch {
            val recurringTaskId = task.recurringTaskId ?: return@launch
            val instanceDate = task.instanceDate ?: return@launch

            // Set endDate on template to day before this instance
            val template = recurringTaskRepository.getById(recurringTaskId) ?: return@launch
            recurringTaskRepository.update(
                template.copy(endDate = instanceDate.minusDays(1))
            )

            // Collect all future instances and their blocks for calendar cleanup
            val futureInstances = taskRepository.getByRecurringTaskId(recurringTaskId)
                .filter { it.instanceDate != null && !it.instanceDate.isBefore(instanceDate) }

            for (instance in futureInstances) {
                syncManager.deleteTaskEvents(instance.id)
            }

            // Delete future task instances
            taskRepository.deleteByRecurringTaskIdFromDate(recurringTaskId, instanceDate)

            _recurringDeleteTask.value = null
            _recurringDeleteTemplate.value = null
        }
    }

    fun deleteEntireRecurringTask(task: Task) {
        viewModelScope.launch {
            val recurringTaskId = task.recurringTaskId ?: return@launch

            // 1. Collect all instances and delete their calendar events
            val allInstances = taskRepository.getByRecurringTaskId(recurringTaskId)
            for (instance in allInstances) {
                syncManager.deleteTaskEvents(instance.id)
            }

            // 2. Explicitly delete all task instances (no FK CASCADE from tasks → recurring_tasks)
            taskRepository.deleteByRecurringTaskIdFromDate(recurringTaskId, LocalDate.MIN)

            // 3. Delete template — CASCADE deletes exceptions
            val template = recurringTaskRepository.getById(recurringTaskId) ?: return@launch
            recurringTaskRepository.delete(template)

            _recurringDeleteTask.value = null
            _recurringDeleteTemplate.value = null
        }
    }

    /**
     * Moves [taskId] to a new time slot, treating its current slot as blocked so the scheduler
     * finds a genuinely different window. The old blocks and calendar events are deleted before
     * inserting new ones; the task ID is preserved so the UI item doesn't flash.
     */
    fun rescheduleTask(taskId: Long) {
        viewModelScope.launch {
            _rescheduleError.value = null
            _reschedulingTaskIds.update { it + taskId }
            try {
                val task = taskRepository.getById(taskId) ?: return@launch
                val oldBlocks = blockRepository.getByTaskId(taskId)
                    .filter { it.status == BlockStatus.CONFIRMED }

                if (oldBlocks.isEmpty()) return@launch

                val blockedSlots = oldBlocks.map { TimeSlot(it.startTime, it.endTime) }

                val availability = availabilityRepository.getEnabled().ifEmpty {
                    _reschedulingTaskIds.update { it - taskId }
                    _rescheduleError.value = "No availability slots enabled. Please configure your availability in Settings."
                    return@launch
                }
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
                        val allNewBlocks = newBlocks + movedBlocks
                        val insertedIds = blockRepository.insertAll(allNewBlocks)
                        taskRepository.updateStatus(taskId, TaskStatus.SCHEDULED)
                        allNewBlocks.zip(insertedIds).forEach { (block, id) ->
                            syncManager.pushNewBlock(block.copy(id = id))
                        }
                    }
                    is SchedulingResult.DeadlineAtRisk -> {
                        _rescheduleError.value = result.message
                    }
                    is SchedulingResult.NoSlotsAvailable -> {
                        _rescheduleError.value = result.message
                    }
                }
            } finally {
                _reschedulingTaskIds.update { it - taskId }
            }
        }
    }

    fun clearRescheduleError() {
        _rescheduleError.value = null
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        syncScheduler.syncNow()
        viewModelScope.launch {
            val terminalState = syncScheduler.observeSyncNowStatus()
                .mapNotNull { workInfos ->
                    workInfos.firstOrNull()?.state
                }
                .first { state ->
                    state == WorkInfo.State.SUCCEEDED ||
                        state == WorkInfo.State.FAILED ||
                        state == WorkInfo.State.CANCELLED
                }
            _isRefreshing.value = false
            if (terminalState == WorkInfo.State.FAILED) {
                _rescheduleError.value = "Sync failed. Please try again."
            }
        }
    }
}
