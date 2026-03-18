# Scheduling Fixes & Task UX Improvements

## Overview

Six targeted fixes and features addressing scheduling reliability and task list usability:

1. Fix conflict resolution so higher-priority tasks displace lower-priority ones
2. Atomic save — don't persist tasks when scheduling fails
3. Swipe-to-delete with confirmation and calendar event cleanup
4. Swipe-to-reschedule with old slot blocking
5. Show scheduled time and deadline on task cards
6. "Due Today" urgency section at the top of the task list

## Context

User availability: 6-8 PM weekdays, 9 AM-5 PM weekends. When a 2h Important task fills the entire 6-8 PM window and a 15m Urgent & Important task with a same-day deadline is created, the scheduler fails with "cannot schedule before deadline" instead of displacing the lower-priority task. The task is also incorrectly persisted despite the error.

---

## 1. Fix Rescheduling (Conflict Resolution)

### Problem

When the 6-8 PM weekday window is fully occupied by a 2h Important task and a 15m Urgent & Important task with a same-day deadline is created, the scheduler returns `DeadlineAtRisk` instead of displacing the lower-priority task.

**Code trace through `scheduleWithConflictResolution`:**

1. **Line 200-209:** `schedule(tasks = listOf(newTask), existingBlocks = existingBlocks)` — the 2h block is in `existingBlocks`, added to `allBusySlots` (line 44-46), making 6-8 PM appear fully occupied. Returns `DeadlineAtRisk`.
2. **Line 210:** `directResult` is NOT `Scheduled`, falls through to displacement path (line 293).
3. **Line 293-299:** Correctly finds the 2h Important block as lower-priority. `lowerPriorityBlocks` is non-empty.
4. **Line 310-323:** Re-runs `schedule()` with the lower-priority block removed. Now both tasks compete for slots: the 15m task gets 6:00-6:15 PM, but the 2h task needs 120 min and only 105 min remain (6:15-8:00). It moves to the next available day.
5. **The bug:** If the displaced 2h task has a deadline that can't be met after displacement, `schedule()` at line 315 returns `DeadlineAtRisk` for the *displaced* task (line 154-163), and this propagates as the final result (line 341: `else -> rescheduleResult`). The new high-priority task is collateral damage — it never gets scheduled even though it could fit.

Additionally, if the displaced task has no deadline but can't fit in remaining same-day time, `schedule()` returns `Scheduled` with blocks only for the new task (the displaced task silently drops). The `movedPairs` in `NeedsReschedule` ends up empty because no new block matches the displaced task's ID.

### Solution

Fix the displacement path in `scheduleWithConflictResolution`:

1. **Always schedule the new task first** — when displacing, run `schedule(tasks = listOf(newTask), ...)` against the freed-up slots to guarantee it gets placed
2. **Then reschedule displaced tasks separately** — run `schedule(tasks = displacedTasks, existingBlocks = remainingBlocks + newTaskBlocks, ...)` so the new task's blocks are treated as occupied
3. **Handle partial displacement gracefully** — if a displaced task can't be rescheduled within the window, still return `NeedsReschedule` with the new task's blocks and the displaced task marked as needing attention (rather than failing the entire operation)
4. **Ensure `movedPairs` is always populated** — for displaced tasks that move to a new slot, include the old→new block mapping; for displaced tasks that can't fit, include them with a null new block so the UI can show "moved to backlog" or similar

### Expected Result (example scenario)

- "Follow up with lawyers" (15m, Urgent & Important) → 6:00-6:15 PM today
- "Upload tax documents" (2h, Important) → moved to next available slot (e.g., tomorrow 6-8 PM), user sees what moved via `NeedsReschedule`

### Files to Modify

- `domain/scheduler/TaskScheduler.kt` — fix displacement path in `scheduleWithConflictResolution()`
- `domain/scheduler/TaskSchedulerTest.kt` — add/update tests for displacement scenarios

---

## 2. Atomic Save

### Problem

`TaskEditViewModel.save()` inserts the task first (PENDING status), then runs scheduling. On scheduling failure, the task remains in the database with no calendar event while the user sees an error — confusing UX.

### Solution

Restructure the save flow to schedule-before-persist:

1. **Validate** — title, duration, availability constraints (existing logic)
2. **Build task in memory** — create `Task` object with temporary ID (0) and PENDING status, do not insert
3. **Gather scheduling inputs** — fetch existing tasks, blocks, free/busy slots, availability (existing logic)
4. **Run scheduler** — call `scheduleWithConflictResolution` with the in-memory task. The scheduler filters on PENDING/SCHEDULED status, so the in-memory task must have PENDING status.
5. **Check result:**
   - `Scheduled` or `NeedsReschedule`: insert task into DB (get real ID), remap block task IDs, insert blocks, push to calendar, navigate away
   - `DeadlineAtRisk` or `NoSlotsAvailable`: show error, persist nothing
6. **For edits:** only delete old blocks/events and update the task if rescheduling succeeds

**Fix NeedsReschedule block mapping bug:** The current code at line 227-230 maps ALL blocks (including moved blocks for other tasks) to the new task's ID via `.map { it.copy(taskId = savedId) }`. This corrupts displaced task data. Fix: only remap `newBlocks` to the saved ID; preserve original `taskId` values for `movedBlocks`.

**Transaction safety:** Wrap the DB insert + block insert in a Room `@Transaction`. If the calendar push fails after local persist, keep the local data (schedule is valid) and let `CalendarSyncManager` retry via its offline queue — do not roll back.

### Files to Modify

- `ui/taskedit/TaskEditViewModel.kt` — restructure `save()` flow, fix block ID mapping

---

## 3. Swipe-to-Delete

### Behavior

- Swipe task card **left** to reveal red background with trash icon
- On swipe completion: show confirmation dialog — "Delete '[task title]'? This will also remove the calendar event."
- On confirm: `syncManager.deleteTaskEvents()` then `taskRepository.delete()` (existing `TaskListViewModel.deleteTask()` flow)
- On cancel: card snaps back to original position
- Enabled for all tasks (including completed tasks, so they retain a delete affordance)

### Files to Modify

- `ui/tasklist/TaskListScreen.kt` — wrap `TaskCard` in `SwipeToDismissBox`
- `ui/tasklist/TaskListViewModel.kt` — existing `deleteTask()` method (no change needed)

---

## 4. Swipe-to-Reschedule

### Behavior

- Swipe task card **right** to reveal purple/brand background with calendar-refresh icon
- On swipe completion, trigger rescheduling (schedule-before-delete to avoid rollback complexity):
  1. Fetch task's current `ScheduledBlock`(s)
  2. Record their time ranges as additional entries in the `busySlots` parameter (not `existingBlocks`) to prevent same-slot reassignment
  3. Run `scheduleWithConflictResolution` with the task (status reset to PENDING in memory) and the additional busy slots
  4. On success: delete old blocks and calendar events, insert new blocks, push to calendar, update status to SCHEDULED
  5. On failure: show snackbar error, original blocks remain untouched (no rollback needed)
- Show brief loading indicator during rescheduling
- Only enabled for SCHEDULED tasks

### Files to Modify

- `ui/tasklist/TaskListScreen.kt` — swipe right gesture handling
- `ui/tasklist/TaskListViewModel.kt` — new `rescheduleTask(taskId)` method

---

## 5. Show Scheduled Time & Deadline on Task Cards

### Current State

Task cards show: title, duration badge, quadrant dot, completion button.

### New Info

Add two rows below the title:

- **Scheduled time:** e.g., "Today, 6:00 - 6:15 PM" or "Mar 19, 9:00 - 11:00 AM". For splittable tasks with multiple blocks: show first block + "(+1 more)". If unscheduled (PENDING): "Not scheduled" in muted text.
- **Deadline:** e.g., "Due today, 11:59 PM" or "Due Mar 20". Only shown if deadline is set. Warning color (red/orange) if deadline is today or past.

### Data Flow

Create a Room query joining tasks with their earliest scheduled block:

```kotlin
data class TaskWithNextBlock(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val blocks: List<ScheduledBlockEntity>
)
```

Use a flat `@Query` with a DTO (avoids N+1 query issues that `@Relation` can cause with large task lists):

```kotlin
@Query("""
    SELECT t.*, sb.startTime AS nextBlockStart, sb.endTime AS nextBlockEnd
    FROM tasks t
    LEFT JOIN scheduled_blocks sb ON sb.taskId = t.id AND sb.status = 'CONFIRMED'
    ORDER BY t.id, sb.startTime ASC
""")
fun observeAllWithNextBlock(): Flow<List<TaskWithBlockDto>>
```

No schema migration needed — this is a read-only query over existing tables.

### Files to Modify

- `data/local/dao/TaskDao.kt` — new joined query
- `data/local/entity/TaskWithBlockDto.kt` — new flat DTO
- `domain/repository/TaskRepository.kt` — new method returning tasks with block info
- `data/repository/TaskRepositoryImpl.kt` — implement the joined query mapping
- `ui/tasklist/TaskListViewModel.kt` — observe joined data
- `ui/components/TaskCard.kt` — render scheduled time and deadline
- `ui/tasklist/TaskListScreen.kt` — pass new data to TaskCard

---

## 6. "Due Today" Urgency Section

### Behavior

- A prominent section appears at the **top** of the task list, above the quadrant groups, when any active task has a deadline set to today (regardless of quadrant)
- Header: "Due Today" with a warning icon and accent/red color to grab attention
- Shows only tasks where `deadline` falls on the current calendar day (compare in the device's timezone)
- Tasks in this section still appear in their normal quadrant group below — the "Due Today" section is a **duplicate view** for visibility, not a move
- If no tasks are due today, the section is hidden entirely (no empty state)
- Completed tasks with today's deadline should NOT appear in this section

### Visual Treatment

- Warning/urgency color for the section header (red or orange, consistent with the deadline warning color from Section 5)
- Task cards in this section use the same `TaskCard` component (with swipe gestures, scheduled time, etc.)
- Section should feel visually distinct from the quadrant groups — e.g., a subtle background tint or border

### Data Flow

- The `TaskListViewModel` already has access to all tasks. Filter for `deadline != null && deadline.isToday(zoneId) && status != COMPLETED` and expose as a separate list in the UI state
- No new queries needed — reuses the same `TaskWithBlockDto` data from Section 5

### Files to Modify

- `ui/tasklist/TaskListViewModel.kt` — add `dueTodayTasks` to UI state
- `ui/tasklist/TaskListScreen.kt` — render "Due Today" section above quadrant groups

---

## Non-Goals

- Drag-to-reorder tasks
- Manual time slot selection
- Recurring task rescheduling
- Notification changes
