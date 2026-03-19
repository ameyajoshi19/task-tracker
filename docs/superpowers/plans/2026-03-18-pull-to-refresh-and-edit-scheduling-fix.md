# Pull-to-Refresh & Edit Scheduling Fix — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pull-to-refresh to the landing page and fix a bug where editing a scheduled task causes it to skip its own time slot.

**Architecture:** Two independent changes. Feature 1 adds pull-to-refresh plumbing through SyncScheduler → TaskListViewModel → TaskListScreen. Feature 2 is a surgical fix in TaskEditViewModel.save() to filter the task's own calendar events from busySlots before scheduling.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 PullToRefreshBox), WorkManager, Google Truth (tests)

**Spec:** `docs/superpowers/specs/2026-03-18-pull-to-refresh-and-edit-scheduling-fix-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/tasktracker/data/sync/SyncScheduler.kt` | Modify | Add dedup constant, unique work enqueue, status observation Flow |
| `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt` | Modify | Add `isRefreshing` state, `refresh()` function with WorkInfo observation |
| `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt` | Modify | Wrap content in `PullToRefreshBox` |
| `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt` | Modify | Filter busySlots to exclude edited task's own blocks |
| `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt` | Modify | Add test proving busySlots self-blocking causes wrong scheduling |

---

## Task 1: SyncScheduler — Deduplicated One-Shot Sync

**Files:**
- Modify: `app/src/main/java/com/tasktracker/data/sync/SyncScheduler.kt:15-61`

- [ ] **Step 1: Add `SYNC_NOW_WORK_NAME` constant and update `syncNow()`**

In `SyncScheduler.kt`, add the new constant to the companion object and change `syncNow()` to use `enqueueUniqueWork`:

```kotlin
companion object {
    private const val SYNC_WORK_NAME = "calendar_sync"
    private const val SYNC_NOW_WORK_NAME = "calendar_sync_now"
}
```

Replace the `syncNow()` method body — change `WorkManager.getInstance(context).enqueue(request)` to:

```kotlin
WorkManager.getInstance(context)
    .enqueueUniqueWork(SYNC_NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
```

This requires adding `ExistingWorkPolicy` to imports (already available from `androidx.work.*`).

- [ ] **Step 2: Add `observeSyncNowStatus()` method**

Add this method to `SyncScheduler`, after `syncNow()`. This returns a Flow that the ViewModel can observe to know when sync completes:

```kotlin
fun observeSyncNowStatus(): Flow<List<WorkInfo>> =
    WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(SYNC_NOW_WORK_NAME)
```

Add the required import at the top of the file:

```kotlin
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
```

- [ ] **Step 3: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/SyncScheduler.kt
git commit -m "feat: deduplicate SyncScheduler.syncNow() with unique work policy"
```

---

## Task 2: TaskListViewModel — Refresh State & WorkInfo Observation

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt:23-76`

- [ ] **Step 1: Add `SyncScheduler` dependency and `isRefreshing` state**

Add `SyncScheduler` to the constructor injection:

```kotlin
import com.tasktracker.data.sync.SyncScheduler
```

Add constructor parameter after `appPreferences`:

```kotlin
private val syncScheduler: SyncScheduler,
```

Add `isRefreshing` field to `TaskListUiState` (line 30, after `reschedulingTaskIds`):

```kotlin
val isRefreshing: Boolean = false,
```

Add a private mutable state flow alongside the existing `_rescheduleError` and `_reschedulingTaskIds`:

```kotlin
private val _isRefreshing = MutableStateFlow(false)
```

- [ ] **Step 2: Wire `isRefreshing` into the `uiState` combine**

Update the `combine` call to include `_isRefreshing`. Change from a 3-argument combine to a 4-argument combine:

```kotlin
val uiState: StateFlow<TaskListUiState> = combine(
    taskRepository.observeAllWithScheduleInfo(),
    _rescheduleError,
    _reschedulingTaskIds,
    _isRefreshing,
) { tasks, rescheduleErr, reschedulingIds, refreshing ->
```

Add `isRefreshing = refreshing,` to the `TaskListUiState(...)` constructor call inside the combine lambda, after `reschedulingTaskIds = reschedulingIds`.

- [ ] **Step 3: Add `refresh()` function**

Add this function after `clearRescheduleError()`:

```kotlin
fun refresh() {
    if (_isRefreshing.value) return
    _isRefreshing.value = true
    syncScheduler.syncNow()
    viewModelScope.launch {
        val terminalState = syncScheduler.observeSyncNowStatus()
            .mapNotNull { workInfos ->
                workInfos.firstOrNull()?.state
            }
            .first { state ->
                state == WorkInfo.State.SUCCEEDED ||
                    state == WorkInfo.State.FAILED ||
                    state == WorkInfo.State.CANCELLED
            }
        _isRefreshing.value = false
        if (terminalState == WorkInfo.State.FAILED) {
            _rescheduleError.value = "Sync failed. Please try again."
        }
    }
}
```

Add the required imports:

```kotlin
import androidx.work.WorkInfo
import com.tasktracker.data.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt
git commit -m "feat: add refresh state and WorkInfo observation to TaskListViewModel"
```

---

## Task 3: TaskListScreen — Pull-to-Refresh UI

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt:128-143`

- [ ] **Step 1: Add PullToRefreshBox import**

Add to the imports section at the top of `TaskListScreen.kt`:

```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
```

- [ ] **Step 2: Wrap content in PullToRefreshBox**

In the `Scaffold` content lambda (around line 128), wrap the entire content (the `if (uiState.isLoading)` / `else` block) inside a `PullToRefreshBox`. The current code looks like:

```kotlin
} { padding ->
    if (uiState.isLoading) {
        Box(...) { ... }
    } else {
        LazyColumn(...) { ... }
    }
}
```

Change it to:

```kotlin
} { padding ->
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
```

Note: Move the `.fillMaxSize().padding(padding)` from the inner `Box`/`LazyColumn` up to the `PullToRefreshBox` modifier. The inner views should only have `.fillMaxSize()` without the scaffold padding (to avoid double-padding).

- [ ] **Step 3: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
git commit -m "feat: add pull-to-refresh to task list screen"
```

---

## Task 4: Fix BusySlots Self-Blocking — Test

**Files:**
- Modify: `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt`

This test proves the root cause at the scheduler level: when a task's own block appears in `busySlots`, the scheduler places the task in a different slot.

- [ ] **Step 1: Write failing test**

Add this test to `TaskSchedulerTest.kt`:

```kotlin
@Test
fun `scheduler avoids busySlots even when they contain the tasks own event`() {
    // Documents scheduler behavior: busySlots are always honored.
    // When a task's own calendar event appears in busySlots (as happens
    // when the free/busy API includes it), the scheduler correctly skips
    // that slot. The fix for the edit bug must happen in the caller
    // (TaskEditViewModel) by filtering busySlots before calling the scheduler.
    val ownSlotStart = monday.atTime(9, 0).atZone(zoneId).toInstant()
    val ownSlotEnd = monday.atTime(11, 0).atZone(zoneId).toInstant()

    val busySlotsWithOwnEvent = listOf(
        TimeSlot(ownSlotStart, ownSlotEnd),
    )

    val editedTask = task(id = 1, duration = 120)

    val result = scheduler.schedule(
        tasks = listOf(editedTask),
        existingBlocks = emptyList(),
        availability = listOf(availability()),
        busySlots = busySlotsWithOwnEvent,
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = testNow,
    )

    // Task lands at 11am — the scheduler correctly avoids the busy slot.
    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
    val blocks = (result as SchedulingResult.Scheduled).blocks
    assertThat(blocks).hasSize(1)
    assertThat(blocks[0].startTime).isEqualTo(
        monday.atTime(11, 0).atZone(zoneId).toInstant()
    )
}

@Test
fun `scheduler places task at 9am when busySlots are filtered`() {
    // After the fix: busySlots no longer include the task's own event.
    // The scheduler should place the task at 9am.
    val existingBlocks = emptyList<ScheduledBlock>()
    val editedTask = task(id = 1, duration = 120)

    val result = scheduler.schedule(
        tasks = listOf(editedTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(), // filtered — no self-blocking
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = testNow,
    )

    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
    val blocks = (result as SchedulingResult.Scheduled).blocks
    assertThat(blocks).hasSize(1)
    assertThat(blocks[0].startTime).isEqualTo(
        monday.atTime(9, 0).atZone(zoneId).toInstant()
    )
}
```

- [ ] **Step 2: Run tests to verify they pass**

These tests document the scheduler's correct behavior (it honors busySlots) and confirm the fix must happen at the caller level. Both should pass.

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest" 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt
git commit -m "test: add scheduler tests documenting busySlots self-blocking behavior"
```

---

## Task 5: Fix BusySlots Self-Blocking — Implementation

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt:186-206`

**Note — Silent reschedule for edits:** The spec requires that edits do not show a RescheduleScreen. The existing `NeedsReschedule` handler (lines 222-240) already sets `savedSuccessfully = true`, which triggers navigation back — no code change needed for this requirement. Verify this is still the case before proceeding.

- [ ] **Step 1: Add busySlots filtering for edits**

In `TaskEditViewModel.save()`, after the `blocksForScheduling` filter (line 193) and before the `scheduleWithConflictResolution` call (line 196), add the busySlots filter:

```kotlin
// For edits, also exclude the task's own calendar events from busySlots.
// The free/busy API includes the task's existing event, which would
// cause the scheduler to skip the task's current slot.
val filteredBusySlots = if (isEditing) {
    val ownBlocks = existingBlocks.filter { it.taskId == savedTaskId }
    busySlots.filter { busy ->
        ownBlocks.none { own ->
            busy.startTime < own.endTime && busy.endTime > own.startTime
        }
    }
} else {
    busySlots
}
```

Then update the `scheduleWithConflictResolution` call to use `filteredBusySlots` instead of `busySlots`:

```kotlin
val result = taskScheduler.scheduleWithConflictResolution(
    newTask = task,
    allTasks = allTasks,
    existingBlocks = blocksForScheduling,
    availability = availability,
    busySlots = filteredBusySlots,  // was: busySlots
    startDate = today,
    endDate = today.plusDays(14),
    zoneId = zoneId,
    now = Instant.now(),
)
```

- [ ] **Step 2: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run all tests**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "fix: filter task's own calendar events from busySlots when editing"
```

---

## Manual Testing Checklist

After all tasks are complete, verify on device/emulator:

- [ ] Pull down on the task list screen — spinner appears, data refreshes, spinner dismisses
- [ ] Pull down rapidly multiple times — only one sync runs, no duplicate work
- [ ] Edit a scheduled task without changing duration — it should keep the same (or equivalent) time slot
- [ ] Edit a scheduled task and change its duration — it should find the best available slot without skipping its own old slot
