# Scheduling Fixes & Task UX Improvements

## Overview

Five targeted fixes and features addressing scheduling reliability and task list usability:

1. Fix conflict resolution so higher-priority tasks displace lower-priority ones
2. Atomic save — don't persist tasks when scheduling fails
3. Swipe-to-delete with confirmation and calendar event cleanup
4. Swipe-to-reschedule with old slot blocking
5. Show scheduled time and deadline on task cards

## Context

User availability: 6-8 PM weekdays, 9 AM-5 PM weekends. When a 2h Important task fills the entire 6-8 PM window and a 15m Urgent & Important task with a same-day deadline is created, the scheduler fails with "cannot schedule before deadline" instead of displacing the lower-priority task. The task is also incorrectly persisted despite the error.

---

## 1. Fix Rescheduling (Conflict Resolution)

### Problem

`scheduleWithConflictResolution` calculates deadline pressure as `duration / minutesUntilDeadline`. For a 15m task with hours remaining, this ratio is far below the 0.25 threshold, so the displacement path is never triggered. The scheduler only looks for free slots, finds none (the window is full), and returns `DeadlineAtRisk`.

### Solution

Change the displacement trigger in `scheduleWithConflictResolution`:

- **Current logic:** Only attempt displacement when `deadlinePressure >= 0.25`
- **New logic:** Also attempt displacement when **no free slots exist** for the new task and there are lower-priority confirmed blocks in the feasible scheduling window
- When displacing: remove the lowest-priority block(s) overlapping the new task's window, schedule the new task first, then re-run scheduling for displaced tasks
- If a displaced task no longer fits in remaining availability (e.g., the 2h task can't fit in 1h45m remaining), move it to the next available day or split if splittable
- Return `NeedsReschedule` with details of what moved so the user is informed

### Expected Result (example scenario)

- "Follow up with lawyers" (15m, Urgent & Important) → 6:00-6:15 PM today
- "Upload tax documents" (2h, Important) → moved to next available slot, user sees what moved

### Files to Modify

- `domain/scheduler/TaskScheduler.kt` — `scheduleWithConflictResolution()` displacement trigger logic

---

## 2. Atomic Save

### Problem

`TaskEditViewModel.save()` inserts the task first (PENDING status), then runs scheduling. On scheduling failure, the task remains in the database with no calendar event while the user sees an error — confusing UX.

### Solution

Restructure the save flow to schedule-before-persist:

1. **Validate** — title, duration, availability constraints (existing logic)
2. **Build task in memory** — create `Task` object with temporary ID (0), do not insert
3. **Gather scheduling inputs** — fetch existing tasks, blocks, free/busy slots, availability (existing logic)
4. **Run scheduler** — call `scheduleWithConflictResolution` with the in-memory task included in the task list
5. **Check result:**
   - `Scheduled` or `NeedsReschedule`: insert task into DB (get real ID), remap block task IDs, insert blocks, push to calendar, navigate away
   - `DeadlineAtRisk` or `NoSlotsAvailable`: show error, persist nothing
6. **For edits:** only delete old blocks/events and update the task if rescheduling succeeds

### Files to Modify

- `ui/taskedit/TaskEditViewModel.kt` — restructure `save()` flow

---

## 3. Swipe-to-Delete

### Behavior

- Swipe task card **left** to reveal red background with trash icon
- On swipe completion: show confirmation dialog — "Delete '[task title]'? This will also remove the calendar event."
- On confirm: `syncManager.deleteTaskEvents()` then `taskRepository.delete()` (existing `TaskListViewModel.deleteTask()` flow)
- On cancel: card snaps back to original position
- Only enabled for non-completed tasks

### Files to Modify

- `ui/tasklist/TaskListScreen.kt` — wrap `TaskCard` in `SwipeToDismissBox`
- `ui/tasklist/TaskListViewModel.kt` — existing `deleteTask()` method (no change needed)

---

## 4. Swipe-to-Reschedule

### Behavior

- Swipe task card **right** to reveal purple/brand background with calendar-refresh icon
- On swipe completion, trigger rescheduling:
  1. Fetch task's current `ScheduledBlock`(s)
  2. Record their time ranges as additional "busy" slots (preventing same-slot reassignment)
  3. Delete existing blocks and calendar events
  4. Set task status to PENDING
  5. Run `scheduleWithConflictResolution` with the additional blocked slots
  6. On success: insert new blocks, push to calendar, update status to SCHEDULED
  7. On failure: show snackbar error, restore original blocks (rollback)
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

Or a simpler `@Query` returning a flat DTO with the earliest block's start/end time. The ViewModel observes this instead of plain tasks.

### Files to Modify

- `data/local/dao/TaskDao.kt` — new query joining tasks with blocks
- `data/local/entity/` — new `TaskWithNextBlock` data class (or DTO)
- `ui/tasklist/TaskListViewModel.kt` — observe joined data
- `ui/components/TaskCard.kt` — render scheduled time and deadline
- `ui/tasklist/TaskListScreen.kt` — pass new data to TaskCard

---

## Non-Goals

- Drag-to-reorder tasks
- Manual time slot selection
- Recurring task rescheduling
- Notification changes
