package com.tasktracker.ui.taskedit

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.SchedulingResult
import com.tasktracker.ui.components.*
import com.tasktracker.ui.theme.SortdColors

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

            // Title
            Column {
                Text(
                    text = "TITLE",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.validationError != null && uiState.title.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SortdColors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            // Tag
            TagSelector(
                tags = uiState.tags,
                selectedTagId = uiState.selectedTagId,
                onTagSelected = viewModel::updateSelectedTag,
                onCreateTag = viewModel::createTag,
            )

            // Duration
            DurationPicker(
                durationMinutes = uiState.durationMinutes,
                onDurationChange = viewModel::updateDuration,
                suggestedMinutes = uiState.suggestedDurationMinutes,
                suggestedKeyword = uiState.suggestedDurationKeyword,
            )

            // Priority Quadrant
            QuadrantSelector(
                selected = uiState.quadrant,
                onSelect = viewModel::updateQuadrant,
                suggestedQuadrant = uiState.suggestedQuadrant,
                suggestionReason = uiState.suggestedQuadrantReason,
            )

            // Deadline
            DeadlinePicker(
                deadline = uiState.deadline,
                onDeadlineChange = viewModel::updateDeadline,
            )

            // Recurring task — placed before scheduling options since it controls their visibility
            RecurringTaskFields(
                isRecurring = uiState.isRecurring,
                onRecurringChange = viewModel::updateRecurring,
                intervalDays = uiState.intervalDays,
                onIntervalChange = viewModel::updateIntervalDays,
                startDate = uiState.startDate,
                onStartDateChange = viewModel::updateStartDate,
                endDate = uiState.endDate,
                onEndDateChange = viewModel::updateEndDate,
                isFixedTime = uiState.isFixedTime,
                onFixedTimeChange = viewModel::updateFixedTime,
                fixedTime = uiState.fixedTime,
                onFixedTimeValueChange = viewModel::updateFixedTimeValue,
            )

            // Day Preference — not applicable for recurring tasks (they use interval days)
            if (!uiState.isRecurring) {
                DayPreferenceSelector(
                    selected = uiState.dayPreference,
                    onSelect = viewModel::updateDayPreference,
                )
            }

            // Availability Slot — not applicable for fixed-time tasks (they have an exact time)
            if (!(uiState.isRecurring && uiState.isFixedTime)) {
                AvailabilitySlotSelector(
                    selected = uiState.selectedAvailabilitySlot,
                    enabledSlotTypes = uiState.enabledSlotTypes,
                    onSelect = viewModel::updateAvailabilitySlot,
                )
            }

            // Splittable toggle — not applicable for fixed-time recurring tasks
            if (!(uiState.isRecurring && uiState.isFixedTime)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(12.dp, 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Splittable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Allow splitting across time blocks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = uiState.splittable,
                        onCheckedChange = viewModel::updateSplittable,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = SortdColors.accent,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }
            }

            // Validation errors
            if (uiState.validationError != null) {
                Text(
                    text = uiState.validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val result = uiState.schedulingResult
            if (result is SchedulingResult.DeadlineAtRisk) {
                Text(text = result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (result is SchedulingResult.NoSlotsAvailable) {
                Text(text = result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // CTA Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (!uiState.isSaving) Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight))
                        else Brush.linearGradient(listOf(SortdColors.accent.copy(alpha = 0.5f), SortdColors.accentLight.copy(alpha = 0.5f)))
                    )
                    .then(if (!uiState.isSaving) Modifier.clickable(onClick = viewModel::save) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = if (uiState.isEditing) "Update" else "Sort it ⚡",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
