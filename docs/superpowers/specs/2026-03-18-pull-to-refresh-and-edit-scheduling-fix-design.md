# Pull-to-Refresh & Edit Scheduling Fix

## Overview

Two changes to the task management flow:

1. **Pull-to-refresh on the landing page** — triggers the same full sync as app load, with deduplication to prevent concurrent syncs.
2. **Fix: edited tasks block their own time slot** — the scheduler sees the task's existing Google Calendar event as busy time, causing it to pick a different slot even though the original is available.

## Feature 1: Pull-to-Refresh

### Problem

No way to manually refresh data on the landing page. The only triggers are app startup and connectivity restoration.

### Design

**SyncScheduler: deduplicated one-shot sync**

Add a new constant `SYNC_NOW_WORK_NAME = "calendar_sync_now"` (distinct from the existing `SYNC_WORK_NAME` used for periodic sync). Change `syncNow()` from a plain `enqueue` to `enqueueUniqueWork` with `ExistingWorkPolicy.KEEP`:

```kotlin
companion object {
    private const val SYNC_WORK_NAME = "calendar_sync"
    private const val SYNC_NOW_WORK_NAME = "calendar_sync_now"
}

fun syncNow() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(SYNC_NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}
```

This ensures back-to-back calls (pull-to-refresh spam, app load + pull, connectivity restore + pull) are collapsed into one sync. All existing callers (`MainActivity` app load, connectivity listener) benefit automatically.

Add a method to expose sync status as a Flow for the ViewModel to observe:

```kotlin
fun observeSyncNowStatus(): Flow<List<WorkInfo>> =
    WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(SYNC_NOW_WORK_NAME)
```

**TaskListViewModel: refresh state**

Add to `TaskListUiState`:
- `isRefreshing: Boolean = false`

Add to `TaskListViewModel`:
- `refresh()` — sets `isRefreshing = true`, calls `syncScheduler.syncNow()`, observes `syncScheduler.observeSyncNowStatus()` to detect when work reaches a terminal state (`SUCCEEDED`, `FAILED`, `CANCELLED`), then clears `isRefreshing`. On `FAILED`, show a snackbar error via the existing `snackbarHostState`.

**TaskListScreen: pull-to-refresh UI**

Wrap content in Material3 `PullToRefreshBox`:

```kotlin
PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() },
) {
    LazyColumn(...) { ... }
}
```

### Files Changed

| File | Change |
|------|--------|
| `SyncScheduler.kt` | Add `SYNC_NOW_WORK_NAME` constant, use `enqueueUniqueWork` with `KEEP` policy, add `observeSyncNowStatus()` |
| `TaskListViewModel.kt` | Add `isRefreshing` state, `refresh()` function, WorkInfo observer |
| `TaskListScreen.kt` | Wrap content in `PullToRefreshBox` |

## Feature 2: Edit Scheduling — BusySlots Self-Blocking Fix

### Problem

When editing a task that's already scheduled (e.g., 9-11am tomorrow), the task gets placed at 11am instead of staying in its original 9am slot, even though the code already filters the task's blocks from `existingBlocks`.

**Root cause:** The `busySlots` fetched from Google Calendar's free/busy API include the task's own calendar event. The filter on line 189 of `TaskEditViewModel` only removes the task's blocks from `existingBlocks`, not from `busySlots`. The scheduler sees 9-11am as busy via the `busySlots` path and skips it.

**Flow:**
1. Fetch `busySlots` from Google Calendar — includes the 9-11am event
2. Filter `existingBlocks` to remove the task's own blocks — correct
3. Scheduler combines both into `allBusySlots` — 9-11am still appears via `busySlots`
4. Task gets placed at 11am
5. Old event is deleted later in `persistTask()` — too late

### Design

**Filter busySlots in `TaskEditViewModel.save()`**

When editing, compute the task's own block time ranges and filter them out of `busySlots`. Use overlap-based matching rather than exact time comparison, because Google Calendar's free/busy API can merge adjacent busy periods (e.g., a task event 9-10am adjacent to a personal event 10-11am may be returned as a single 9-11am busy slot):

```kotlin
val filteredBusySlots = if (isEditing) {
    val ownBlocks = existingBlocks.filter { it.taskId == savedTaskId }
    busySlots.filter { busy ->
        // Keep busy slots that don't overlap with any of the task's own blocks
        ownBlocks.none { own ->
            busy.startTime < own.endTime && busy.endTime > own.startTime
        }
    }
} else {
    busySlots
}
```

This uses the standard overlap test (`A.start < B.end && A.end > B.start`). If a Google Calendar busy slot was merged with the task's own event, the entire merged slot is removed. This is acceptable because the non-task portion of the merged slot will still be represented by its own event in `existingBlocks` from other calendars, or the scheduler will naturally avoid conflicts with other busy times.

Pass `filteredBusySlots` instead of `busySlots` to `scheduleWithConflictResolution()`.

**Silent reschedule for edits**

When editing a task, the `NeedsReschedule` result should be handled the same as `Scheduled` — persist the task, insert blocks, push to calendar, and navigate back. Do not show the RescheduleScreen. The user edited their own task and expects it to just save.

The existing `NeedsReschedule` handler already persists and pushes blocks (lines 222-240). The only change: do not navigate to the RescheduleScreen when `isEditing` is true. The `savedSuccessfully = true` on line 240 already handles this correctly — the screen navigates back.

### Files Changed

| File | Change |
|------|--------|
| `TaskEditViewModel.kt` | Filter `busySlots` to exclude edited task's own blocks before scheduling |

## Testing

- **Pull-to-refresh:** Pull down on landing page triggers sync; pulling again while sync is running does not enqueue a second sync; spinner dismisses when sync completes.
- **Edit scheduling:** Edit a scheduled task without changing its duration or constraints — it should land back in the same (or equivalent) time slot, not skip over its own slot.
- **Edit with reschedule:** Edit a task in a way that causes other tasks to move — changes should apply silently without showing the reschedule approval screen.
