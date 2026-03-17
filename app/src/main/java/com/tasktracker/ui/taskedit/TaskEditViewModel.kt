package com.tasktracker.ui.taskedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import com.tasktracker.domain.scheduler.*
import com.tasktracker.domain.validation.TaskValidator
import com.tasktracker.domain.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
)

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
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L
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

    fun updateTitle(title: String) { _uiState.update { it.copy(title = title, validationError = null) } }
    fun updateDescription(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun updateDuration(minutes: Int) { _uiState.update { it.copy(durationMinutes = minutes, validationError = null) } }
    fun updateQuadrant(q: Quadrant) { _uiState.update { it.copy(quadrant = q) } }
    fun updateDeadline(deadline: Instant?) { _uiState.update { it.copy(deadline = deadline) } }
    fun updateDayPreference(pref: DayPreference) { _uiState.update { it.copy(dayPreference = pref) } }
    fun updateSplittable(splittable: Boolean) { _uiState.update { it.copy(splittable = splittable, validationError = null) } }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(validationError = "Title is required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

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

            val savedId = if (taskId != -1L) {
                taskRepository.update(task)
                taskId
            } else {
                taskRepository.insert(task)
            }

            // Run scheduler
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
            val savedTask = taskRepository.getById(savedId) ?: task.copy(id = savedId)

            val result = taskScheduler.scheduleWithConflictResolution(
                newTask = savedTask,
                allTasks = allTasks,
                existingBlocks = existingBlocks,
                availability = availability,
                busySlots = busySlots,
                startDate = today,
                endDate = today.plusDays(14),
                zoneId = zoneId,
            )

            when (result) {
                is SchedulingResult.Scheduled -> {
                    blockRepository.insertAll(result.blocks.map { it.copy(taskId = savedId) })
                    taskRepository.updateStatus(savedId, TaskStatus.SCHEDULED)
                    result.blocks.forEach { block ->
                        syncManager.pushNewBlock(block.copy(taskId = savedId))
                    }
                    _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
                }
                is SchedulingResult.NeedsReschedule -> {
                    blockRepository.insertAll(
                        (result.newBlocks + result.movedBlocks.map { it.second })
                            .map { it.copy(taskId = savedId) }
                    )
                    _uiState.update {
                        it.copy(
                            schedulingResult = result,
                            isSaving = false,
                        )
                    }
                }
                else -> {
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
}
