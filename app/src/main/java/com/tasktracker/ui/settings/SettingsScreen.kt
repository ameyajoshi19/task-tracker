package com.tasktracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.ui.components.AvailabilityEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text("Account", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signed in as", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            uiState.email ?: "Not signed in",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    TextButton(onClick = {
                        viewModel.signOut(context)
                        onSignedOut()
                    }) {
                        Text("Sign out")
                    }
                }
            }

            AvailabilityEditor(
                availabilities = uiState.availabilities,
                onUpdate = viewModel::updateAvailability,
            )

            Text("Calendars", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select which calendars to check for scheduling conflicts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (cal in uiState.calendars) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = cal.enabled,
                        onCheckedChange = { viewModel.toggleCalendar(cal) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(cal.calendarName, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
