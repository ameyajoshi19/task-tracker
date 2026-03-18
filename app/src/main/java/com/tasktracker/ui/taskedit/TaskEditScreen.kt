package com.tasktracker.ui.taskedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.SchedulingResult
import com.tasktracker.ui.components.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReschedule: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onNavigateBack()
    }

    LaunchedEffect(uiState.schedulingResult) {
        if (uiState.schedulingResult is SchedulingResult.NeedsReschedule) {
            onNavigateToReschedule()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Task" else "New Task") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Stale data warning
            if (uiState.staleDataWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "Calendar data may be outdated. Schedule might conflict with recent calendar changes.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.validationError != null && uiState.title.isBlank(),
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            DurationPicker(
                durationMinutes = uiState.durationMinutes,
                onDurationChange = viewModel::updateDuration,
            )

            QuadrantSelector(
                selected = uiState.quadrant,
                onSelect = viewModel::updateQuadrant,
            )

            DeadlinePicker(
                deadline = uiState.deadline,
                onDeadlineChange = viewModel::updateDeadline,
            )

            DayPreferenceSelector(
                selected = uiState.dayPreference,
                onSelect = viewModel::updateDayPreference,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Splittable", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Allow splitting across multiple time blocks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.splittable,
                    onCheckedChange = viewModel::updateSplittable,
                )
            }

            if (uiState.validationError != null) {
                Text(
                    text = uiState.validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val result = uiState.schedulingResult
            if (result is SchedulingResult.DeadlineAtRisk) {
                Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (result is SchedulingResult.NoSlotsAvailable) {
                Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isEditing) "Update" else "Create & Schedule")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlinePicker(
    deadline: Instant?,
    onDeadlineChange: (Instant?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val zoneId = ZoneId.systemDefault()

    Column {
        Text("Deadline (optional)", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(
                    deadline?.let {
                        LocalDateTime.ofInstant(it, zoneId)
                            .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    } ?: "Set deadline"
                )
            }
            if (deadline != null) {
                TextButton(onClick = { onDeadlineChange(null) }) {
                    Text("Clear")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.toEpochMilli()
                ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // DatePicker returns midnight UTC — convert to local date
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val existingTime = deadline?.let {
            LocalDateTime.ofInstant(it, zoneId).toLocalTime()
        }
        val timePickerState = rememberTimePickerState(
            initialHour = existingTime?.hour ?: 17,
            initialMinute = existingTime?.minute ?: 0,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate?.let { date ->
                        val localDateTime = date.atTime(
                            timePickerState.hour,
                            timePickerState.minute,
                        )
                        onDeadlineChange(localDateTime.atZone(zoneId).toInstant())
                    }
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }
}
