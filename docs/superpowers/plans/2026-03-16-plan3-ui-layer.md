# Plan 3: UI Layer

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build all 6 screens (Onboarding, Task List, Add/Edit Task, Schedule View, Reschedule Proposal, Settings) with ViewModels, Compose Navigation, and shared UI components.

**Architecture:** Each screen is a Compose function paired with a Hilt ViewModel. Navigation uses Compose Navigation with a sealed `Screen` route class. ViewModels expose UI state via `StateFlow` and accept events via methods. Shared components (quadrant selector, duration picker, availability editor) are extracted into reusable composables.

**Tech Stack:** Jetpack Compose, Material 3, Compose Navigation, Hilt ViewModel, StateFlow

**Spec:** `docs/superpowers/specs/2026-03-16-smart-task-scheduler-design.md`
**Depends on:** Plan 1 (data layer), Plan 2 (calendar integration)

---

## File Structure

```
app/src/main/java/com/tasktracker/
├── ui/
│   ├── navigation/
│   │   ├── Screen.kt                          # Sealed class for navigation routes
│   │   └── NavGraph.kt                        # Navigation graph setup
│   ├── theme/
│   │   ├── Theme.kt                           # Material 3 theme
│   │   ├── Color.kt                           # Color definitions
│   │   └── Type.kt                            # Typography
│   ├── components/
│   │   ├── QuadrantSelector.kt                # Eisenhower quadrant picker
│   │   ├── DurationPicker.kt                  # 5-min increment duration input
│   │   ├── DayPreferenceSelector.kt           # Weekday/Weekend/Any chips
│   │   ├── AvailabilityEditor.kt              # Per-day time window editor
│   │   ├── TaskCard.kt                        # Task item in list
│   │   └── TimeBlockCard.kt                   # Scheduled block in calendar view
│   ├── onboarding/
│   │   ├── OnboardingScreen.kt                # Multi-step onboarding flow
│   │   └── OnboardingViewModel.kt
│   ├── tasklist/
│   │   ├── TaskListScreen.kt                  # Tasks grouped by quadrant
│   │   └── TaskListViewModel.kt
│   ├── taskedit/
│   │   ├── TaskEditScreen.kt                  # Add/Edit task form
│   │   └── TaskEditViewModel.kt
│   ├── schedule/
│   │   ├── ScheduleScreen.kt                  # Daily/weekly calendar view
│   │   └── ScheduleViewModel.kt
│   ├── reschedule/
│   │   ├── RescheduleScreen.kt                # Reschedule proposal review
│   │   └── RescheduleViewModel.kt
│   └── settings/
│       ├── SettingsScreen.kt                  # Availability + calendar config
│       └── SettingsViewModel.kt
├── MainActivity.kt                            # Single activity, hosts NavGraph
```

---

## Task 1: Theme and Color Setup

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/theme/Color.kt`
- Create: `app/src/main/java/com/tasktracker/ui/theme/Type.kt`
- Create: `app/src/main/java/com/tasktracker/ui/theme/Theme.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.tasktracker.ui.theme

import androidx.compose.ui.graphics.Color

// Quadrant colors
val QuadrantUrgentImportant = Color(0xFFE53935)   // Red
val QuadrantImportant = Color(0xFF1E88E5)          // Blue
val QuadrantUrgent = Color(0xFFFB8C00)             // Orange
val QuadrantNeither = Color(0xFF43A047)             // Green

// Status colors
val StatusPending = Color(0xFF9E9E9E)
val StatusScheduled = Color(0xFF1E88E5)
val StatusInProgress = Color(0xFFFB8C00)
val StatusCompleted = Color(0xFF43A047)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.tasktracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TaskTrackerTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
package com.tasktracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun TaskTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TaskTrackerTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/theme/
git commit -m "feat: add Material 3 theme with quadrant and status colors"
```

---

## Task 2: Navigation Setup

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/navigation/Screen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt`
- Create: `app/src/main/java/com/tasktracker/MainActivity.kt`

- [ ] **Step 1: Create Screen routes**

```kotlin
package com.tasktracker.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object TaskList : Screen("task_list")
    data object TaskEdit : Screen("task_edit/{taskId}") {
        fun createRoute(taskId: Long = -1L) = "task_edit/$taskId"
    }
    data object Schedule : Screen("schedule")
    data object Reschedule : Screen("reschedule")
    data object Settings : Screen("settings")
}
```

- [ ] **Step 2: Create NavGraph (placeholder screens for now)**

```kotlin
package com.tasktracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun TaskTrackerNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            PlaceholderScreen("Onboarding")
        }
        composable(Screen.TaskList.route) {
            PlaceholderScreen("Task List")
        }
        composable(
            route = Screen.TaskEdit.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: -1L
            PlaceholderScreen("Task Edit (id=$taskId)")
        }
        composable(Screen.Schedule.route) {
            PlaceholderScreen("Schedule")
        }
        composable(Screen.Reschedule.route) {
            PlaceholderScreen("Reschedule")
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen("Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}
```

- [ ] **Step 3: Create MainActivity**

```kotlin
package com.tasktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.tasktracker.ui.navigation.TaskTrackerNavGraph
import com.tasktracker.ui.theme.TaskTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskTrackerTheme {
                val navController = rememberNavController()
                TaskTrackerNavGraph(navController = navController)
            }
        }
    }
}
```

- [ ] **Step 4: Register MainActivity in AndroidManifest.xml**

Add inside the `<application>` tag:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Material3.DynamicColors.DayNight">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

- [ ] **Step 5: Verify project compiles and app launches**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/navigation/ app/src/main/java/com/tasktracker/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add navigation graph with routes and MainActivity"
```

---

## Task 3: Shared UI Components

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/components/QuadrantSelector.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/DurationPicker.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/DayPreferenceSelector.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/TaskCard.kt`

- [ ] **Step 1: Create QuadrantSelector**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.ui.theme.*

@Composable
fun QuadrantSelector(
    selected: Quadrant,
    onSelect: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Priority",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuadrantCell(
                    label = "Urgent &\nImportant",
                    color = QuadrantUrgentImportant,
                    isSelected = selected == Quadrant.URGENT_IMPORTANT,
                    onClick = { onSelect(Quadrant.URGENT_IMPORTANT) },
                    modifier = Modifier.weight(1f),
                )
                QuadrantCell(
                    label = "Important",
                    color = QuadrantImportant,
                    isSelected = selected == Quadrant.IMPORTANT,
                    onClick = { onSelect(Quadrant.IMPORTANT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuadrantCell(
                    label = "Urgent",
                    color = QuadrantUrgent,
                    isSelected = selected == Quadrant.URGENT,
                    onClick = { onSelect(Quadrant.URGENT) },
                    modifier = Modifier.weight(1f),
                )
                QuadrantCell(
                    label = "Neither",
                    color = QuadrantNeither,
                    isSelected = selected == Quadrant.NEITHER,
                    onClick = { onSelect(Quadrant.NEITHER) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else color.copy(alpha = 0.4f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 2: Create DurationPicker**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DurationPicker(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 15,
    max: Int = 480,
    step: Int = 5,
) {
    Column(modifier = modifier) {
        Text(
            text = "Estimated Duration",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = {
                    val newVal = (durationMinutes - step).coerceAtLeast(min)
                    onDurationChange(newVal)
                },
                enabled = durationMinutes > min,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }

            Text(
                text = formatDuration(durationMinutes),
                style = MaterialTheme.typography.headlineMedium,
            )

            IconButton(
                onClick = {
                    val newVal = (durationMinutes + step).coerceAtMost(max)
                    onDurationChange(newVal)
                },
                enabled = durationMinutes < max,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}
```

- [ ] **Step 3: Create DayPreferenceSelector**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.DayPreference

@Composable
fun DayPreferenceSelector(
    selected: DayPreference,
    onSelect: (DayPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Day Preference",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPreference.entries.forEach { pref ->
                FilterChip(
                    selected = selected == pref,
                    onClick = { onSelect(pref) },
                    label = {
                        Text(
                            when (pref) {
                                DayPreference.WEEKDAY -> "Weekday"
                                DayPreference.WEEKEND -> "Weekend"
                                DayPreference.ANY -> "Any"
                            }
                        )
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 4: Create TaskCard**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.ui.theme.*

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quadrantColor = when (task.quadrant) {
        Quadrant.URGENT_IMPORTANT -> QuadrantUrgentImportant
        Quadrant.IMPORTANT -> QuadrantImportant
        Quadrant.URGENT -> QuadrantUrgent
        Quadrant.NEITHER -> QuadrantNeither
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Quadrant indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(quadrantColor),
            )
            Spacer(Modifier.width(12.dp))

            // Task info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDuration(task.estimatedDurationMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Complete button
            if (task.status != TaskStatus.COMPLETED) {
                IconButton(onClick = onComplete) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/
git commit -m "feat: add shared UI components - quadrant selector, duration picker, task card"
```

---

## Task 4: Task List Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt`

- [ ] **Step 1: Create TaskListViewModel**

```kotlin
package com.tasktracker.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<Task>> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val syncManager: CalendarSyncManager,
) : ViewModel() {

    val uiState: StateFlow<TaskListUiState> = taskRepository.observeAll()
        .map { tasks ->
            TaskListUiState(
                tasksByQuadrant = tasks.groupBy { it.quadrant },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskListUiState(),
        )

    fun completeTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateStatus(task.id, TaskStatus.COMPLETED)
            syncManager.markTaskCompleted(task.id)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            syncManager.deleteTaskEvents(task.id)
            taskRepository.delete(task)
        }
    }
}
```

- [ ] **Step 2: Create TaskListScreen**

```kotlin
package com.tasktracker.ui.tasklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.ui.components.TaskCard
import com.tasktracker.ui.theme.*

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
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(Icons.Default.CalendarMonth, "Schedule")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, "Add task")
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
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
                    item {
                        QuadrantHeader(quadrant)
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEditTask(task.id) },
                            onComplete = { viewModel.completeTask(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuadrantHeader(quadrant: Quadrant) {
    val (label, color) = when (quadrant) {
        Quadrant.URGENT_IMPORTANT -> "Urgent & Important" to QuadrantUrgentImportant
        Quadrant.IMPORTANT -> "Important" to QuadrantImportant
        Quadrant.URGENT -> "Urgent" to QuadrantUrgent
        Quadrant.NEITHER -> "Neither" to QuadrantNeither
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/
git commit -m "feat: add task list screen with quadrant grouping"
```

---

## Task 5: Add/Edit Task Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt`

- [ ] **Step 1: Create TaskEditViewModel**

```kotlin
package com.tasktracker.ui.taskedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import com.tasktracker.domain.scheduler.*
import com.tasktracker.domain.validation.TaskValidator
import com.tasktracker.domain.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TaskEditUiState(
    val title: String = "",
    val description: String = "",
    val durationMinutes: Int = 60,
    val quadrant: Quadrant = Quadrant.IMPORTANT,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val isEditing: Boolean = false,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val schedulingResult: SchedulingResult? = null,
)

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val syncManager: CalendarSyncManager,
    private val taskScheduler: TaskScheduler,
    private val taskValidator: TaskValidator,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L
    private val _uiState = MutableStateFlow(TaskEditUiState(isEditing = taskId != -1L))
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    init {
        if (taskId != -1L) {
            viewModelScope.launch {
                val task = taskRepository.getById(taskId) ?: return@launch
                _uiState.update {
                    it.copy(
                        title = task.title,
                        description = task.description,
                        durationMinutes = task.estimatedDurationMinutes,
                        quadrant = task.quadrant,
                        deadline = task.deadline,
                        dayPreference = task.dayPreference,
                        splittable = task.splittable,
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) { _uiState.update { it.copy(title = title, validationError = null) } }
    fun updateDescription(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun updateDuration(minutes: Int) { _uiState.update { it.copy(durationMinutes = minutes, validationError = null) } }
    fun updateQuadrant(q: Quadrant) { _uiState.update { it.copy(quadrant = q) } }
    fun updateDeadline(deadline: Instant?) { _uiState.update { it.copy(deadline = deadline) } }
    fun updateDayPreference(pref: DayPreference) { _uiState.update { it.copy(dayPreference = pref) } }
    fun updateSplittable(splittable: Boolean) { _uiState.update { it.copy(splittable = splittable, validationError = null) } }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(validationError = "Title is required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val task = Task(
                id = if (taskId != -1L) taskId else 0,
                title = state.title.trim(),
                description = state.description.trim(),
                estimatedDurationMinutes = state.durationMinutes,
                quadrant = state.quadrant,
                deadline = state.deadline,
                dayPreference = state.dayPreference,
                splittable = state.splittable,
            )

            val availability = availabilityRepository.getAll()
            val validation = taskValidator.validate(task, availability)
            if (validation is ValidationResult.Invalid) {
                _uiState.update { it.copy(validationError = validation.reason, isSaving = false) }
                return@launch
            }

            val savedId = if (taskId != -1L) {
                taskRepository.update(task)
                taskId
            } else {
                taskRepository.insert(task)
            }

            // Run scheduler
            val allTasks = taskRepository.getByStatuses(
                listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
            )
            val existingBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            )
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val busySlots = try {
                val now = Instant.now()
                val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
                calendarRepository.getFreeBusySlots(
                    calendarIds = enabledCalendars.map { it.googleCalendarId },
                    timeMin = now,
                    timeMax = twoWeeksLater,
                )
            } catch (_: Exception) {
                emptyList()
            }

            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val savedTask = taskRepository.getById(savedId) ?: task.copy(id = savedId)

            val result = taskScheduler.scheduleWithConflictResolution(
                newTask = savedTask,
                allTasks = allTasks,
                existingBlocks = existingBlocks,
                availability = availability,
                busySlots = busySlots,
                startDate = today,
                endDate = today.plusDays(14),
                zoneId = zoneId,
            )

            when (result) {
                is SchedulingResult.Scheduled -> {
                    blockRepository.insertAll(result.blocks.map { it.copy(taskId = savedId) })
                    taskRepository.updateStatus(savedId, TaskStatus.SCHEDULED)
                    result.blocks.forEach { block ->
                        syncManager.pushNewBlock(block.copy(taskId = savedId))
                    }
                    _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
                }
                is SchedulingResult.NeedsReschedule -> {
                    // Store proposed blocks temporarily, navigate to reschedule screen
                    blockRepository.insertAll(
                        (result.newBlocks + result.movedBlocks.map { it.second })
                            .map { it.copy(taskId = savedId) }
                    )
                    _uiState.update {
                        it.copy(
                            schedulingResult = result,
                            isSaving = false,
                        )
                    }
                }
                else -> {
                    // Task is saved but scheduling failed — show error, don't navigate away
                    _uiState.update {
                        it.copy(
                            schedulingResult = result,
                            savedSuccessfully = false,
                            isSaving = false,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create TaskEditScreen**

```kotlin
package com.tasktracker.ui.taskedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.SchedulingResult
import com.tasktracker.ui.components.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReschedule: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onNavigateBack()
    }

    LaunchedEffect(uiState.schedulingResult) {
        if (uiState.schedulingResult is SchedulingResult.NeedsReschedule) {
            onNavigateToReschedule()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Task" else "New Task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.validationError != null && uiState.title.isBlank(),
            )

            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            // Duration
            DurationPicker(
                durationMinutes = uiState.durationMinutes,
                onDurationChange = viewModel::updateDuration,
            )

            // Quadrant
            QuadrantSelector(
                selected = uiState.quadrant,
                onSelect = viewModel::updateQuadrant,
            )

            // Deadline
            DeadlinePicker(
                deadline = uiState.deadline,
                onDeadlineChange = viewModel::updateDeadline,
            )

            // Day Preference
            DayPreferenceSelector(
                selected = uiState.dayPreference,
                onSelect = viewModel::updateDayPreference,
            )

            // Splittable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Splittable", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Allow splitting across multiple time blocks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.splittable,
                    onCheckedChange = viewModel::updateSplittable,
                )
            }

            // Validation error
            if (uiState.validationError != null) {
                Text(
                    text = uiState.validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Scheduling result feedback
            val result = uiState.schedulingResult
            if (result is SchedulingResult.DeadlineAtRisk) {
                Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (result is SchedulingResult.NoSlotsAvailable) {
                Text(
                    text = result.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Save button
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState.isEditing) "Update" else "Create & Schedule")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlinePicker(
    deadline: Instant?,
    onDeadlineChange: (Instant?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val zoneId = ZoneId.systemDefault()

    Column {
        Text("Deadline (optional)", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showPicker = true }) {
                Text(
                    deadline?.let {
                        LocalDateTime.ofInstant(it, zoneId)
                            .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    } ?: "Set deadline"
                )
            }
            if (deadline != null) {
                TextButton(onClick = { onDeadlineChange(null) }) {
                    Text("Clear")
                }
            }
        }
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.toEpochMilli()
                ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDeadlineChange(Instant.ofEpochMilli(millis))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/
git commit -m "feat: add task add/edit screen with validation and scheduling"
```

---

## Task 6: Schedule View Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/schedule/ScheduleViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/TimeBlockCard.kt`

- [ ] **Step 1: Create TimeBlockCard component**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimeBlockCard(
    title: String,
    startTime: Instant,
    endTime: Instant,
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    isTaskBlock: Boolean = false,
) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    val zoneId = ZoneId.systemDefault()
    val startStr = startTime.atZone(zoneId).format(formatter)
    val endStr = endTime.atZone(zoneId).format(formatter)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (isTaskBlock) 0.3f else 0.15f))
            .padding(8.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            Text(
                text = "$startStr - $endStr",
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f),
            )
        }
    }
}
```

- [ ] **Step 2: Create ScheduleViewModel**

```kotlin
package com.tasktracker.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class ScheduleItem(
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val isTaskBlock: Boolean,
    val taskId: Long? = null,
    val blockId: Long? = null,
    val quadrant: Quadrant? = null,
)

data class ScheduleUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val items: List<ScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val viewMode: ViewMode = ViewMode.DAILY,
)

enum class ViewMode { DAILY, WEEKLY }

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedule()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadSchedule()
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.DAILY) ViewMode.WEEKLY else ViewMode.DAILY)
        }
        loadSchedule()
    }

    fun navigateDay(forward: Boolean) {
        _uiState.update {
            it.copy(selectedDate = if (forward) it.selectedDate.plusDays(1) else it.selectedDate.minusDays(1))
        }
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val state = _uiState.value
            val zoneId = ZoneId.systemDefault()
            val (start, end) = when (state.viewMode) {
                ViewMode.DAILY -> {
                    val dayStart = state.selectedDate.atStartOfDay(zoneId).toInstant()
                    val dayEnd = state.selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                    dayStart to dayEnd
                }
                ViewMode.WEEKLY -> {
                    val weekStart = state.selectedDate.with(java.time.DayOfWeek.MONDAY)
                        .atStartOfDay(zoneId).toInstant()
                    val weekEnd = weekStart.atZone(zoneId).plusWeeks(1).toInstant()
                    weekStart to weekEnd
                }
            }

            val items = mutableListOf<ScheduleItem>()

            // Load task blocks
            val blocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            ).filter { it.startTime >= start && it.startTime < end }

            for (block in blocks) {
                val task = taskRepository.getById(block.taskId)
                items.add(
                    ScheduleItem(
                        title = task?.title ?: "Task",
                        startTime = block.startTime,
                        endTime = block.endTime,
                        isTaskBlock = true,
                        taskId = block.taskId,
                        blockId = block.id,
                        quadrant = task?.quadrant,
                    )
                )
            }

            // Load external calendar events
            try {
                val enabledCalendars = calendarSelectionRepository.getEnabled()
                for (cal in enabledCalendars) {
                    val events = calendarRepository.getEvents(cal.googleCalendarId, start, end)
                    items.addAll(
                        events.map { event ->
                            ScheduleItem(
                                title = event.title.ifBlank { "Busy" },
                                startTime = event.startTime,
                                endTime = event.endTime,
                                isTaskBlock = false,
                            )
                        }
                    )
                }
            } catch (_: Exception) {
                // Offline — show only task blocks
            }

            _uiState.update {
                it.copy(
                    items = items.sortedBy { item -> item.startTime },
                    isLoading = false,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create ScheduleScreen**

```kotlin
package com.tasktracker.ui.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.ui.components.TimeBlockCard
import com.tasktracker.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.selectedDate.format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (uiState.viewMode == ViewMode.DAILY) Icons.Default.CalendarViewWeek
                            else Icons.Default.CalendarViewDay,
                            "Toggle view",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Day navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.navigateDay(false) }) {
                    Icon(Icons.Default.ChevronLeft, "Previous day")
                }
                Text(
                    uiState.selectedDate.dayOfWeek.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { viewModel.navigateDay(true) }) {
                    Icon(Icons.Default.ChevronRight, "Next day")
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No events scheduled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.items) { item ->
                        val color = if (item.isTaskBlock) {
                            when (item.quadrant) {
                                Quadrant.URGENT_IMPORTANT -> QuadrantUrgentImportant
                                Quadrant.IMPORTANT -> QuadrantImportant
                                Quadrant.URGENT -> QuadrantUrgent
                                Quadrant.NEITHER -> QuadrantNeither
                                null -> MaterialTheme.colorScheme.primary
                            }
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }

                        TimeBlockCard(
                            title = item.title,
                            startTime = item.startTime,
                            endTime = item.endTime,
                            color = color,
                            height = 64.dp,
                            isTaskBlock = item.isTaskBlock,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/schedule/ app/src/main/java/com/tasktracker/ui/components/TimeBlockCard.kt
git commit -m "feat: add schedule view with daily/weekly calendar display"
```

---

## Task 7: Reschedule Proposal Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/reschedule/RescheduleViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/reschedule/RescheduleScreen.kt`

- [ ] **Step 1: Create RescheduleViewModel**

```kotlin
package com.tasktracker.ui.reschedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RescheduleChange(
    val taskTitle: String,
    val oldStart: Instant?,
    val oldEnd: Instant?,
    val newStart: Instant,
    val newEnd: Instant,
    val isNew: Boolean,
)

data class RescheduleUiState(
    val changes: List<RescheduleChange> = emptyList(),
    val isProcessing: Boolean = false,
    val isDone: Boolean = false,
)

@HiltViewModel
class RescheduleViewModel @Inject constructor(
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val syncManager: CalendarSyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RescheduleUiState())
    val uiState: StateFlow<RescheduleUiState> = _uiState.asStateFlow()

    init {
        loadProposedChanges()
    }

    private fun loadProposedChanges() {
        viewModelScope.launch {
            val proposed = blockRepository.getByStatuses(listOf(BlockStatus.PROPOSED))
            val changes = proposed.map { block ->
                val task = taskRepository.getById(block.taskId)
                val existingBlocks = blockRepository.getByTaskId(block.taskId)
                    .filter { it.status == BlockStatus.CONFIRMED }
                val oldBlock = existingBlocks.firstOrNull()
                RescheduleChange(
                    taskTitle = task?.title ?: "Unknown",
                    oldStart = oldBlock?.startTime,
                    oldEnd = oldBlock?.endTime,
                    newStart = block.startTime,
                    newEnd = block.endTime,
                    isNew = oldBlock == null,
                )
            }
            _uiState.update { it.copy(changes = changes) }
        }
    }

    fun approve() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val proposed = blockRepository.getByStatuses(listOf(BlockStatus.PROPOSED))
            for (block in proposed) {
                // Cancel old confirmed blocks for same task
                val oldBlocks = blockRepository.getByTaskId(block.taskId)
                    .filter { it.status == BlockStatus.CONFIRMED }
                for (old in oldBlocks) {
                    blockRepository.updateStatus(old.id, BlockStatus.CANCELLED)
                    if (old.googleCalendarEventId != null) {
                        syncManager.deleteTaskEvents(old.taskId)
                    }
                }
                // Confirm proposed block
                blockRepository.updateStatus(block.id, BlockStatus.CONFIRMED)
                taskRepository.updateStatus(block.taskId, TaskStatus.SCHEDULED)
                syncManager.pushNewBlock(block.copy(status = BlockStatus.CONFIRMED))
            }
            _uiState.update { it.copy(isProcessing = false, isDone = true) }
        }
    }

    fun reject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            blockRepository.deleteProposed()
            _uiState.update { it.copy(isProcessing = false, isDone = true) }
        }
    }
}
```

- [ ] **Step 2: Create RescheduleScreen**

```kotlin
package com.tasktracker.ui.reschedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleScreen(
    onNavigateBack: () -> Unit,
    viewModel: RescheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onNavigateBack()
    }

    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    val zoneId = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reschedule Proposal") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "The following changes are needed to fit your new task:",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.changes) { change ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = change.taskTitle,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (change.isNew) {
                                Text(
                                    "New: ${change.newStart.atZone(zoneId).format(formatter)} - " +
                                        change.newEnd.atZone(zoneId).format(formatter),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        change.oldStart?.atZone(zoneId)?.format(formatter) ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        change.newStart.atZone(zoneId).format(formatter),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::reject,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isProcessing,
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = viewModel::approve,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isProcessing,
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Approve")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/reschedule/
git commit -m "feat: add reschedule proposal screen with approve/reject flow"
```

---

## Task 8: Onboarding Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt`

- [ ] **Step 1: Create AvailabilityEditor component**

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.UserAvailability
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AvailabilityEditor(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Availability", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set your available hours for each day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        for (day in DayOfWeek.entries) {
            val availability = availabilities.find { it.dayOfWeek == day }
                ?: UserAvailability(dayOfWeek = day, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), enabled = false)

            AvailabilityRow(
                dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                availability = availability,
                onToggle = { onUpdate(availability.copy(enabled = !availability.enabled)) },
                onStartTimeChange = { onUpdate(availability.copy(startTime = it, enabled = true)) },
                onEndTimeChange = { onUpdate(availability.copy(endTime = it, enabled = true)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailabilityRow(
    dayName: String,
    availability: UserAvailability,
    onToggle: () -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onEndTimeChange: (LocalTime) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = availability.enabled,
            onCheckedChange = { onToggle() },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(48.dp),
        )
        if (availability.enabled) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showStartPicker = true }) {
                Text(availability.startTime.toString())
            }
            Text(" - ")
            TextButton(onClick = { showEndPicker = true }) {
                Text(availability.endTime.toString())
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = availability.startTime.hour,
            initialMinute = availability.startTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onStartTimeChange(LocalTime.of(state.hour, state.minute))
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(
            initialHour = availability.endTime.hour,
            initialMinute = availability.endTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onEndTimeChange(LocalTime.of(state.hour, state.minute))
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
```

- [ ] **Step 2: Create OnboardingViewModel**

```kotlin
package com.tasktracker.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

enum class OnboardingStep { SIGN_IN, AVAILABILITY, CALENDARS, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SIGN_IN,
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
    val email: String? = null,
    val availabilities: List<UserAvailability> = DayOfWeek.entries.map { day ->
        val isWeekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        UserAvailability(
            dayOfWeek = day,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            enabled = isWeekday,
        )
    },
    val calendars: List<CalendarSelectionState> = emptyList(),
    val isLoadingCalendars: Boolean = false,
)

data class CalendarSelectionState(
    val id: String,
    val name: String,
    val color: String,
    val enabled: Boolean,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun signIn(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningIn = true, signInError = null) }
            val result = authManager.signIn(context)
            result.fold(
                onSuccess = { email ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            email = email,
                            step = OnboardingStep.AVAILABILITY,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = error.message)
                    }
                },
            )
        }
    }

    fun updateAvailability(availability: UserAvailability) {
        _uiState.update { state ->
            state.copy(
                availabilities = state.availabilities.map {
                    if (it.dayOfWeek == availability.dayOfWeek) availability else it
                },
            )
        }
    }

    fun saveAvailabilityAndProceed() {
        viewModelScope.launch {
            for (a in _uiState.value.availabilities) {
                availabilityRepository.insert(a)
            }
            _uiState.update { it.copy(step = OnboardingStep.CALENDARS, isLoadingCalendars = true) }
            loadCalendars()
        }
    }

    private suspend fun loadCalendars() {
        try {
            val calendars = calendarRepository.listCalendars()
            _uiState.update { state ->
                state.copy(
                    isLoadingCalendars = false,
                    calendars = calendars.map {
                        CalendarSelectionState(
                            id = it.id,
                            name = it.name,
                            color = it.color,
                            enabled = true,
                        )
                    },
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingCalendars = false) }
        }
    }

    fun toggleCalendar(calendarId: String) {
        _uiState.update { state ->
            state.copy(
                calendars = state.calendars.map {
                    if (it.id == calendarId) it.copy(enabled = !it.enabled) else it
                },
            )
        }
    }

    fun saveCalendarsAndFinish() {
        viewModelScope.launch {
            for (cal in _uiState.value.calendars) {
                calendarSelectionRepository.insert(
                    CalendarSelection(
                        googleCalendarId = cal.id,
                        calendarName = cal.name,
                        calendarColor = cal.color,
                        enabled = cal.enabled,
                    )
                )
            }
            // Create the Task Tracker calendar
            try {
                calendarRepository.getOrCreateTaskCalendar()
            } catch (_: Exception) { /* Will retry on first sync */ }
            _uiState.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
}
```

- [ ] **Step 3: Create OnboardingScreen**

```kotlin
package com.tasktracker.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.ui.components.AvailabilityEditor

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.step) {
        if (uiState.step == OnboardingStep.DONE) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Step indicator
        LinearProgressIndicator(
            progress = {
                when (uiState.step) {
                    OnboardingStep.SIGN_IN -> 0.33f
                    OnboardingStep.AVAILABILITY -> 0.66f
                    OnboardingStep.CALENDARS -> 1.0f
                    OnboardingStep.DONE -> 1.0f
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        when (uiState.step) {
            OnboardingStep.SIGN_IN -> SignInStep(
                isSigningIn = uiState.isSigningIn,
                error = uiState.signInError,
                onSignIn = { context -> viewModel.signIn(context) },
            )
            OnboardingStep.AVAILABILITY -> AvailabilityStep(
                availabilities = uiState.availabilities,
                onUpdate = viewModel::updateAvailability,
                onNext = viewModel::saveAvailabilityAndProceed,
            )
            OnboardingStep.CALENDARS -> CalendarSelectionStep(
                calendars = uiState.calendars,
                isLoading = uiState.isLoadingCalendars,
                onToggle = viewModel::toggleCalendar,
                onFinish = viewModel::saveCalendarsAndFinish,
            )
            OnboardingStep.DONE -> { /* Will navigate away */ }
        }
    }
}

@Composable
private fun SignInStep(
    isSigningIn: Boolean,
    error: String?,
    onSignIn: (android.content.Context) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Task Tracker", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Sign in with Google to sync your tasks with Google Calendar",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSignIn(context) },
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Sign in with Google")
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AvailabilityStep(
    availabilities: List<com.tasktracker.domain.model.UserAvailability>,
    onUpdate: (com.tasktracker.domain.model.UserAvailability) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AvailabilityEditor(
            availabilities = availabilities,
            onUpdate = onUpdate,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun CalendarSelectionStep(
    calendars: List<CalendarSelectionState>,
    isLoading: Boolean,
    onToggle: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select Calendars", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose which calendars to check for conflicts",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(calendars) { cal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = cal.enabled,
                            onCheckedChange = { onToggle(cal.id) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Finish Setup")
        }
    }
}
```

- [ ] **Step 4: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/onboarding/ app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt
git commit -m "feat: add onboarding screen with sign-in, availability, and calendar selection"
```

---

## Task 9: Settings Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsViewModel**

```kotlin
package com.tasktracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val email: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        authManager.signedInEmail,
        availabilityRepository.observeAll(),
        calendarSelectionRepository.observeAll(),
    ) { email, availabilities, calendars ->
        SettingsUiState(
            email = email,
            availabilities = availabilities,
            calendars = calendars,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun updateAvailability(availability: UserAvailability) {
        viewModelScope.launch {
            availabilityRepository.update(availability)
        }
    }

    fun toggleCalendar(calendar: CalendarSelection) {
        viewModelScope.launch {
            calendarSelectionRepository.update(calendar.copy(enabled = !calendar.enabled))
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authManager.signOut()
        }
    }
}
```

- [ ] **Step 2: Create SettingsScreen**

```kotlin
package com.tasktracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.ui.components.AvailabilityEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Account section
            Text("Account", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Signed in as", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            uiState.email ?: "Not signed in",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    TextButton(onClick = {
                        viewModel.signOut(context)
                        onSignedOut()
                    }) {
                        Text("Sign out")
                    }
                }
            }

            // Availability section
            AvailabilityEditor(
                availabilities = uiState.availabilities,
                onUpdate = viewModel::updateAvailability,
            )

            // Calendar selection section
            Text("Calendars", style = MaterialTheme.typography.titleMedium)
            Text(
                "Select which calendars to check for scheduling conflicts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (cal in uiState.calendars) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = cal.enabled,
                        onCheckedChange = { viewModel.toggleCalendar(cal) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(cal.calendarName, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/
git commit -m "feat: add settings screen with availability and calendar management"
```

---

## Task 10: Wire Navigation Graph to Real Screens

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Replace placeholder screens with real implementations**

```kotlin
package com.tasktracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tasktracker.ui.onboarding.OnboardingScreen
import com.tasktracker.ui.reschedule.RescheduleScreen
import com.tasktracker.ui.schedule.ScheduleScreen
import com.tasktracker.ui.settings.SettingsScreen
import com.tasktracker.ui.taskedit.TaskEditScreen
import com.tasktracker.ui.tasklist.TaskListScreen

@Composable
fun TaskTrackerNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.TaskList.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.TaskList.route) {
            TaskListScreen(
                onAddTask = { navController.navigate(Screen.TaskEdit.createRoute()) },
                onEditTask = { id -> navController.navigate(Screen.TaskEdit.createRoute(id)) },
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(
            route = Screen.TaskEdit.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            TaskEditScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReschedule = { navController.navigate(Screen.Reschedule.route) },
            )
        }
        composable(Screen.Schedule.route) {
            ScheduleScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Reschedule.route) {
            RescheduleScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.TaskList.route, inclusive = false)
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 2: Verify full project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt
git commit -m "feat: wire navigation graph to all real screens"
```

---

## Task 11: Hilt Providers for Scheduler Dependencies

**Files:**
- Create: `app/src/main/java/com/tasktracker/di/SchedulerModule.kt`

TaskEditViewModel needs `TaskScheduler` and `TaskValidator` injected. These are plain classes, so we provide them via a Hilt module.

- [ ] **Step 1: Create SchedulerModule**

```kotlin
package com.tasktracker.di

import com.tasktracker.domain.scheduler.SlotFinder
import com.tasktracker.domain.scheduler.TaskPriorityComparator
import com.tasktracker.domain.scheduler.TaskScheduler
import com.tasktracker.domain.validation.TaskValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SchedulerModule {

    @Provides
    @Singleton
    fun provideTaskPriorityComparator(): TaskPriorityComparator = TaskPriorityComparator()

    @Provides
    @Singleton
    fun provideSlotFinder(): SlotFinder = SlotFinder()

    @Provides
    @Singleton
    fun provideTaskScheduler(
        comparator: TaskPriorityComparator,
        slotFinder: SlotFinder,
    ): TaskScheduler = TaskScheduler(comparator, slotFinder)

    @Provides
    @Singleton
    fun provideTaskValidator(): TaskValidator = TaskValidator()
}
```

- [ ] **Step 2: Verify full project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/di/SchedulerModule.kt
git commit -m "feat: add Hilt module for scheduler and validator dependencies"
```

---

## Summary

After completing all 11 tasks, you will have:

- Material 3 theme with quadrant-specific colors
- Compose Navigation with 6 routes and proper back stack management
- Shared components: QuadrantSelector, DurationPicker, DayPreferenceSelector, AvailabilityEditor, TaskCard, TimeBlockCard
- Onboarding screen with Google Sign-In, availability setup, and calendar selection
- Task List screen with Eisenhower quadrant grouping and complete/delete actions
- Add/Edit Task screen with full form, validation, and auto-scheduling on save
- Schedule View with daily/weekly toggle showing task blocks and external events
- Reschedule Proposal screen with approve/reject flow
- Settings screen for availability, calendars, and account management
- Full Hilt DI for all ViewModels and scheduler components

**Next plan:** Plan 4 — Background Sync + Offline + Notifications
