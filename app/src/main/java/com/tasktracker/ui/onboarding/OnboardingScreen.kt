package com.tasktracker.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.DONE) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = {
                when (uiState.step) {
                    OnboardingStep.SIGN_IN -> 0.33f
                    OnboardingStep.AVAILABILITY -> 0.66f
                    OnboardingStep.CALENDARS -> 1.0f
                    OnboardingStep.DONE -> 1.0f
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        when (uiState.step) {
            OnboardingStep.SIGN_IN -> SignInStep(
                isSigningIn = uiState.isSigningIn,
                error = uiState.signInError,
                onSignIn = {
                    viewModel.setSigningIn()
                    signInLauncher.launch(viewModel.getSignInIntent())
                },
            )
            OnboardingStep.AVAILABILITY -> AvailabilityStep(
                availabilities = uiState.availabilities,
                onUpdate = viewModel::updateAvailability,
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
private fun SignInStep(
    isSigningIn: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Task Tracker", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Sign in with Google to sync your tasks with Google Calendar",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSignIn,
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Sign in with Google")
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AvailabilityStep(
    availabilities: List<com.tasktracker.domain.model.UserAvailability>,
    onUpdate: (com.tasktracker.domain.model.UserAvailability) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AvailabilityEditor(
            availabilities = availabilities,
            onUpdate = onUpdate,
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
