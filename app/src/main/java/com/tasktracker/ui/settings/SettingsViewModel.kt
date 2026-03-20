package com.tasktracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.DailySummaryScheduler
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

data class SettingsUiState(
    val email: String? = null,
    val displayName: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
    val syncInterval: SyncInterval = SyncInterval.THIRTY_MINUTES,
    val themeMode: String = "auto",
    val dailySummaryEnabled: Boolean = true,
    val dailySummaryTime: String = "08:00",
) {
    val activeDayCount: Int
        get() = availabilities.filter { it.enabled }.map { it.dayOfWeek }.distinct().size
    val syncedCalendarCount: Int
        get() = calendars.count { it.enabled }
    val themeModeLabel: String
        get() = themeMode.replaceFirstChar { it.uppercase() }
    val dailySummarySubtitle: String
        get() = if (dailySummaryEnabled) dailySummaryTime else "Off"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val appPreferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val dailySummaryScheduler: DailySummaryScheduler,
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SettingsUiState> = combine(
        authManager.signedInEmail,
        availabilityRepository.observeAll(),
        calendarSelectionRepository.observeAll(),
        appPreferences.syncInterval,
        appPreferences.themeMode,
        appPreferences.taskCalendarId,
        authManager.signedInDisplayName,
        appPreferences.dailySummaryEnabled,
        appPreferences.dailySummaryTime,
    ) { values ->
        // Index: 0=email, 1=availabilities, 2=calendars, 3=interval, 4=theme, 5=taskCalId, 6=displayName, 7=summaryEnabled, 8=summaryTime
        val email = values[0] as String?
        val availabilities = values[1] as List<UserAvailability>
        val allCalendars = values[2] as List<CalendarSelection>
        val interval = values[3] as SyncInterval
        val theme = values[4] as String
        val taskCalId = values[5] as String?
        val displayName = values[6] as String?
        val summaryEnabled = values[7] as Boolean
        val summaryTime = values[8] as String

        val calendars = allCalendars.filter { it.googleCalendarId != taskCalId }

        SettingsUiState(
            email = email,
            displayName = displayName,
            availabilities = availabilities,
            calendars = calendars,
            syncInterval = interval,
            themeMode = theme,
            dailySummaryEnabled = summaryEnabled,
            dailySummaryTime = summaryTime,
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

    fun addAvailability(availability: UserAvailability) {
        viewModelScope.launch {
            availabilityRepository.insert(availability)
        }
    }

    fun removeAvailability(availability: UserAvailability) {
        viewModelScope.launch {
            availabilityRepository.delete(availability)
        }
    }

    fun toggleCalendar(calendar: CalendarSelection) {
        viewModelScope.launch {
            calendarSelectionRepository.update(calendar.copy(enabled = !calendar.enabled))
        }
    }

    fun updateSyncInterval(interval: SyncInterval) {
        viewModelScope.launch {
            appPreferences.setSyncInterval(interval)
            syncScheduler.schedule(interval)
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)
        }
    }

    fun copyToAllDays(dayOfWeek: DayOfWeek) {
        viewModelScope.launch {
            availabilityRepository.copyToAllDays(dayOfWeek)
        }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setDailySummaryEnabled(enabled)
            if (enabled) {
                val timeStr = appPreferences.dailySummaryTime.first()
                dailySummaryScheduler.schedule(LocalTime.parse(timeStr))
            } else {
                dailySummaryScheduler.cancel()
            }
        }
    }

    fun setDailySummaryTime(time: String) {
        viewModelScope.launch {
            appPreferences.setDailySummaryTime(time)
            dailySummaryScheduler.schedule(LocalTime.parse(time))
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authManager.signOut()
        }
    }
}
