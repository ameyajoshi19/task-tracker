package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.UserAvailability
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AvailabilityEditor(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Availability", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set your available hours for each day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        for (day in DayOfWeek.entries) {
            val availability = availabilities.find { it.dayOfWeek == day }
                ?: UserAvailability(dayOfWeek = day, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), enabled = false)

            AvailabilityRow(
                dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                availability = availability,
                onToggle = { onUpdate(availability.copy(enabled = !availability.enabled)) },
                onStartTimeChange = { onUpdate(availability.copy(startTime = it, enabled = true)) },
                onEndTimeChange = { onUpdate(availability.copy(endTime = it, enabled = true)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailabilityRow(
    dayName: String,
    availability: UserAvailability,
    onToggle: () -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onEndTimeChange: (LocalTime) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = availability.enabled,
            onCheckedChange = { onToggle() },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(48.dp),
        )
        if (availability.enabled) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showStartPicker = true }) {
                Text(availability.startTime.toString())
            }
            Text(" - ")
            TextButton(onClick = { showEndPicker = true }) {
                Text(availability.endTime.toString())
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = availability.startTime.hour,
            initialMinute = availability.startTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onStartTimeChange(LocalTime.of(state.hour, state.minute))
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(
            initialHour = availability.endTime.hour,
            initialMinute = availability.endTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onEndTimeChange(LocalTime.of(state.hour, state.minute))
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
