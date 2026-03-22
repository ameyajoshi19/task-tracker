package com.tasktracker.ui.tasklist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.ui.theme.SortdColors

@Composable
fun TodayView(
    overdueTasks: List<TaskWithScheduleInfo>,
    todayTasks: List<TaskWithScheduleInfo>,
    upcomingTasks: List<TaskWithScheduleInfo>,
    completedTodayTasks: List<TaskWithScheduleInfo>,
    reschedulingTaskIds: Set<Long>,
    onEdit: (Long) -> Unit,
    onComplete: (TaskWithScheduleInfo) -> Unit,
    onDelete: (TaskWithScheduleInfo) -> Unit,
    onReschedule: (Long) -> Unit,
) {
    var completedExpanded by remember { mutableStateOf(false) }

    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Overdue section
        if (overdueTasks.isNotEmpty()) {
            todaySection(
                headerLabel = "Overdue",
                headerColor = errorColor,
                headerIcon = { Icon(Icons.Default.Warning, null, tint = errorColor, modifier = Modifier.size(16.dp)) },
                count = overdueTasks.size,
                tasks = overdueTasks,
                reschedulingTaskIds = reschedulingTaskIds,
                onEdit = onEdit,
                onComplete = onComplete,
                onDelete = onDelete,
                onReschedule = onReschedule,
            )
        }

        // Today section
        if (todayTasks.isNotEmpty()) {
            todaySection(
                headerLabel = "Today",
                headerColor = primaryColor,
                count = todayTasks.size,
                tasks = todayTasks,
                reschedulingTaskIds = reschedulingTaskIds,
                onEdit = onEdit,
                onComplete = onComplete,
                onDelete = onDelete,
                onReschedule = onReschedule,
            )
        }

        // Upcoming section
        if (upcomingTasks.isNotEmpty()) {
            todaySection(
                headerLabel = "Upcoming",
                headerColor = tertiaryColor,
                count = upcomingTasks.size,
                tasks = upcomingTasks,
                reschedulingTaskIds = reschedulingTaskIds,
                onEdit = onEdit,
                onComplete = onComplete,
                onDelete = onDelete,
                onReschedule = onReschedule,
            )
        }

        // Completed today section (collapsed by default)
        if (completedTodayTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { completedExpanded = !completedExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = completedTodayTasks.size.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (completedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (completedExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                }
            }
            if (completedExpanded) {
                items(completedTodayTasks, key = { "done-${it.task.id}" }) { taskInfo ->
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

        // Empty state
        if (overdueTasks.isEmpty() && todayTasks.isEmpty() && upcomingTasks.isEmpty() && completedTodayTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No tasks for today",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.todaySection(
    headerLabel: String,
    headerColor: Color,
    headerIcon: (@Composable () -> Unit)? = null,
    count: Int,
    tasks: List<TaskWithScheduleInfo>,
    reschedulingTaskIds: Set<Long>,
    onEdit: (Long) -> Unit,
    onComplete: (TaskWithScheduleInfo) -> Unit,
    onDelete: (TaskWithScheduleInfo) -> Unit,
    onReschedule: (Long) -> Unit,
) {
    item {
        SectionHeader(
            label = headerLabel,
            color = headerColor,
            icon = headerIcon,
            count = count,
        )
    }
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
    item { Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun SectionHeader(
    label: String,
    color: Color,
    icon: (@Composable () -> Unit)? = null,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
