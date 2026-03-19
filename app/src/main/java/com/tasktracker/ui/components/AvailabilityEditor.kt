package com.tasktracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.ui.theme.SortdColors
import kotlinx.coroutines.launch
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
    onCopyToAll: (DayOfWeek) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Column(
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
                    onCopyToAll = { copyDay ->
                        onCopyToAll(copyDay)
                        val dayName = copyDay.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "${dayName}'s schedule copied to all days"
                            )
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
    onCopyToAll: (DayOfWeek) -> Unit,
) {
    val dayEnabled = slots.any { it.enabled }
    val dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = if (!dayEnabled) Modifier.alpha(0.6f) else Modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row: toggle + day name + copy-to-all button
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (dayEnabled) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SortdColors.accent.copy(alpha = 0.1f),
                        modifier = Modifier.clickable { onCopyToAll(day) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy to all days",
                                tint = SortdColors.accentLight,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Copy to all",
                                fontSize = 11.sp,
                                color = SortdColors.accentLight,
                            )
                        }
                    }
                }
            }

            // Time slots (only when enabled)
            if (dayEnabled) {
                for ((index, slot) in slots.withIndex()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeSlotRow(
                            slot = slot,
                            onUpdate = onUpdateSlot,
                            showRemove = index > 0,
                            onRemove = { onRemoveSlot(slot) },
                        )
                    }
                }

                // Add slot button
                if (canAddSlot) {
                    TextButton(onClick = onAddSlot) {
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
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clickable { showStartPicker = true },
        ) {
            Text(
                text = slot.startTime.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = SortdColors.Dark.textPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Text(
            text = "to",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clickable { showEndPicker = true },
        ) {
            Text(
                text = slot.endTime.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = SortdColors.Dark.textPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
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
