package com.tasktracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilitySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AvailabilitySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Availability") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Set your available hours for scheduling",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            for (slotType in AvailabilitySlotType.entries) {
                val slots = uiState.slots[slotType] ?: emptyList()
                val isExpanded = uiState.expandedSlotType == slotType
                val enabledCount = slots.count { it.enabled }

                SlotTypeCard(
                    slotType = slotType,
                    slots = slots,
                    isExpanded = isExpanded,
                    enabledCount = enabledCount,
                    onToggleExpanded = { viewModel.toggleExpanded(slotType) },
                    onUpdateSlot = viewModel::updateSlot,
                    onCopyToAllDays = { sourceDay ->
                        viewModel.copyToAllDays(slotType, sourceDay)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "${slotType.displayName}: copied ${sourceDay.getDisplayName(TextStyle.SHORT, Locale.getDefault())} to all days"
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SlotTypeCard(
    slotType: AvailabilitySlotType,
    slots: List<AvailabilitySlot>,
    isExpanded: Boolean,
    enabledCount: Int,
    onToggleExpanded: () -> Unit,
    onUpdateSlot: (AvailabilitySlot) -> Unit,
    onCopyToAllDays: (DayOfWeek) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // Header row
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

            // Expandable day rows
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val sortedSlots = slots.sortedBy { it.dayOfWeek }
                    for (slot in sortedSlots) {
                        SlotDayRow(
                            slot = slot,
                            onUpdate = onUpdateSlot,
                            onCopyToAll = onCopyToAllDays,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDayRow(
    slot: AvailabilitySlot,
    onUpdate: (AvailabilitySlot) -> Unit,
    onCopyToAll: (DayOfWeek) -> Unit,
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

            Spacer(Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SortdColors.accent.copy(alpha = 0.1f),
                modifier = Modifier.clickable { onCopyToAll(slot.dayOfWeek) },
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy to all days",
                    tint = SortdColors.accentLight,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(6.dp),
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
