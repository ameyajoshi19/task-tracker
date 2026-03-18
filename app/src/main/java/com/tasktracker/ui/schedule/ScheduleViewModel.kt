package com.tasktracker.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class ScheduleItem(
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val isTaskBlock: Boolean,
    val taskId: Long? = null,
    val blockId: Long? = null,
    val quadrant: Quadrant? = null,
    val isCompleted: Boolean = false,
)

data class ScheduleUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val items: List<ScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.DAILY,
)

enum class ViewMode { DAILY, ALL }

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadSchedule()
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.DAILY) ViewMode.ALL else ViewMode.DAILY)
        }
        loadSchedule()
    }

    fun navigateDay(forward: Boolean) {
        _uiState.update {
            it.copy(selectedDate = if (forward) it.selectedDate.plusDays(1) else it.selectedDate.minusDays(1))
        }
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val state = _uiState.value
            val zoneId = ZoneId.systemDefault()
            val useTimeRange = state.viewMode == ViewMode.DAILY
            val (start, end) = if (useTimeRange) {
                val dayStart = state.selectedDate.atStartOfDay(zoneId).toInstant()
                val dayEnd = state.selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                dayStart to dayEnd
            } else {
                null to null
            }

            val items = mutableListOf<ScheduleItem>()

            val blocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            ).let { allBlocks ->
                if (start != null && end != null) {
                    allBlocks.filter { it.startTime >= start && it.startTime < end }
                } else {
                    allBlocks
                }
            }

            for (block in blocks) {
                val task = taskRepository.getById(block.taskId)
                val isCompleted = task?.status == TaskStatus.COMPLETED
                val title = if (isCompleted) {
                    "Completed: ${task?.title ?: "Task"}"
                } else {
                    task?.title ?: "Task"
                }
                items.add(
                    ScheduleItem(
                        title = title,
                        startTime = block.startTime,
                        endTime = block.endTime,
                        isTaskBlock = true,
                        taskId = block.taskId,
                        blockId = block.id,
                        quadrant = task?.quadrant,
                        isCompleted = isCompleted,
                    )
                )
            }

            if (start != null && end != null) {
                try {
                    val enabledCalendars = calendarSelectionRepository.getEnabled()
                    val taskCalendarId = appPreferences.taskCalendarId.first()
                    for (cal in enabledCalendars) {
                        // Skip the task calendar — its events are already shown as task blocks
                        if (cal.googleCalendarId == taskCalendarId) continue
                        val events = calendarRepository.getEvents(cal.googleCalendarId, start, end)
                        items.addAll(
                            events.map { event ->
                                ScheduleItem(
                                    title = event.title.ifBlank { "Busy" },
                                    startTime = event.startTime,
                                    endTime = event.endTime,
                                    isTaskBlock = false,
                                )
                            }
                        )
                    }
                } catch (_: Exception) {
                    // Offline — show only task blocks
                }
            }

            _uiState.update {
                it.copy(
                    items = items.sortedBy { item -> item.startTime },
                    isLoading = false,
                )
            }
        }
    }
}
