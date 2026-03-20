// app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun DeadlinePicker(
    deadline: Instant?,
    onDeadlineChange: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember(deadline) {
        mutableStateOf(deadline?.let { LocalDateTime.ofInstant(it, zoneId).toLocalDate() })
    }
    var selectedTime by remember(deadline) {
        mutableStateOf(deadline?.let { LocalDateTime.ofInstant(it, zoneId).toLocalTime() })
    }
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }

    Column(modifier = modifier) {
        Text(
            text = "DEADLINE",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Date and time trigger fields
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerField(
                icon = "📅",
                text = selectedDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                placeholder = "Set date",
                isActive = showDatePicker,
                onClick = {
                    showDatePicker = !showDatePicker
                    showTimePicker = false
                },
                modifier = Modifier.weight(1f),
            )
            TriggerField(
                icon = "🕐",
                text = selectedTime?.format(DateTimeFormatter.ofPattern("h:mm a")),
                placeholder = "Set time",
                isActive = showTimePicker,
                onClick = {
                    showTimePicker = !showTimePicker
                    showDatePicker = false
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Date picker section
        AnimatedVisibility(visible = showDatePicker) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Quick date chips
                val today = LocalDate.now(zoneId)
                val quickDates = listOf(
                    "Today" to today,
                    "Tomorrow" to today.plusDays(1),
                    "This Fri" to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)),
                    "Next Week" to today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickDates.forEach { (label, date) ->
                        QuickChip(
                            label = label,
                            isActive = selectedDate == date,
                            onClick = {
                                selectedDate = date
                                updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                                showDatePicker = false
                                if (selectedTime == null) showTimePicker = true
                            },
                        )
                    }
                    QuickChip(
                        label = "No deadline",
                        isActive = false,
                        onClick = {
                            selectedDate = null
                            selectedTime = null
                            onDeadlineChange(null)
                            showDatePicker = false
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Calendar grid
                CalendarGrid(
                    displayMonth = displayMonth,
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = { date ->
                        selectedDate = date
                        updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                        showDatePicker = false
                        if (selectedTime == null) showTimePicker = true
                    },
                    onMonthChange = { displayMonth = it },
                )
            }
        }

        // Time picker section
        AnimatedVisibility(visible = showTimePicker) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Quick time chips
                val quickTimes = listOf(
                    "9 AM" to LocalTime.of(9, 0),
                    "12 PM" to LocalTime.of(12, 0),
                    "3 PM" to LocalTime.of(15, 0),
                    "5 PM" to LocalTime.of(17, 0),
                    "EOD" to LocalTime.of(23, 59),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickTimes.forEach { (label, time) ->
                        QuickChip(
                            label = label,
                            isActive = selectedTime == time,
                            onClick = {
                                selectedTime = time
                                updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                                showTimePicker = false
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Time wheel
                TimeWheel(
                    selectedTime = selectedTime ?: LocalTime.of(17, 0),
                    onTimeSelected = { time ->
                        selectedTime = time
                        updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                    },
                )
            }
        }

        // Confirmed deadline summary
        if (selectedDate != null && selectedTime != null && !showDatePicker && !showTimePicker) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SortdColors.accent.copy(alpha = 0.1f))
                    .border(1.dp, SortdColors.accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📅 ${selectedDate!!.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} at ${selectedTime!!.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = SortdColors.accentLight,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .clickable {
                            selectedDate = null
                            selectedTime = null
                            onDeadlineChange(null)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TriggerField(
    icon: String,
    text: String?,
    placeholder: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isActive) Modifier.border(
                    1.dp, SortdColors.accent, RoundedCornerShape(12.dp)
                )
                else Modifier.border(
                    1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)
                )
            )
            .clickable(onClick = onClick)
            .padding(10.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text ?: placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = if (text != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (isActive) SortdColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .then(
                if (isActive) Modifier
                    .background(SortdColors.accent.copy(alpha = 0.2f))
                    .border(1.dp, SortdColors.accent, shape)
                else Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontSize = 11.sp,
    )
}

private fun updateDeadline(
    date: LocalDate?,
    time: LocalTime?,
    zoneId: ZoneId,
    onDeadlineChange: (Instant?) -> Unit,
) {
    if (date != null && time != null) {
        onDeadlineChange(date.atTime(time).atZone(zoneId).toInstant())
    } else if (date != null) {
        // Set tentative deadline at end of day so suggestion logic can work
        onDeadlineChange(date.atTime(23, 59).atZone(zoneId).toInstant())
    }
}
