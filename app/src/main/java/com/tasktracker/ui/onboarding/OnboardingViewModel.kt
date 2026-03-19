package com.tasktracker.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.model.SyncInterval
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

enum class OnboardingStep { SIGN_IN, AVAILABILITY, CALENDARS, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SIGN_IN,
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
    val email: String? = null,
    val availabilities: List<UserAvailability> = DayOfWeek.entries.map { day ->
        val isWeekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        UserAvailability(
            dayOfWeek = day,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            enabled = isWeekday,
        )
    },
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
    val authManager: GoogleAuthManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val syncScheduler: SyncScheduler,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val result = authManager.handleSignInResult(account)
            result.fold(
                onSuccess = { email ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            email = email,
                            step = OnboardingStep.AVAILABILITY,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = error.message)
                    }
                },
            )
        } catch (e: ApiException) {
            _uiState.update {
                it.copy(isSigningIn = false, signInError = "Sign-in failed: ${e.statusCode}")
            }
        }
    }

    fun setSigningIn() {
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
    }

    fun updateAvailability(availability: UserAvailability) {
        _uiState.update { state ->
            state.copy(
                availabilities = state.availabilities.map {
                    if (it.id == availability.id) availability else it
                },
            )
        }
    }

    fun addAvailability(availability: UserAvailability) {
        _uiState.update { state ->
            val newId = (state.availabilities.minOfOrNull { it.id } ?: 0L) - 1
            state.copy(
                availabilities = state.availabilities + availability.copy(id = newId),
            )
        }
    }

    fun removeAvailability(availability: UserAvailability) {
        _uiState.update { state ->
            state.copy(
                availabilities = state.availabilities.filter { it.id != availability.id },
            )
        }
    }

    fun saveAvailabilityAndProceed() {
        viewModelScope.launch {
            for (a in _uiState.value.availabilities) {
                availabilityRepository.insert(a)
            }
            _uiState.update { it.copy(step = OnboardingStep.CALENDARS, isLoadingCalendars = true) }
            loadCalendars()
        }
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
            syncScheduler.schedule(SyncInterval.THIRTY_MINUTES)
            _uiState.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
}
