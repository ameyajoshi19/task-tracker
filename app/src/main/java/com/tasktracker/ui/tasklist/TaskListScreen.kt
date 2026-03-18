// app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
package com.tasktracker.ui.tasklist

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "sortd",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            ".",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SortdColors.accent,
                        )
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
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SortdColors.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val quadrantOrder = listOf(
                    Quadrant.URGENT_IMPORTANT,
                    Quadrant.IMPORTANT,
                    Quadrant.URGENT,
                    Quadrant.NEITHER,
                )
                for (quadrant in quadrantOrder) {
                    val tasks = uiState.tasksByQuadrant[quadrant] ?: continue
                    item { QuadrantHeader(quadrant, tasks.size) }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEditTask(task.id) },
                            onComplete = { viewModel.completeTask(task) },
                            onDelete = { viewModel.deleteTask(task) },
                        )
                    }
                }
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
                    items(uiState.completedTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEditTask(task.id) },
                            onComplete = { },
                            onDelete = { viewModel.deleteTask(task) },
                        )
                    }
                }
            }
        }
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
