# UI Polish Improvements Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement 4 UI polish improvements: reschedule shimmer animation, calendar typography, Due Today container, and list view header fix.

**Architecture:** All changes are in the UI layer — two ViewModels and two Screens. No domain or data layer changes. The ScheduleViewModel's `ViewMode.WEEKLY` is renamed to `ViewMode.ALL` with updated data loading.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt ViewModels, StateFlow

**Spec:** `docs/superpowers/specs/2026-03-18-ui-polish-improvements-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/tasktracker/ui/schedule/ScheduleViewModel.kt` | Modify | Rename `WEEKLY` → `ALL`, update `loadSchedule()` for ALL mode |
| `app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt` | Modify | Typography polish, day pill, conditional header for ALL mode |
| `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt` | Modify | Add `reschedulingTaskIds` state |
| `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt` | Modify | Shimmer overlay, Due Today container, interaction disabling |

---

## Task 1: Schedule View — Rename WEEKLY to ALL and Update Data Loading

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/schedule/ScheduleViewModel.kt`

- [ ] **Step 1: Rename ViewMode.WEEKLY to ViewMode.ALL**

In `ScheduleViewModel.kt`, change the enum:

```kotlin
enum class ViewMode { DAILY, ALL }
```

Update `toggleViewMode()`:

```kotlin
fun toggleViewMode() {
    _uiState.update {
        it.copy(viewMode = if (it.viewMode == ViewMode.DAILY) ViewMode.ALL else ViewMode.DAILY)
    }
    loadSchedule()
}
```

- [ ] **Step 2: Update loadSchedule() for ALL mode**

Replace the `when (state.viewMode)` block in `loadSchedule()` with:

```kotlin
val useTimeRange = state.viewMode == ViewMode.DAILY
val (start, end) = if (useTimeRange) {
    val dayStart = state.selectedDate.atStartOfDay(zoneId).toInstant()
    val dayEnd = state.selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant()
    dayStart to dayEnd
} else {
    null to null
}
```

Update the blocks query — replace the existing `.filter` line:

```kotlin
val blocks = blockRepository.getByStatuses(
    listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
).let { allBlocks ->
    if (start != null && end != null) {
        allBlocks.filter { it.startTime >= start && it.startTime < end }
    } else {
        allBlocks
    }
}
```

Update the calendar events section — wrap the `getEvents` call with the same conditional:

```kotlin
if (start != null && end != null) {
    val events = calendarRepository.getEvents(cal.googleCalendarId, start, end)
    // ... existing mapping code
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/schedule/ScheduleViewModel.kt
git commit -m "refactor: rename ViewMode.WEEKLY to ALL, fetch all blocks in ALL mode"
```

---

## Task 2: Schedule View — Typography Polish and Conditional Header

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt`

- [ ] **Step 1: Add required imports**

Add these imports at the top of `ScheduleScreen.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tasktracker.ui.theme.SortdColors
```

- [ ] **Step 2: Update TopAppBar title with conditional text and bold style**

Replace the `title` lambda in `TopAppBar` (lines 32-38):

```kotlin
title = {
    Text(
        text = if (uiState.viewMode == ViewMode.ALL) {
            "All Scheduled"
        } else {
            uiState.selectedDate.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            )
        },
        style = MaterialTheme.typography.headlineMedium,
    )
},
```

- [ ] **Step 3: Update toggle icon for ALL mode**

Replace the `if` in the actions icon (line 47):

```kotlin
Icon(
    if (uiState.viewMode == ViewMode.DAILY) Icons.Default.CalendarViewWeek
    else Icons.Default.CalendarViewDay,
    "Toggle view",
)
```

(This logic is already correct after the rename — `WEEKLY` references become `ALL`.)

- [ ] **Step 4: Make day selector row conditional and add pill styling**

Replace the entire day selector `Row` (lines 61-79) with:

```kotlin
if (uiState.viewMode == ViewMode.DAILY) {
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
        Box(
            modifier = Modifier
                .background(
                    SortdColors.accent.copy(alpha = 0.15f),
                    RoundedCornerShape(20.dp),
                )
                .border(
                    1.dp,
                    SortdColors.accent.copy(alpha = 0.3f),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Text(
                uiState.selectedDate.dayOfWeek.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = SortdColors.accentLight,
            )
        }
        IconButton(onClick = { viewModel.navigateDay(true) }) {
            Icon(Icons.Default.ChevronRight, "Next day")
        }
    }
}
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt
git commit -m "feat: calendar typography polish with day pill and conditional ALL header"
```

---

## Task 3: Task List — Reschedule Shimmer State in ViewModel

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt`

- [ ] **Step 1: Add reschedulingTaskIds to UiState**

Update the `TaskListUiState` data class (line 22-28):

```kotlin
data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>> = emptyMap(),
    val completedTasks: List<TaskWithScheduleInfo> = emptyList(),
    val dueTodayTasks: List<TaskWithScheduleInfo> = emptyList(),
    val isLoading: Boolean = true,
    val rescheduleError: String? = null,
    val reschedulingTaskIds: Set<Long> = emptySet(),
)
```

- [ ] **Step 2: Add reschedulingTaskIds StateFlow and combine it**

Add a new private flow after `_rescheduleError` (line 42):

```kotlin
private val _reschedulingTaskIds = MutableStateFlow<Set<Long>>(emptySet())
```

Update the `combine` call to include it — change from `combine(flow1, flow2)` to `combine(flow1, flow2, flow3)`:

```kotlin
val uiState: StateFlow<TaskListUiState> = combine(
    taskRepository.observeAllWithScheduleInfo(),
    _rescheduleError,
    _reschedulingTaskIds,
) { tasks, rescheduleErr, reschedulingIds ->
```

Add `reschedulingTaskIds = reschedulingIds,` to the `TaskListUiState` constructor in the combine lambda.

- [ ] **Step 3: Add/remove task IDs in rescheduleTask()**

At the start of `rescheduleTask()`, after `_rescheduleError.value = null` (line 93), add the task ID to the set and wrap the entire remaining function body in a try/finally so all exit paths (early returns, exceptions, normal completion) clean up:

```kotlin
_reschedulingTaskIds.update { it + taskId }
try {
    val task = taskRepository.getById(taskId) ?: return@launch
    val oldBlocks = blockRepository.getByTaskId(taskId)
        .filter { it.status == BlockStatus.CONFIRMED }

    if (oldBlocks.isEmpty()) return@launch

    // ... rest of existing function body unchanged through the when (result) block ...
} finally {
    _reschedulingTaskIds.update { it - taskId }
}
```

Remove the existing `val task = ...` and `val oldBlocks = ...` lines that are now inside the try block. The `finally` handles all exit paths cleanly — early returns, exceptions, and normal completion.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt
git commit -m "feat: add reschedulingTaskIds state for shimmer animation"
```

---

## Task 4: Task List — Shimmer Overlay and Interaction Disabling

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt`

- [ ] **Step 1: Add required imports**

Add these imports to `TaskListScreen.kt`:

```kotlin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import android.provider.Settings
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
```

- [ ] **Step 2: Add isRescheduling parameter to SwipeableTaskCard**

Update the `SwipeableTaskCard` signature (line 207-213) to include:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    taskInfo: TaskWithScheduleInfo,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: (() -> Unit)?,
    isRescheduling: Boolean = false,
)
```

- [ ] **Step 3: Disable interactions when rescheduling**

Inside `SwipeableTaskCard`, update the `SwipeToDismissBox` enables (lines 270-271):

```kotlin
enableDismissFromStartToEnd = isScheduled && onReschedule != null && !isRescheduling,
enableDismissFromEndToStart = !isRescheduling,
```

Update the `TaskCard` call (lines 273-277) to guard callbacks:

```kotlin
TaskCard(
    taskInfo = taskInfo,
    onClick = if (isRescheduling) ({ }) else onEdit,
    onComplete = if (isRescheduling) ({ }) else onComplete,
)
```

- [ ] **Step 4: Add shimmer overlay after TaskCard**

After the `TaskCard` call inside the `SwipeToDismissBox` content lambda, add the shimmer overlay. Wrap `TaskCard` in a `Box` so we can overlay:

Replace the existing `TaskCard(...)` block with:

```kotlin
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
            // Static overlay when reduce-motion is enabled
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
```

- [ ] **Step 5: Pass isRescheduling from all call sites**

Update all `SwipeableTaskCard` calls in `TaskListScreen` to pass the new parameter. There are 3 call sites:

Due Today section (line 138-144):
```kotlin
SwipeableTaskCard(
    taskInfo = taskInfo,
    onEdit = { onEditTask(taskInfo.task.id) },
    onComplete = { viewModel.completeTask(taskInfo.task) },
    onDelete = { taskToDelete = taskInfo },
    onReschedule = { viewModel.rescheduleTask(taskInfo.task.id) },
    isRescheduling = taskInfo.task.id in uiState.reschedulingTaskIds,
)
```

Quadrant section (line 160-166): same pattern, add `isRescheduling = taskInfo.task.id in uiState.reschedulingTaskIds,`

Completed section (line 191-197): add `isRescheduling = false,` (completed tasks can't be rescheduled)

- [ ] **Step 6: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
git commit -m "feat: add shimmer overlay and disable interactions during reschedule"
```

---

## Task 5: Task List — Due Today Distinct Container

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt`

- [ ] **Step 1: Restructure Due Today into a container**

Replace the entire Due Today section in the `LazyColumn` (lines 134-147):

```kotlin
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
```

- [ ] **Step 2: Add key import if not present**

Ensure the `key` composable is imported:

```kotlin
import androidx.compose.runtime.key
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
git commit -m "feat: wrap Due Today section in distinct container with red-tinted border"
```

---

## Task 6: Final Verification

- [ ] **Step 1: Run full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run unit tests**

Run: `./gradlew test`
Expected: All tests pass. The existing `SortdColorsTest` should still pass — no color values were changed.

- [ ] **Step 3: Verify on device/emulator**

Manual verification checklist:
1. Task list: swipe right on a scheduled task → shimmer appears on card, interactions disabled → completes or shows error
2. Task list: "Due Today" section has visible container with subtle red border, clearly separated from quadrant sections below
3. Schedule screen: date header is bold (22sp SemiBold), day-of-week shows in purple pill
4. Schedule screen: tap toggle icon → header changes to "All Scheduled", day selector disappears, all tasks shown
5. Schedule screen: tap toggle icon again → returns to daily view with date and day pill

- [ ] **Step 4: Final commit if any adjustments needed**

```bash
git add -A
git commit -m "fix: address any visual adjustments from manual testing"
```
