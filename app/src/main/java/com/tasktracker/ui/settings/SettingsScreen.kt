package com.tasktracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.SyncInterval
import com.tasktracker.ui.components.AvailabilityEditor
import com.tasktracker.ui.theme.SortdColors

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
                        val name = uiState.displayName
                        Text(
                            "Signed in as",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (name != null) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            uiState.email ?: "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (name != null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
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
                onAdd = viewModel::addAvailability,
                onRemove = viewModel::removeAvailability,
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

            // Sync interval section
            Text("Background Sync", style = MaterialTheme.typography.titleMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = uiState.syncInterval.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sync Interval") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    SyncInterval.entries.forEach { interval ->
                        DropdownMenuItem(
                            text = { Text(interval.label) },
                            onClick = {
                                viewModel.updateSyncInterval(interval)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Text(
                "More frequent syncs keep your schedule up to date but use more battery in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Theme section
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Light", "Dark", "Auto").forEach { label ->
                    val mode = label.lowercase()
                    val isActive = uiState.themeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isActive) SortdColors.accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) SortdColors.accent
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { viewModel.updateThemeMode(mode) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) SortdColors.accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
