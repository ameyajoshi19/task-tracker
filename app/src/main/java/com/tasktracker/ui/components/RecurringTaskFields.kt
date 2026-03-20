// app/src/main/java/com/tasktracker/ui/components/RecurringTaskFields.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun RecurringTaskFields(
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    endDate: LocalDate?,
    onEndDateChange: (LocalDate?) -> Unit,
    isFixedTime: Boolean,
    onFixedTimeChange: (Boolean) -> Unit,
    fixedTime: LocalTime?,
    onFixedTimeValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Recurring task toggle (mirrors Splittable style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Recurring task",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Repeats on a schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = isRecurring,
                onCheckedChange = onRecurringChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SortdColors.accent,
                    checkedThumbColor = Color.White,
                ),
            )
        }

        // Indented recurring fields
        AnimatedVisibility(visible = isRecurring) {
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .drawStartBorder(SortdColors.accent)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // REPEAT EVERY
                IntervalInput(intervalDays = intervalDays, onIntervalChange = onIntervalChange)

                // START DATE
                var showStartDatePicker by remember { mutableStateOf(false) }
                var startDisplayMonth by remember(startDate) { mutableStateOf(YearMonth.from(startDate)) }
                DateField(
                    label = "START DATE",
                    date = startDate,
                    placeholder = "Set start date",
                    isActive = showStartDatePicker,
                    onClick = { showStartDatePicker = !showStartDatePicker },
                )
                AnimatedVisibility(visible = showStartDatePicker) {
                    CalendarGrid(
                        displayMonth = startDisplayMonth,
                        selectedDate = startDate,
                        today = LocalDate.now(),
                        onDateSelected = { date ->
                            onStartDateChange(date)
                            showStartDatePicker = false
                        },
                        onMonthChange = { startDisplayMonth = it },
                    )
                }

                // END DATE
                var showEndDatePicker by remember { mutableStateOf(false) }
                val endDisplayMonthInit = endDate?.let { YearMonth.from(it) }
                    ?: YearMonth.from(startDate.plusDays(30))
                var endDisplayMonth by remember(endDate) { mutableStateOf(endDisplayMonthInit) }
                DateField(
                    label = "END DATE",
                    date = endDate,
                    placeholder = "No end date",
                    isActive = showEndDatePicker,
                    onClick = { showEndDatePicker = !showEndDatePicker },
                )
                AnimatedVisibility(visible = showEndDatePicker) {
                    Column {
                        CalendarGrid(
                            displayMonth = endDisplayMonth,
                            selectedDate = endDate,
                            today = LocalDate.now(),
                            onDateSelected = { date ->
                                onEndDateChange(date)
                                showEndDatePicker = false
                            },
                            onMonthChange = { endDisplayMonth = it },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .clickable {
                                    onEndDateChange(null)
                                    showEndDatePicker = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Fixed time toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fixed time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Always at a specific time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = isFixedTime,
                        onCheckedChange = onFixedTimeChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = SortdColors.accent,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }

                // Time picker (nested indent when fixed time is ON)
                AnimatedVisibility(visible = isFixedTime) {
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 4.dp)
                            .drawStartBorder(SortdColors.accentLight)
                            .padding(start = 14.dp),
                    ) {
                        Text(
                            text = "TIME",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.sp, letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        var showTimePicker by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (showTimePicker) SortdColors.accent
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { showTimePicker = !showTimePicker }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("\uD83D\uDD50", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = fixedTime?.format(DateTimeFormatter.ofPattern("h:mm a"))
                                    ?: "Set time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fixedTime != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                        AnimatedVisibility(visible = showTimePicker) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                TimeWheel(
                                    selectedTime = fixedTime ?: LocalTime.of(9, 0),
                                    onTimeSelected = { time ->
                                        onFixedTimeValueChange(time)
                                        showTimePicker = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary banner (when recurring is ON, fixed time is ON, and time is configured)
        if (isRecurring && isFixedTime && fixedTime != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SortdColors.accent.copy(alpha = 0.1f))
                    .border(1.dp, SortdColors.accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\uD83D\uDD01", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Every $intervalDays day${if (intervalDays > 1) "s" else ""} at ${
                        fixedTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                    }, starting ${startDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = SortdColors.accentLight,
                )
            }
        }
    }
}

@Composable
private fun IntervalInput(
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
) {
    Column {
        Text(
            text = "REPEAT EVERY",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp, letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (intervalDays > 0) intervalDays.toString() else "",
                onValueChange = { text ->
                    val value = text.filter { it.isDigit() }.take(3).toIntOrNull()
                    if (value != null && value > 0) onIntervalChange(value)
                },
                modifier = Modifier.width(72.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SortdColors.accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    placeholder: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp, letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isActive) Modifier.border(1.dp, SortdColors.accent, RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\uD83D\uDCC5", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = date?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = if (date != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

private fun Modifier.drawStartBorder(color: Color) = this.then(
    Modifier.drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = 2.dp.toPx(),
        )
    }
)
