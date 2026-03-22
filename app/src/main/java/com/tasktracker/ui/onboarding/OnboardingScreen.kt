package com.tasktracker.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.ui.theme.SortdColors
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

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
                slots = uiState.slots,
                expandedSlotType = uiState.expandedSlotType,
                onToggleExpanded = viewModel::toggleExpanded,
                onUpdateSlot = viewModel::updateSlot,
                onNext = viewModel::proceedToCalendars,
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
    slots: Map<AvailabilitySlotType, List<AvailabilitySlot>>,
    expandedSlotType: AvailabilitySlotType?,
    onToggleExpanded: (AvailabilitySlotType) -> Unit,
    onUpdateSlot: (AvailabilitySlot) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Availability", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Set your available hours for scheduling",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (slotType in AvailabilitySlotType.entries) {
                val typeSlots = slots[slotType] ?: emptyList()
                val isExpanded = expandedSlotType == slotType
                val enabledCount = typeSlots.count { it.enabled }

                OnboardingSlotTypeCard(
                    slotType = slotType,
                    slots = typeSlots,
                    isExpanded = isExpanded,
                    enabledCount = enabledCount,
                    onToggleExpanded = { onToggleExpanded(slotType) },
                    onUpdateSlot = onUpdateSlot,
                )
            }
        }

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
private fun OnboardingSlotTypeCard(
    slotType: AvailabilitySlotType,
    slots: List<AvailabilitySlot>,
    isExpanded: Boolean,
    enabledCount: Int,
    onToggleExpanded: () -> Unit,
    onUpdateSlot: (AvailabilitySlot) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slotType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (enabledCount > 0) "$enabledCount days enabled" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (slot in slots.sortedBy { it.dayOfWeek }) {
                        OnboardingSlotDayRow(slot = slot, onUpdate = onUpdateSlot)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSlotDayRow(
    slot: AvailabilitySlot,
    onUpdate: (AvailabilitySlot) -> Unit,
) {
    val dayName = slot.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!slot.enabled) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(40.dp),
        )

        Switch(
            checked = slot.enabled,
            onCheckedChange = { onUpdate(slot.copy(enabled = it)) },
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (slot.enabled) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.clickable { showStartPicker = true },
            ) {
                Text(
                    text = slot.startTime.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Text(
                text = "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                    onUpdate(slot.copy(startTime = LocalTime.of(state.hour, state.minute)))
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
                    onUpdate(slot.copy(endTime = LocalTime.of(state.hour, state.minute)))
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
