# Recurring Tasks Design Spec

## Overview

Add recurring task support to the Smart Task Scheduler. Recurring tasks are templates that generate concrete task instances within the scheduling window. Two modes: **flexible-time** (scheduler picks the slot) and **fixed-time** (always at a specific time). Recurrence is interval-based: "every X days."

## Data Model

### New Entity: `RecurringTask`

A template that defines the recurrence pattern. Not scheduled directly — generates `Task` instances.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Auto-increment primary key |
| `title` | `String` | Task title |
| `description` | `String` | Optional description |
| `estimatedDurationMinutes` | `Int` | Duration per instance |
| `quadrant` | `Quadrant` | Eisenhower priority |
| `dayPreference` | `DayPreference` | Weekday/Weekend/Any |
| `splittable` | `Boolean` | Whether instances can be split |
| `intervalDays` | `Int` | Repeat every X days (1 = daily, 7 = weekly) |
| `startDate` | `LocalDate` | First occurrence date |
| `endDate` | `LocalDate?` | Optional end date; null = repeats forever |
| `fixedTime` | `LocalTime?` | null = flexible (scheduler picks), non-null = always at this time |
| `createdAt` | `Instant` | Creation timestamp |
| `updatedAt` | `Instant` | Last update timestamp |

Room entity: `recurring_tasks` table with corresponding `RecurringTaskEntity` and `RecurringTaskDao`.

### New Entity: `RecurringTaskException`

Tracks individually cancelled instances so the expander does not regenerate them.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Auto-increment primary key |
| `recurringTaskId` | `Long` | Foreign key to `RecurringTask` (CASCADE on delete) |
| `exceptionDate` | `LocalDate` | The cancelled occurrence date |

Room entity: `recurring_task_exceptions` table.

### Changes to Existing `Task`

- **Add** `recurringTaskId: Long?` — links instance back to its template (null for ad hoc tasks)
- **Add** `instanceDate: LocalDate?` — which occurrence this represents (null for ad hoc tasks)
- **Keep** `recurringPattern: String?` — currently unused, left in place to avoid destructive SQLite migration (SQLite < 3.35.0 does not support `DROP COLUMN`)

`TaskEntity` updated with new columns. Foreign key from `recurringTaskId` to `recurring_tasks.id` with CASCADE on delete. UNIQUE index on `(recurringTaskId, instanceDate)` to prevent duplicate instances.

**Note:** Generated recurring instances always have `deadline = null`. Recurring tasks do not participate in deadline-pressure logic or `DeadlineAtRisk` results — this is by design since recurring tasks represent ongoing commitments, not deadline-driven work.

## Recurrence Expander

**Class:** `RecurrenceExpander` in `domain/scheduler/`

Pure Kotlin, stateless, no Android dependencies. Generates task instances for a bounded scheduling window.

### Input

- `recurringTask: RecurringTask`
- `exceptions: List<RecurringTaskException>` — cancelled dates
- `existingInstances: List<Task>` — already-generated instances in the window (matched by `recurringTaskId` + `instanceDate`)
- `windowStart: LocalDate`
- `windowEnd: LocalDate`

### Logic

1. Generate occurrence dates: start from `startDate`, step by `intervalDays`
2. Stop at `endDate` or `windowEnd`, whichever is earlier
3. Skip dates before `windowStart`
4. Filter out dates in `exceptions`
5. Filter out dates that already have a generated instance
6. For each remaining date, create a `Task` with fields copied from the template, `recurringTaskId` set, `instanceDate` set, `status = PENDING`

### Output

`List<Task>` — new instances to persist. The caller (ViewModel or WorkManager) handles persistence.

### Fixed-Time Conflict Detection During Expansion

When the expander generates a fixed-time instance, it must check for conflicts with other fixed-time tasks (recurring or ad hoc) on that date. If a conflict is discovered during expansion (possible when two recurring tasks with different intervals eventually overlap beyond the initial validation window), the conflicting instance is skipped and the user is notified. This is a known limitation of validating only within the current window — acknowledged as an acceptable trade-off since the user is alerted when it occurs.

### `dayPreference` and `intervalDays` Interaction

The expander always generates dates by stepping `intervalDays` regardless of `dayPreference`. The `dayPreference` field is copied to the generated `Task` instance and respected by the scheduler's `SlotFinder` when finding available slots for flexible-time instances. For fixed-time instances, `dayPreference` is informational only — the task is placed at its fixed time regardless of the day.

## Scheduler Integration

### Flow

When scheduling is triggered (ViewModel or background sync):

1. **Expand:** For each `RecurringTask`, run `RecurrenceExpander` over the scheduling window (currently `today` to `today + 14 days`). **Persist new instances to the database** so they receive real IDs. This is a hard requirement — unpersisted tasks (id=0) would collide in the scheduler's internal maps.
2. **Partition:** Separate fixed-time instances (`fixedTime != null`) from all other tasks.
3. **Place fixed-time instances:** Convert `instanceDate` + `fixedTime` + `estimatedDurationMinutes` into concrete `TimeSlot` objects. Add these to the `busySlots` list. Create `ScheduledBlock` entries for them directly.
4. **Displace conflicts:** Any existing flexible/ad hoc `ScheduledBlock` that overlaps a fixed-time slot gets cancelled. The displaced task re-enters the pool as PENDING. **This displacement uses the existing `NeedsReschedule` flow** — the user sees a confirmation screen showing which tasks were displaced and their proposed new slots, consistent with the existing conflict resolution UX.
5. **Run best-fit:** The existing slot-centric best-fit algorithm runs unchanged with the remaining tasks (flexible recurring instances + ad hoc tasks + displaced tasks).

### Fixed-Time Conflict Detection

Fixed-time vs fixed-time conflicts are detected at **validation time** (task creation/edit), not during scheduling:

- When creating or editing a fixed-time recurring task, check whether any existing fixed-time task (recurring or ad hoc) occupies the same slot on any generated instance date within the scheduling window.
- If conflict found: surface an error to the user. The new fixed-time task is not created.
- This validation lives in the validation layer alongside `TaskValidator`.

### No Changes to Core Algorithm

The existing `TaskScheduler.schedule()` and `TaskScheduler.scheduleWithConflictResolution()` methods are unchanged. Fixed-time tasks are handled as a pre-processing step (injected into busy slots), and flexible recurring instances are regular `Task` objects.

## Deletion Logic

### Delete Single Instance

1. Create a `RecurringTaskException` for the instance's date
2. Delete the `Task` instance and its `ScheduledBlock` entries
3. Delete corresponding Google Calendar events
4. **No rescheduling triggered** — freed slots are not auto-filled

### Delete This and All Future Instances

1. Set `endDate` on the `RecurringTask` template to the day before the selected instance's `instanceDate`
2. Delete all `Task` instances with `instanceDate >= selectedDate` for this `recurringTaskId`
3. Delete their `ScheduledBlock` entries and Google Calendar events
4. **No rescheduling triggered**

### Delete Entire Recurring Task

1. **Collect all affected data first:** Query all `Task` instances for this `recurringTaskId`, then all `ScheduledBlock` entries for those tasks, then all Google Calendar event IDs from those blocks.
2. Delete Google Calendar events for all collected blocks (via `CalendarSyncManager`).
3. Delete the `RecurringTask` template — CASCADE deletes all instances, their blocks, and exceptions at the SQLite level.
4. **No rescheduling triggered**

**Important ordering:** Google Calendar event IDs must be collected *before* the CASCADE delete fires, since the CASCADE happens at the SQLite level and does not trigger DAO callbacks or repository logic.

## UI Design

All new UI components use `MaterialTheme.colorScheme` tokens exclusively — no hardcoded colors. Both light and dark themes are supported through the existing `SortdDarkColorScheme` and `SortdLightColorScheme`.

### Task Creation / Edit Screen

The recurring task fields are added to the existing `TaskEditScreen`, placed after the Splittable toggle.

**Recurring task toggle:**
- Identical style to the Splittable toggle: `surfaceVariant` background, `outline` border, 12dp rounded corners, `Switch` with `SortdColors.accent` track when checked
- Title: "Recurring task"
- Subtitle: "Repeats on a schedule"

**When toggle is ON — recurring fields appear indented:**
- Left border: 2dp, `SortdColors.accent` color
- Margin-left: 12dp, padding-left: 14dp
- Fields inside the indent:
  - **REPEAT EVERY** — number input with accent border + "days" label
  - **START DATE** — date trigger field (same pattern as `DeadlinePicker`'s `TriggerField`), default: today
  - **END DATE** — date trigger field, placeholder: "No end date"
  - **Fixed time toggle** — same switch style as Splittable/Recurring toggles
    - Title: "Fixed time"
    - Subtitle: "Always at a specific time"

**When fixed time toggle is ON:**
- Nested indent (2dp left border, `SortdColors.accentLight`)
- **TIME** — time trigger field (same pattern as `DeadlinePicker`'s time field), opens the existing `TimeWheel` component

**Confirmation summary banner (when fixed time is ON):**
- Appears below the recurring fields section
- Same style as the deadline confirmation summary: `SortdColors.accent` at 10% opacity background, 20% opacity border, 10dp rounded corners
- Text: "Every X days at H:MM AM/PM, starting MMM D"

### Deletion Dialog

Material 3 `AlertDialog` style, centered:

- **Icon:** Recurrence icon (centered, 28sp)
- **Title:** "Delete recurring task" (centered, 18sp, `onSurface`)
- **Body:** "{Task title} repeats every X days. What would you like to delete?" (centered, 13sp, `onSurfaceVariant`)
- **Button 1:** "Delete this instance" — `error` color at 15% opacity background, `error` border at 30% opacity, subtitle: "Only {date}"
- **Button 2:** "Delete this & future" — `error` color at 8% opacity background, `error` border at 15% opacity, subtitle: "Stop from {date} onward"
- **Cancel:** text button, `onSurfaceVariant` color

### Task List Indicators

- Recurring task instances display a small recurrence indicator to distinguish them from ad hoc tasks
- The indicator uses the same subtle styling as existing metadata (e.g., block count, next scheduled time)
- `TaskWithScheduleInfo` is extended with `recurringTaskId: Long?` so the UI can detect recurring instances and show the indicator without additional queries

## Repository Layer

### New: `RecurringTaskRepository`

Interface in `domain/repository/`:

```kotlin
interface RecurringTaskRepository {
    suspend fun insert(recurringTask: RecurringTask): Long
    suspend fun update(recurringTask: RecurringTask)
    suspend fun delete(recurringTask: RecurringTask)
    suspend fun getById(id: Long): RecurringTask?
    fun observeAll(): Flow<List<RecurringTask>>
    suspend fun getAll(): List<RecurringTask>
}
```

Implementation in `data/repository/` backed by `RecurringTaskDao`.

### New: `RecurringTaskExceptionRepository`

```kotlin
interface RecurringTaskExceptionRepository {
    suspend fun insert(exception: RecurringTaskException): Long
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskException>
    suspend fun deleteByRecurringTaskId(recurringTaskId: Long)
}
```

### Changes to `TaskRepository`

Add query for fetching instances by recurring task ID:

```kotlin
suspend fun getByRecurringTaskId(recurringTaskId: Long): List<Task>
suspend fun getByRecurringTaskIdAndDateRange(
    recurringTaskId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Task>
```

## Editing a Recurring Task Template

When a user edits fields on a `RecurringTask` template (e.g., duration, quadrant, title):

- Changes apply only to **newly generated instances** going forward.
- Already-generated instances (persisted `Task` rows) are not retroactively updated.
- Already-scheduled blocks are not affected.
- This is consistent with how Google Calendar handles editing "this and future events" — past instances retain their original properties.

If the user wants to change an already-scheduled instance, they edit the `Task` instance directly (standard task edit flow).

## Database Migration

Room migration adds:
- `recurring_tasks` table
- `recurring_task_exceptions` table
- `recurring_task_id` and `instance_date` columns to `tasks` table (both nullable)
- UNIQUE index on `(recurring_task_id, instance_date)` to prevent duplicate instances
- Foreign key index on `recurring_task_id`
- `recurring_pattern` column is **retained** (nullable, unused) to avoid destructive migration

## Validation

Extend `TaskValidator` or create `RecurringTaskValidator`:

- `intervalDays` must be >= 1
- `startDate` must not be in the past — **this validation applies only at creation time**, not when editing or re-validating existing recurring tasks (whose `startDate` will naturally be in the past after running for a while)
- `endDate` (if set) must be after `startDate`
- `estimatedDurationMinutes` follows existing rules (15-480, 5-minute increments)
- Fixed-time conflict check: no overlapping fixed-time tasks on generated instance dates within the scheduling window

## Google Calendar Sync

Recurring task instances sync to Google Calendar as individual events (not native recurring events). Each instance's `ScheduledBlock` gets its own calendar event via the existing `CalendarSyncManager.pushNewBlock()` flow. No changes to the sync layer.

## Scope Boundaries

**In scope:**
- RecurringTask CRUD
- Instance generation within scheduling window
- Fixed-time and flexible-time modes
- Deletion (single instance, this & future)
- UI for creation, deletion dialog, task list indicators
- Light and dark theme support

**Out of scope:**
- Day-of-week patterns (Mon/Wed/Fri)
- RRULE / iCalendar native recurrence
- Google Calendar native recurring events
- Configurable scheduling window size
- Editing individual instances differently from template
