// app/src/main/java/com/tasktracker/ui/components/CalendarGrid.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CalendarGrid(
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
