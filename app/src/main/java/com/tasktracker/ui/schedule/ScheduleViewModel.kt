package com.tasktracker.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

data class ScheduleUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val items: List<ScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.DAILY,
)

enum class ViewMode { DAILY, WEEKLY }

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
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
            it.copy(viewMode = if (it.viewMode == ViewMode.DAILY) ViewMode.WEEKLY else ViewMode.DAILY)
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
            val (start, end) = when (state.viewMode) {
                ViewMode.DAILY -> {
                    val dayStart = state.selectedDate.atStartOfDay(zoneId).toInstant()
                    val dayEnd = state.selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                    dayStart to dayEnd
                }
                ViewMode.WEEKLY -> {
                    val weekStart = state.selectedDate.with(java.time.DayOfWeek.MONDAY)
                        .atStartOfDay(zoneId).toInstant()
                    val weekEnd = weekStart.atZone(zoneId).plusWeeks(1).toInstant()
                    weekStart to weekEnd
                }
            }

            val items = mutableListOf<ScheduleItem>()

            val blocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            ).filter { it.startTime >= start && it.startTime < end }

            for (block in blocks) {
                val task = taskRepository.getById(block.taskId)
                items.add(
                    ScheduleItem(
                        title = task?.title ?: "Task",
                        startTime = block.startTime,
                        endTime = block.endTime,
                        isTaskBlock = true,
                        taskId = block.taskId,
                        blockId = block.id,
                        quadrant = task?.quadrant,
                    )
                )
            }

            try {
                val enabledCalendars = calendarSelectionRepository.getEnabled()
                for (cal in enabledCalendars) {
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

            _uiState.update {
                it.copy(
                    items = items.sortedBy { item -> item.startTime },
                    isLoading = false,
                )
            }
        }
    }
}
