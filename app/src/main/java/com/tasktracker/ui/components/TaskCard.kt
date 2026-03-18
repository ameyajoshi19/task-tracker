// app/src/main/java/com/tasktracker/ui/components/TaskCard.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.ui.theme.SortdColors

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val (colorStart, colorEnd) = quadrantColors(task.quadrant)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.45f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
