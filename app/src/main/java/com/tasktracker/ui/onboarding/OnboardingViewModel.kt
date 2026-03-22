package com.tasktracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.model.SyncInterval
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { AVAILABILITY, CALENDARS, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.AVAILABILITY,
    val slots: Map<AvailabilitySlotType, List<AvailabilitySlot>> = emptyMap(),
    val expandedSlotType: AvailabilitySlotType? = null,
    val calendars: List<CalendarSelectionState> = emptyList(),
    val isLoadingCalendars: Boolean = false,
)

data class CalendarSelectionState(
    val id: String,
    val name: String,
    val color: String,
    val enabled: Boolean,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val availabilitySlotRepository: AvailabilitySlotRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val syncScheduler: SyncScheduler,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            availabilitySlotRepository.observeAll().collect { slots ->
                _uiState.update { state ->
                    state.copy(slots = slots.groupBy { it.slotType })
                }
            }
        }
    }

    fun toggleExpanded(slotType: AvailabilitySlotType) {
        _uiState.update { state ->
            state.copy(
                expandedSlotType = if (state.expandedSlotType == slotType) null else slotType,
            )
        }
    }

    fun updateSlot(slot: AvailabilitySlot) {
        viewModelScope.launch {
            availabilitySlotRepository.update(slot)
        }
    }

    fun proceedToCalendars() {
        _uiState.update { it.copy(step = OnboardingStep.CALENDARS, isLoadingCalendars = true) }
        viewModelScope.launch { loadCalendars() }
    }

    private suspend fun loadCalendars() {
        try {
            val calendars = calendarRepository.listCalendars()
            val taskCalId = appPreferences.taskCalendarId.first()
            val filtered = calendars.filter { cal ->
                cal.id != taskCalId &&
                    cal.name != "Sortd Task Tracker" &&
                    cal.name != "Task Tracker"
            }
            _uiState.update { state ->
                state.copy(
                    isLoadingCalendars = false,
                    calendars = filtered.map {
                        CalendarSelectionState(
                            id = it.id,
                            name = it.name,
                            color = it.color,
                            enabled = true,
                        )
                    },
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingCalendars = false) }
        }
    }

    fun toggleCalendar(calendarId: String) {
        _uiState.update { state ->
            state.copy(
                calendars = state.calendars.map {
                    if (it.id == calendarId) it.copy(enabled = !it.enabled) else it
                },
            )
        }
    }

    fun saveCalendarsAndFinish() {
        viewModelScope.launch {
            for (cal in _uiState.value.calendars) {
                calendarSelectionRepository.insert(
                    CalendarSelection(
                        googleCalendarId = cal.id,
                        calendarName = cal.name,
                        calendarColor = cal.color,
                        enabled = cal.enabled,
                    )
                )
            }
            try {
                val calendarId = calendarRepository.getOrCreateTaskCalendar()
                appPreferences.setTaskCalendarId(calendarId)
            } catch (_: Exception) { /* Will retry on first sync */ }
            appPreferences.setOnboardingCompleted(true)
            syncScheduler.schedule(SyncInterval.FIFTEEN_MINUTES)
            _uiState.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
}
