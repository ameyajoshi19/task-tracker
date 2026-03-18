// app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

@Composable
private fun CalendarGrid(
    displayMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        // Month header with nav arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row {
                Text(
                    "‹",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable { onMonthChange(displayMonth.minusMonths(1)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 18.sp,
                    color = SortdColors.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "›",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable { onMonthChange(displayMonth.plusMonths(1)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 18.sp,
                    color = SortdColors.accent,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Calendar days
        val firstDay = displayMonth.atDay(1)
        val startPadding = firstDay.dayOfWeek.value % 7 // Sunday = 0
        val daysInMonth = displayMonth.lengthOfMonth()

        val totalCells = startPadding + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startPadding + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = displayMonth.atDay(dayNum)
                        val isPast = date.isBefore(today)
                        val isToday = date == today
                        val isSelected = date == selectedDate

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    when {
                                        isSelected -> Modifier.background(
                                            Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight))
                                        )
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (!isPast) Modifier.clickable { onDateSelected(date) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        isToday -> SortdColors.accent
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (isToday && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(SortdColors.accent),
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWheel(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
) {
    var hour by remember(selectedTime) { mutableIntStateOf(if (selectedTime.hour % 12 == 0) 12 else selectedTime.hour % 12) }
    var minute by remember(selectedTime) { mutableIntStateOf(selectedTime.minute / 15 * 15) }
    var isAm by remember(selectedTime) { mutableStateOf(selectedTime.hour < 12) }

    val hourOptions = (1..12).toList()
    val minuteOptions = listOf(0, 15, 30, 45)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Hour
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                listOf(hour - 1, hour, hour + 1).forEach { h ->
                    val display = when {
                        h < 1 -> h + 12
                        h > 12 -> h - 12
                        else -> h
                    }
                    val isSel = h == hour
                    Text(
                        text = display.toString(),
                        fontSize = if (isSel) 26.sp else 18.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Light,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSel) Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SortdColors.accent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .clickable {
                                hour = display
                                emitTime(hour, minute, isAm, onTimeSelected)
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text("HOUR", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            Text(":", fontSize = 22.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))

            // Minute
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                val curIdx = minuteOptions.indexOf(minute).coerceAtLeast(0)
                listOf(curIdx - 1, curIdx, curIdx + 1).forEach { idx ->
                    val wrappedIdx = (idx + minuteOptions.size) % minuteOptions.size
                    val isSel = idx == curIdx
                    Text(
                        text = minuteOptions[wrappedIdx].toString().padStart(2, '0'),
                        fontSize = if (isSel) 26.sp else 18.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Light,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSel) Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SortdColors.accent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .clickable {
                                minute = minuteOptions[wrappedIdx]
                                emitTime(hour, minute, isAm, onTimeSelected)
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text("MIN", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            Spacer(Modifier.width(8.dp))

            // AM/PM
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val amShape = RoundedCornerShape(8.dp)
                Text(
                    text = "AM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(amShape)
                        .then(
                            if (isAm) Modifier.background(Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight)))
                            else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        )
                        .clickable {
                            isAm = true
                            emitTime(hour, minute, isAm, onTimeSelected)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    text = "PM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!isAm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(amShape)
                        .then(
                            if (!isAm) Modifier.background(Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight)))
                            else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        )
                        .clickable {
                            isAm = false
                            emitTime(hour, minute, isAm, onTimeSelected)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Summary
        val displayTime = LocalTime.of(
            if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12),
            minute,
        )
        Text(
            text = displayTime.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = SortdColors.accentLight,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun emitTime(hour: Int, minute: Int, isAm: Boolean, onTimeSelected: (LocalTime) -> Unit) {
    val h24 = if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12)
    onTimeSelected(LocalTime.of(h24, minute))
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
