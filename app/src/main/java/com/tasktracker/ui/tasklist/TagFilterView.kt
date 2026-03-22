package com.tasktracker.ui.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Tag
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.domain.scheduler.ScheduledTimeComparator

private val scheduledTimeComparator = ScheduledTimeComparator()

@Composable
fun TagFilterView(
    tagId: Long,
    tagName: String,
    tags: List<Tag>,
    tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>>,
    completedTasks: List<TaskWithScheduleInfo>,
    reschedulingTaskIds: Set<Long>,
    onEdit: (Long) -> Unit,
    onComplete: (TaskWithScheduleInfo) -> Unit,
    onDelete: (TaskWithScheduleInfo) -> Unit,
    onReschedule: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = tags.find { it.id == tagId }
    val tagColor = tag?.let { Color(it.color) }

    // Filter tasks by tag
    val filteredByQuadrant = tasksByQuadrant.mapValues { (_, tasks) ->
        tasks.filter { it.task.tagId == tagId }
            .sortedWith(scheduledTimeComparator)
    }.filterValues { it.isNotEmpty() }

    val filteredCompleted = completedTasks
        .filter { it.task.tagId == tagId }

    val quadrantOrder = listOf(
        Quadrant.URGENT_IMPORTANT,
        Quadrant.IMPORTANT,
        Quadrant.URGENT,
        Quadrant.NEITHER,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Tag header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tagColor != null) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tagColor),
                    )
                }
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (filteredByQuadrant.isEmpty() && filteredCompleted.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No tasks with this tag",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        for (quadrant in quadrantOrder) {
            val tasks = filteredByQuadrant[quadrant] ?: continue
            item { QuadrantHeader(quadrant, tasks.size) }
            items(tasks, key = { it.task.id }) { taskInfo ->
                SwipeableTaskCard(
                    taskInfo = taskInfo,
                    onEdit = { onEdit(taskInfo.task.id) },
                    onComplete = { onComplete(taskInfo) },
                    onDelete = { onDelete(taskInfo) },
                    onReschedule = { onReschedule(taskInfo.task.id) },
                    isRescheduling = taskInfo.task.id in reschedulingTaskIds,
                )
            }
        }

        // Completed section (filtered by tag)
        if (filteredCompleted.isNotEmpty()) {
            item { CompletedSectionHeader(filteredCompleted.size) }
            items(filteredCompleted, key = { "done-${it.task.id}" }) { taskInfo ->
                SwipeableTaskCard(
                    taskInfo = taskInfo,
                    onEdit = { onEdit(taskInfo.task.id) },
                    onComplete = { },
                    onDelete = { onDelete(taskInfo) },
                    onReschedule = null,
                    isRescheduling = false,
                )
            }
        }
    }
}
