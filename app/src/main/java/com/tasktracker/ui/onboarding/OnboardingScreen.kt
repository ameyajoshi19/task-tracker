package com.tasktracker.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.ui.components.AvailabilityEditor
import java.time.DayOfWeek

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.DONE) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = {
                when (uiState.step) {
                    OnboardingStep.AVAILABILITY -> 0.5f
                    OnboardingStep.CALENDARS -> 1.0f
                    OnboardingStep.DONE -> 1.0f
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        when (uiState.step) {
            OnboardingStep.AVAILABILITY -> AvailabilityStep(
                availabilities = uiState.availabilities,
                onUpdate = viewModel::updateAvailability,
                onAdd = viewModel::addAvailability,
                onRemove = viewModel::removeAvailability,
                onCopyToAll = viewModel::copyToAllDays,
                onNext = viewModel::saveAvailabilityAndProceed,
            )
            OnboardingStep.CALENDARS -> CalendarSelectionStep(
                calendars = uiState.calendars,
                isLoading = uiState.isLoadingCalendars,
                onToggle = viewModel::toggleCalendar,
                onFinish = viewModel::saveCalendarsAndFinish,
            )
            OnboardingStep.DONE -> { /* Will navigate away */ }
        }
    }
}

@Composable
private fun AvailabilityStep(
    availabilities: List<com.tasktracker.domain.model.UserAvailability>,
    onUpdate: (com.tasktracker.domain.model.UserAvailability) -> Unit,
    onAdd: (com.tasktracker.domain.model.UserAvailability) -> Unit,
    onRemove: (com.tasktracker.domain.model.UserAvailability) -> Unit,
    onCopyToAll: (DayOfWeek) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AvailabilityEditor(
            availabilities = availabilities,
            onUpdate = onUpdate,
            onAdd = onAdd,
            onRemove = onRemove,
            onCopyToAll = onCopyToAll,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun CalendarSelectionStep(
    calendars: List<CalendarSelectionState>,
    isLoading: Boolean,
    onToggle: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select Calendars", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose which calendars to check for conflicts",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(calendars) { cal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = cal.enabled,
                            onCheckedChange = { onToggle(cal.id) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Finish Setup")
        }
    }
}
