package com.tasktracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val email: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        authManager.signedInEmail,
        availabilityRepository.observeAll(),
        calendarSelectionRepository.observeAll(),
    ) { email, availabilities, calendars ->
        SettingsUiState(
            email = email,
            availabilities = availabilities,
            calendars = calendars,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun updateAvailability(availability: UserAvailability) {
        viewModelScope.launch {
            availabilityRepository.update(availability)
        }
    }

    fun toggleCalendar(calendar: CalendarSelection) {
        viewModelScope.launch {
            calendarSelectionRepository.update(calendar.copy(enabled = !calendar.enabled))
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authManager.signOut()
        }
    }
}
