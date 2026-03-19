package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.ui.theme.SortdColors
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AvailabilityEditor(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    onAdd: (UserAvailability) -> Unit = {},
    onRemove: (UserAvailability) -> Unit = {},
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
            val daySlots = availabilities
                .filter { it.dayOfWeek == day }
                .sortedBy { it.startTime }
                .ifEmpty {
                    listOf(
                        UserAvailability(
                            dayOfWeek = day,
                            startTime = LocalTime.of(9, 0),
                            endTime = LocalTime.of(17, 0),
                            enabled = false,
                        )
                    )
                }

            AvailabilityDayGroup(
                day = day,
                slots = daySlots,
                onToggleDay = { enabled ->
                    daySlots.forEach { slot ->
                        if (slot.id == 0L) {
                            onAdd(slot.copy(enabled = enabled))
                        } else {
                            onUpdate(slot.copy(enabled = enabled))
                        }
                    }
                },
                onUpdateSlot = onUpdate,
                onAddSlot = {
                    val lastSlot = daySlots.last()
                    val newStart = lastSlot.endTime.plusHours(1).let {
                        if (it.isAfter(LocalTime.of(22, 0))) LocalTime.of(22, 0) else it
                    }
                    val newEnd = newStart.plusHours(4).let {
                        if (it.isAfter(LocalTime.of(23, 0)) || it.isBefore(newStart)) LocalTime.of(23, 0) else it
                    }
                    val hasOverlap = daySlots.any { existing ->
                        newStart < existing.endTime && newEnd > existing.startTime
                    }
                    if (!hasOverlap && newEnd > newStart) {
                        onAdd(
                            UserAvailability(
                                dayOfWeek = day,
                                startTime = newStart,
                                endTime = newEnd,
                                enabled = true,
                            )
                        )
                    }
                },
                onRemoveSlot = onRemove,
                canAddSlot = daySlots.last().endTime.isBefore(LocalTime.of(22, 0)),
            )
        }
    }
}

@Composable
private fun AvailabilityDayGroup(
    day: DayOfWeek,
    slots: List<UserAvailability>,
    onToggleDay: (Boolean) -> Unit,
    onUpdateSlot: (UserAvailability) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (UserAvailability) -> Unit,
    canAddSlot: Boolean,
) {
    val dayEnabled = slots.any { it.enabled }
    val dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // First slot row: toggle + day name + times
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = dayEnabled,
                onCheckedChange = { onToggleDay(it) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = dayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(48.dp),
            )
            if (dayEnabled && slots.isNotEmpty()) {
                TimeSlotRow(
                    slot = slots.first(),
                    onUpdate = onUpdateSlot,
                    showRemove = false,
                    onRemove = {},
                )
            }
        }

        // Additional slots: indented, with remove button
        if (dayEnabled) {
            for (slot in slots.drop(1)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 108.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeSlotRow(
                        slot = slot,
                        onUpdate = onUpdateSlot,
                        showRemove = true,
                        onRemove = { onRemoveSlot(slot) },
                    )
                }
            }

            // Add slot button
            if (canAddSlot) {
                TextButton(
                    onClick = onAddSlot,
                    modifier = Modifier.padding(start = 100.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add slot", color = SortdColors.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotRow(
    slot: UserAvailability,
    onUpdate: (UserAvailability) -> Unit,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showStartPicker = true }) {
            Text(slot.startTime.toString())
        }
        Text(" - ")
        TextButton(onClick = { showEndPicker = true }) {
            Text(slot.endTime.toString())
        }
        if (showRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove slot",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = slot.startTime.hour,
            initialMinute = slot.startTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(slot.copy(startTime = LocalTime.of(state.hour, state.minute), enabled = true))
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
            initialHour = slot.endTime.hour,
            initialMinute = slot.endTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(slot.copy(endTime = LocalTime.of(state.hour, state.minute), enabled = true))
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
