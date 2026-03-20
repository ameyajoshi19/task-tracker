package com.tasktracker.ui.tasklist

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.painterResource
import com.tasktracker.R
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.ui.components.TaskCard
import com.tasktracker.ui.components.quadrantColors
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var taskToDelete by remember { mutableStateOf<TaskWithScheduleInfo?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.rescheduleError) {
        uiState.rescheduleError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRescheduleError()
        }
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete task?") },
            text = { Text("Delete \"${taskToDelete!!.task.title}\"? This will also remove the calendar event.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(taskToDelete!!)
                    taskToDelete = null
                }) {
                    Text("Delete", color = SortdColors.deadlineWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sortd_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                        Text("sortd", style = MaterialTheme.typography.headlineMedium)
                        Text(".", style = MaterialTheme.typography.headlineMedium, color = SortdColors.accent)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(Icons.Default.CalendarMonth, "Schedule", tint = SortdColors.accent)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                shape = RoundedCornerShape(16.dp),
                containerColor = SortdColors.accent,
                contentColor = Color.White,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Add, "Add task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SortdColors.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Due Today section
                if (uiState.dueTodayTasks.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    SortdColors.deadlineWarning.copy(alpha = 0.2f),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DueTodayHeader(uiState.dueTodayTasks.size)
                            uiState.dueTodayTasks.forEach { taskInfo ->
                                key(taskInfo.task.id) {
                                    SwipeableTaskCard(
                                        taskInfo = taskInfo,
                                        onEdit = { onEditTask(taskInfo.task.id) },
                                        onComplete = { viewModel.completeTask(taskInfo.task) },
                                        onDelete = { taskToDelete = taskInfo },
                                        onReschedule = { viewModel.rescheduleTask(taskInfo.task.id) },
                                        isRescheduling = taskInfo.task.id in uiState.reschedulingTaskIds,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Quadrant groups
                val quadrantOrder = listOf(
                    Quadrant.URGENT_IMPORTANT,
                    Quadrant.IMPORTANT,
                    Quadrant.URGENT,
                    Quadrant.NEITHER,
                )
                for (quadrant in quadrantOrder) {
                    val tasks = uiState.tasksByQuadrant[quadrant] ?: continue
                    item { QuadrantHeader(quadrant, tasks.size) }
                    items(tasks, key = { it.task.id }) { taskInfo ->
                        SwipeableTaskCard(
                            taskInfo = taskInfo,
                            onEdit = { onEditTask(taskInfo.task.id) },
                            onComplete = { viewModel.completeTask(taskInfo.task) },
                            onDelete = { taskToDelete = taskInfo },
                            onReschedule = { viewModel.rescheduleTask(taskInfo.task.id) },
                            isRescheduling = taskInfo.task.id in uiState.reschedulingTaskIds,
                        )
                    }
                }

                // Completed section
                if (uiState.completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "Completed · ${uiState.completedTasks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                letterSpacing = 1.sp,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(uiState.completedTasks, key = { "done-${it.task.id}" }) { taskInfo ->
                        SwipeableTaskCard(
                            taskInfo = taskInfo,
                            onEdit = { onEditTask(taskInfo.task.id) },
                            onComplete = { },
                            onDelete = { taskToDelete = taskInfo },
                            onReschedule = null,
                            isRescheduling = false,
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    taskInfo: TaskWithScheduleInfo,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: (() -> Unit)?,
    isRescheduling: Boolean = false,
) {
    val isScheduled = taskInfo.task.status == TaskStatus.SCHEDULED
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isScheduled && onReschedule != null) {
                        onReschedule()
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> SortdColors.deadlineWarning.copy(alpha = 0.2f)
                    SwipeToDismissBoxValue.StartToEnd -> SortdColors.accent.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                label = "swipe-bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd && isScheduled) {
                    Icon(
                        Icons.Default.EventRepeat,
                        contentDescription = "Reschedule",
                        tint = SortdColors.accent,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = SortdColors.deadlineWarning,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        },
        enableDismissFromStartToEnd = isScheduled && onReschedule != null && !isRescheduling,
        enableDismissFromEndToStart = !isRescheduling,
    ) {
        Box {
            TaskCard(
                taskInfo = taskInfo,
                onClick = if (isRescheduling) ({ }) else onEdit,
                onComplete = if (isRescheduling) ({ }) else onComplete,
            )
            if (isRescheduling) {
                val context = LocalContext.current
                val reduceMotion = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
                if (reduceMotion) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SortdColors.accent.copy(alpha = 0.12f)),
                    )
                } else {
                    val transition = rememberInfiniteTransition(label = "shimmer")
                    val offsetX by transition.animateFloat(
                        initialValue = -1f,
                        targetValue = 2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                        ),
                        label = "shimmer-offset",
                    )
                    BoxWithConstraints(
                        modifier = Modifier.matchParentSize(),
                    ) {
                        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            SortdColors.accent.copy(alpha = 0.2f),
                                            Color.Transparent,
                                        ),
                                        startX = offsetX * widthPx,
                                        endX = (offsetX + 1f) * widthPx,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DueTodayHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SortdColors.deadlineWarning.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = SortdColors.deadlineWarning,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Due Today",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SortdColors.deadlineWarning,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = SortdColors.deadlineWarning,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SortdColors.deadlineWarning.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun QuadrantHeader(quadrant: Quadrant, count: Int) {
    val (colorStart, colorEnd) = quadrantColors(quadrant)
    val (icon, label) = when (quadrant) {
        Quadrant.URGENT_IMPORTANT -> "⚡" to "Now"
        Quadrant.IMPORTANT -> "🎯" to "Next"
        Quadrant.URGENT -> "🔄" to "Soon"
        Quadrant.NEITHER -> "📦" to "Later"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$icon $label",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colorStart,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colorStart,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colorStart.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
