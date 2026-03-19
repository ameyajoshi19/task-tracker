package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.ui.theme.SortdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskCard(
    taskInfo: TaskWithScheduleInfo,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = taskInfo.task
    val isCompleted = task.status == TaskStatus.COMPLETED
    val (colorStart, colorEnd) = quadrantColors(task.quadrant)
    val isDark = isSystemInDarkTheme()
    val cardBackground = if (isDark) {
        colorStart.copy(alpha = 0.12f)
    } else {
        colorStart.copy(alpha = 0.08f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.45f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBackground)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gradient quadrant dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(12.dp))

        // Task info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Scheduled time
            if (taskInfo.nextBlockStart != null && taskInfo.nextBlockEnd != null) {
                val extra = if (taskInfo.blockCount > 1) " (+${taskInfo.blockCount - 1} more)" else ""
                Text(
                    text = formatScheduledTime(taskInfo.nextBlockStart, taskInfo.nextBlockEnd) + extra,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            } else if (!isCompleted) {
                Text(
                    text = "Not scheduled",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }

            // Deadline
            if (task.deadline != null) {
                val isUrgent = isDeadlineUrgent(task.deadline)
                Text(
                    text = formatDeadline(task.deadline),
                    fontSize = 11.sp,
                    color = if (isUrgent) SortdColors.deadlineWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // Duration badge
                Text(
                    text = formatDuration(task.estimatedDurationMinutes),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorStart,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorStart.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // Check button
        if (!isCompleted) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.12f))
                    .border(1.5.dp, colorStart.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onComplete),
                contentAlignment = Alignment.Center,
            ) {
                Text("○", fontSize = 14.sp, color = colorStart)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Completed",
                    modifier = Modifier.size(16.dp),
                    tint = colorStart,
                )
            }
        }
    }
}

fun quadrantColors(quadrant: Quadrant): Pair<Color, Color> = when (quadrant) {
    Quadrant.URGENT_IMPORTANT -> SortdColors.nowStart to SortdColors.nowEnd
    Quadrant.IMPORTANT -> SortdColors.nextStart to SortdColors.nextEnd
    Quadrant.URGENT -> SortdColors.soonStart to SortdColors.soonEnd
    Quadrant.NEITHER -> SortdColors.laterStart to SortdColors.laterEnd
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatScheduledTime(start: Instant, end: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val startZoned = start.atZone(zoneId)
    val endZoned = end.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val startDate = startZoned.toLocalDate()

    val datePrefix = when {
        startDate == today -> "Today"
        startDate == today.plusDays(1) -> "Tomorrow"
        else -> startDate.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    return "$datePrefix, ${startZoned.format(timeFormatter)} - ${endZoned.format(timeFormatter)}"
}

private fun formatDeadline(deadline: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val deadlineZoned = deadline.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val deadlineDate = deadlineZoned.toLocalDate()

    return when {
        deadlineDate.isBefore(today) -> "Overdue"
        deadlineDate == today -> "Due today, ${deadlineZoned.format(timeFormatter)}"
        deadlineDate == today.plusDays(1) -> "Due tomorrow"
        else -> "Due ${deadlineDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
}

private fun isDeadlineUrgent(deadline: Instant): Boolean {
    val zoneId = ZoneId.systemDefault()
    val deadlineDate = deadline.atZone(zoneId).toLocalDate()
    val today = LocalDate.now(zoneId)
    return !deadlineDate.isAfter(today)
}
