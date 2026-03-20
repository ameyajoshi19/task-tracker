package com.tasktracker.ui.taskedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import com.tasktracker.domain.scheduler.*
import com.tasktracker.domain.validation.RecurringTaskValidator
import com.tasktracker.domain.validation.TaskValidator
import com.tasktracker.domain.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.tasktracker.ui.components.suggestDuration
import com.tasktracker.ui.components.suggestQuadrant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class TaskEditUiState(
    val title: String = "",
    val description: String = "",
    val durationMinutes: Int = 60,
    val quadrant: Quadrant = Quadrant.IMPORTANT,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val isEditing: Boolean = false,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val schedulingResult: SchedulingResult? = null,
    val staleDataWarning: Boolean = false,
    val suggestedDurationMinutes: Int? = null,
    val suggestedDurationKeyword: String? = null,
    val suggestedQuadrant: Quadrant? = null,
    val suggestedQuadrantReason: String? = null,
    val isRecurring: Boolean = false,
    val intervalDays: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val isFixedTime: Boolean = false,
    val fixedTime: LocalTime? = null,
)

/**
 * Manages state for the task create/edit screen.
 *
 * For one-off tasks the save flow is: validate → gather scheduling inputs → run the scheduler
 * against in-memory data → only persist to Room if scheduling succeeds. This avoids orphaned
 * PENDING tasks when the calendar is full.
 *
 * Recurring tasks follow a different path: the template ([RecurringTask]) is persisted first,
 * instances are expanded via [RecurrenceExpander], fixed-time instances are placed directly as
 * blocks, and flexible instances are each scheduled individually with conflict resolution.
 *
 * When editing an existing task, the task's own blocks and free/busy slots are excluded from the
 * scheduling inputs so the scheduler doesn't treat the current slot as already occupied.
 */
@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val syncManager: CalendarSyncManager,
    private val taskScheduler: TaskScheduler,
    private val taskValidator: TaskValidator,
    private val appPreferences: AppPreferences,
    private val recurringTaskRepository: RecurringTaskRepository,
    private val recurringTaskExceptionRepository: RecurringTaskExceptionRepository,
    private val recurrenceExpander: RecurrenceExpander,
    private val recurringTaskValidator: RecurringTaskValidator,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L
    private var savedTaskId: Long = taskId
    private val _uiState = MutableStateFlow(TaskEditUiState(isEditing = taskId != -1L))
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.isFreeBusyDataStale.collect { isStale ->
                _uiState.update { it.copy(staleDataWarning = isStale) }
            }
        }
        if (taskId != -1L) {
            viewModelScope.launch {
                val task = taskRepository.getById(taskId) ?: return@launch
                _uiState.update {
                    it.copy(
                        title = task.title,
                        description = task.description,
                        durationMinutes = task.estimatedDurationMinutes,
                        quadrant = task.quadrant,
                        deadline = task.deadline,
                        dayPreference = task.dayPreference,
                        splittable = task.splittable,
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        val suggestion = suggestDuration(title)
        val keyword = if (suggestion != null) {
            title.lowercase().split(" ").firstOrNull { word ->
                suggestDuration(word) != null
            }
        } else null
        _uiState.update {
            it.copy(
                title = title,
                validationError = null,
                suggestedDurationMinutes = suggestion,
                suggestedDurationKeyword = keyword,
            )
        }
    }
    fun updateDescription(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun updateDuration(minutes: Int) { _uiState.update { it.copy(durationMinutes = minutes, validationError = null) } }
    fun updateQuadrant(q: Quadrant) { _uiState.update { it.copy(quadrant = q) } }
    fun updateDeadline(deadline: Instant?) {
        val now = Instant.now()
        val suggested = deadline?.let {
            suggestQuadrant(it, now)
        }
        val reason = deadline?.let {
            val hoursUntil = ChronoUnit.HOURS.between(now, it)
            when {
                hoursUntil <= 24 -> "deadline is today"
                hoursUntil <= 72 -> "deadline in ${hoursUntil / 24 + 1} days"
                hoursUntil <= 168 -> "deadline this week"
                else -> null
            }
        }
        _uiState.update {
            var newState = it.copy(
                deadline = deadline,
                suggestedQuadrant = suggested,
                suggestedQuadrantReason = reason,
            )
            // Auto-select suggested quadrant if user hasn't manually changed it
            if (suggested != null && it.quadrant == (it.suggestedQuadrant ?: Quadrant.IMPORTANT)) {
                newState = newState.copy(quadrant = suggested)
            }
            newState
        }
    }
    fun updateDayPreference(pref: DayPreference) { _uiState.update { it.copy(dayPreference = pref) } }
    fun updateSplittable(splittable: Boolean) { _uiState.update { it.copy(splittable = splittable, validationError = null) } }

    fun updateRecurring(isRecurring: Boolean) {
        _uiState.update { it.copy(isRecurring = isRecurring) }
    }

    fun updateIntervalDays(interval: Int) {
        _uiState.update { it.copy(intervalDays = interval) }
    }

    fun updateStartDate(date: LocalDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun updateEndDate(date: LocalDate?) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun updateFixedTime(isFixed: Boolean) {
        _uiState.update { it.copy(isFixedTime = isFixed, fixedTime = if (isFixed) it.fixedTime ?: LocalTime.of(9, 0) else null) }
    }

    fun updateFixedTimeValue(time: LocalTime) {
        _uiState.update { it.copy(fixedTime = time) }
    }

    fun save() {
        val state = _uiState.value
        // Quick synchronous guard before launching a coroutine
        if (state.title.isBlank()) {
            _uiState.update { it.copy(validationError = "Title is required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // --- Recurring task branch ---
            if (state.isRecurring) {
                val availabilityList = availabilityRepository.getAll()
                val today = LocalDate.now()
                val recurringTask = RecurringTask(
                    title = state.title.trim(),
                    description = state.description.trim(),
                    estimatedDurationMinutes = state.durationMinutes,
                    quadrant = state.quadrant,
                    dayPreference = state.dayPreference,
                    splittable = state.splittable,
                    intervalDays = state.intervalDays,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    fixedTime = if (state.isFixedTime) state.fixedTime else null,
                )

                val validationResult = recurringTaskValidator.validate(
                    recurringTask, availabilityList, today = today,
                )
                if (validationResult is ValidationResult.Invalid) {
                    _uiState.update { it.copy(validationError = validationResult.reason, isSaving = false) }
                    return@launch
                }

                // Save the template
                val templateId = recurringTaskRepository.insert(recurringTask)

                // Expand instances for the scheduling window
                val windowEnd = today.plusDays(14)
                val instances = recurrenceExpander.expand(
                    recurringTask = recurringTask.copy(id = templateId),
                    exceptions = emptyList(),
                    existingInstances = emptyList(),
                    windowStart = today,
                    windowEnd = windowEnd,
                )

                // Persist instances and get IDs
                val persistedInstances = instances.map { instance ->
                    val instanceId = taskRepository.insert(instance)
                    instance.copy(id = instanceId)
                }

                // Partition into fixed-time and flexible
                val (fixedTimeInstances, flexibleInstances) = persistedInstances.partition { it.fixedTime != null }

                val zoneId = ZoneId.systemDefault()

                // Place fixed-time instances as busy slots
                val fixedTimeSlots = fixedTimeInstances.mapNotNull { instance ->
                    val date = instance.instanceDate ?: return@mapNotNull null
                    val time = instance.fixedTime ?: return@mapNotNull null
                    val start = date.atTime(time).atZone(zoneId).toInstant()
                    val end = start.plus(instance.estimatedDurationMinutes.toLong(), ChronoUnit.MINUTES)
                    TimeSlot(startTime = start, endTime = end)
                }

                // Create ScheduledBlocks for fixed-time instances directly
                for (instance in fixedTimeInstances) {
                    val date = instance.instanceDate ?: continue
                    val time = instance.fixedTime ?: continue
                    val start = date.atTime(time).atZone(zoneId).toInstant()
                    val end = start.plus(instance.estimatedDurationMinutes.toLong(), ChronoUnit.MINUTES)
                    val block = ScheduledBlock(taskId = instance.id, startTime = start, endTime = end)
                    val blockId = blockRepository.insert(block)
                    taskRepository.updateStatus(instance.id, TaskStatus.SCHEDULED)
                    syncManager.pushNewBlock(block.copy(id = blockId))
                }

                // Schedule flexible instances through the existing algorithm
                if (flexibleInstances.isNotEmpty()) {
                    val allTasks = taskRepository.getByStatuses(
                        listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
                    )
                    val existingBlocks = blockRepository.getByStatuses(
                        listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
                    )
                    val enabledCalendars = calendarSelectionRepository.getEnabled()
                    val busySlots = try {
                        val now = Instant.now()
                        val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
                        calendarRepository.getFreeBusySlots(
                            calendarIds = enabledCalendars.map { it.googleCalendarId },
                            timeMin = now,
                            timeMax = twoWeeksLater,
                        )
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val allBusySlots = busySlots + fixedTimeSlots

                    // Schedule each flexible instance through conflict resolution
                    for (instance in flexibleInstances) {
                        val result = taskScheduler.scheduleWithConflictResolution(
                            newTask = instance,
                            allTasks = allTasks,
                            existingBlocks = existingBlocks,
                            availability = availabilityList,
                            busySlots = allBusySlots,
                            startDate = today,
                            endDate = windowEnd,
                            zoneId = zoneId,
                            now = Instant.now(),
                        )

                        when (result) {
                            is SchedulingResult.Scheduled -> {
                                val blocksWithTaskId = result.blocks.map { it.copy(taskId = instance.id) }
                                val insertedIds = blockRepository.insertAll(blocksWithTaskId)
                                taskRepository.updateStatus(instance.id, TaskStatus.SCHEDULED)
                                blocksWithTaskId.zip(insertedIds).forEach { (block, id) ->
                                    syncManager.pushNewBlock(block.copy(id = id))
                                }
                            }
                            is SchedulingResult.NeedsReschedule -> {
                                val newBlocks = result.newBlocks.map { it.copy(taskId = instance.id) }
                                val movedBlocks = result.movedBlocks.map { it.second }
                                val allBlocks = newBlocks + movedBlocks
                                val insertedIds = blockRepository.insertAll(allBlocks)
                                taskRepository.updateStatus(instance.id, TaskStatus.SCHEDULED)
                                allBlocks.zip(insertedIds).forEach { (block, id) ->
                                    syncManager.pushNewBlock(block.copy(id = id))
                                }
                                for ((oldBlock, _) in result.movedBlocks) {
                                    oldBlock.googleCalendarEventId?.let {
                                        syncManager.deleteTaskEvents(oldBlock.taskId)
                                    }
                                }
                            }
                            else -> {
                                // Instance couldn't be scheduled — leave as PENDING
                            }
                        }
                    }
                }

                _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
                return@launch
            }

            // --- One-off task: validation ---
            val task = Task(
                id = if (taskId != -1L) taskId else 0,
                title = state.title.trim(),
                description = state.description.trim(),
                estimatedDurationMinutes = state.durationMinutes,
                quadrant = state.quadrant,
                deadline = state.deadline,
                dayPreference = state.dayPreference,
                splittable = state.splittable,
            )

            val availability = availabilityRepository.getAll()
            val validation = taskValidator.validate(task, availability)
            if (validation is ValidationResult.Invalid) {
                _uiState.update { it.copy(validationError = validation.reason, isSaving = false) }
                return@launch
            }

            // --- Scheduling: gather inputs before persisting to keep DB clean on failure ---
            // Gather scheduling inputs BEFORE persisting
            val allTasks = taskRepository.getByStatuses(
                listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
            )
            val existingBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            )
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val busySlots = try {
                val now = Instant.now()
                val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
                calendarRepository.getFreeBusySlots(
                    calendarIds = enabledCalendars.map { it.googleCalendarId },
                    timeMin = now,
                    timeMax = twoWeeksLater,
                )
            } catch (_: Exception) {
                emptyList()
            }

            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val isEditing = savedTaskId != -1L

            // For edits, exclude the task's own old blocks from existingBlocks
            val blocksForScheduling = if (isEditing) {
                existingBlocks.filter { it.taskId != savedTaskId }
            } else {
                existingBlocks
            }

            // For edits, also exclude the task's own calendar events from busySlots.
            // The free/busy API includes the task's existing event, which would
            // cause the scheduler to skip the task's current slot.
            val filteredBusySlots = if (isEditing) {
                val ownBlocks = existingBlocks.filter { it.taskId == savedTaskId }
                busySlots.filter { busy ->
                    ownBlocks.none { own ->
                        busy.startTime < own.endTime && busy.endTime > own.startTime
                    }
                }
            } else {
                busySlots
            }

            // Run scheduler with in-memory task (not yet persisted) — persist only on success
            val result = taskScheduler.scheduleWithConflictResolution(
                newTask = task,
                allTasks = allTasks,
                existingBlocks = blocksForScheduling,
                availability = availability,
                busySlots = filteredBusySlots,
                startDate = today,
                endDate = today.plusDays(14),
                zoneId = zoneId,
                now = Instant.now(),
            )

            appPreferences.setLastSyncTimestamp(Instant.now())

            // --- Calendar sync: push new blocks after successful persistence ---
            when (result) {
                is SchedulingResult.Scheduled -> {
                    // Scheduling succeeded — now persist
                    val savedId = persistTask(task, isEditing)
                    val blocksWithTaskId = result.blocks.map { it.copy(taskId = savedId) }
                    val insertedIds = blockRepository.insertAll(blocksWithTaskId)
                    taskRepository.updateStatus(savedId, TaskStatus.SCHEDULED)
                    blocksWithTaskId.zip(insertedIds).forEach { (block, id) ->
                        syncManager.pushNewBlock(block.copy(id = id))
                    }
                    _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
                }
                is SchedulingResult.NeedsReschedule -> {
                    val savedId = persistTask(task, isEditing)
                    // Only remap newBlocks to saved ID; movedBlocks keep their original taskId
                    val newBlocks = result.newBlocks.map { it.copy(taskId = savedId) }
                    val movedBlocks = result.movedBlocks.map { it.second }
                    val allBlocks = newBlocks + movedBlocks
                    val insertedIds = blockRepository.insertAll(allBlocks)
                    taskRepository.updateStatus(savedId, TaskStatus.SCHEDULED)
                    // Push new blocks to calendar
                    allBlocks.zip(insertedIds).forEach { (block, id) ->
                        syncManager.pushNewBlock(block.copy(id = id))
                    }
                    // Delete old calendar events for moved tasks
                    for ((oldBlock, _) in result.movedBlocks) {
                        oldBlock.googleCalendarEventId?.let {
                            syncManager.deleteTaskEvents(oldBlock.taskId)
                        }
                    }
                    _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
                }
                else -> {
                    // Scheduling failed — do NOT persist
                    _uiState.update {
                        it.copy(
                            schedulingResult = result,
                            savedSuccessfully = false,
                            isSaving = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun persistTask(task: Task, isEditing: Boolean): Long {
        return if (isEditing) {
            syncManager.deleteTaskEvents(savedTaskId)
            blockRepository.deleteByTaskId(savedTaskId)
            taskRepository.update(task.copy(id = savedTaskId))
            savedTaskId
        } else {
            val id = taskRepository.insert(task)
            savedTaskId = id
            id
        }
    }
}
