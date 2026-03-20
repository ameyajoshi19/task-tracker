// app/src/main/java/com/tasktracker/ui/components/TimeWheel.kt
package com.tasktracker.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun TimeWheel(
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

internal fun emitTime(hour: Int, minute: Int, isAm: Boolean, onTimeSelected: (LocalTime) -> Unit) {
    val h24 = if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12)
    onTimeSelected(LocalTime.of(h24, minute))
}
