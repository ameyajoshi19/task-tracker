# Deadline Pressure Reshuffling

## Problem

When a high-priority task with an approaching deadline is scheduled, the scheduler places it in the first available slot without considering whether an earlier slot could be freed by displacing lower-priority tasks. This leaves the user with less buffer time if they can't complete the task in the scheduled window.

## Solution

Add a "deadline pressure" check after direct scheduling succeeds. If the task's estimated duration consumes 25% or more of the remaining time until its deadline, attempt to reshuffle lower-priority tasks to find an earlier slot.

## Pressure Formula

```
pressure = estimatedDurationHours / hoursUntilDeadline
```

- If `pressure >= 0.25`, trigger reshuffle attempt.
- If deadline is already past or `hoursUntilDeadline <= 0`, treat pressure as `1.0` (always reshuffle).
- Tasks without deadlines skip the pressure check entirely.

## Flow Change in `scheduleWithConflictResolution()`

### Current Flow

1. Try scheduling without displacement. If succeeds, return immediately.
2. If fails, try displacing lower-priority tasks.

### New Flow

1. Try scheduling without displacement.
2. If succeeds AND task has a deadline:
   a. Calculate pressure.
   b. If `pressure >= 0.25`, attempt reshuffle by removing lower-priority blocks and re-running the scheduler with both the new task and displaced tasks.
   c. If the reshuffle produces an earlier start time for the new task, return `NeedsReschedule` so the user can approve.
   d. If the reshuffle doesn't improve timing (or no lower-priority blocks exist), return the original direct result.
3. If step 1 fails, proceed with displacement as before (unchanged).

## What Changes

- **`TaskScheduler`**: Add `DEADLINE_PRESSURE_THRESHOLD = 0.25` constant. Add pressure check between steps 1 and 2 in `scheduleWithConflictResolution()`.
- No new classes, models, or UI changes required. The existing `NeedsReschedule` result type and reschedule approval flow handle the user interaction.

## Edge Cases

- **No lower-priority blocks to displace**: Keep original scheduling, no reshuffle.
- **Reshuffle produces same or later start time**: Keep original result, no point asking the user.
- **Task has no deadline**: Skip pressure check entirely.
- **Deadline already passed**: Pressure = 1.0, always attempt reshuffle (best effort).
