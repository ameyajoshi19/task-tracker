package com.tasktracker.ui.tasklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.domain.scheduler.ScheduledTimeComparator

private val scheduledTimeComparator = ScheduledTimeComparator()

@Composable
fun AllTasksView(
    tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>>,
    completedTasks: List<TaskWithScheduleInfo>,
    reschedulingTaskIds: Set<Long>,
    onEdit: (Long) -> Unit,
    onComplete: (TaskWithScheduleInfo) -> Unit,
    onDelete: (TaskWithScheduleInfo) -> Unit,
    onReschedule: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        for (quadrant in quadrantOrder) {
            val tasks = tasksByQuadrant[quadrant]
                ?.sortedWith(scheduledTimeComparator)
                ?: continue
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

        // Completed section
        if (completedTasks.isNotEmpty()) {
            item { CompletedSectionHeader(completedTasks.size) }
            items(completedTasks, key = { "done-${it.task.id}" }) { taskInfo ->
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

@Composable
internal fun CompletedSectionHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = "Completed · $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
            letterSpacing = 1.sp,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}
