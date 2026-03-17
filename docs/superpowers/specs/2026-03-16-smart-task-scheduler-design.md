# Smart Task Scheduler — Design Spec

An Android app that plans, schedules, and manages tasks by intelligently assigning them to time slots on the user's Google Calendar based on priority, deadlines, and availability.

## Core Concept

When a user adds a task with an estimated duration, priority (Eisenhower quadrant), deadline, and weekday/weekend preference, the app automatically finds the best available time slot and schedules it on a dedicated Google Calendar. It reads existing events from user-selected calendars to avoid conflicts, and proposes rescheduling when new tasks require displacing existing ones.

## Architecture

**MVVM with Clean Architecture**, three layers:

- **UI Layer** — Jetpack Compose screens + ViewModels with StateFlow
- **Domain Layer** — Use cases for scheduling logic, task management, calendar sync
- **Data Layer** — Room database for local persistence, Google Calendar API for sync

Data flows UI → ViewModel → Use Case → Repository → Data Source. Calendar data also flows inward: the app reads free/busy data from Google Calendar to inform scheduling decisions.

## Data Model

### Task

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| title | String | Required |
| description | String | Optional |
| estimatedDurationMinutes | Int | Required. Minimum: 15 min, maximum: 480 min (8 hours). Granularity: 5-minute increments. Must not exceed longest availability window if task is not splittable (validated at creation and re-checked if availability changes). |
| quadrant | Enum | URGENT_IMPORTANT, IMPORTANT, URGENT, NEITHER |
| deadline | Instant? | Optional. Stored as UTC, displayed in local timezone. |
| dayPreference | Enum | WEEKDAY, WEEKEND, ANY |
| splittable | Boolean | Whether the task can be split across multiple time blocks. Default: false. Minimum block size: 30 minutes. Split blocks can span multiple days. |
| status | Enum | PENDING, SCHEDULED, IN_PROGRESS, COMPLETED. A splittable task is IN_PROGRESS when some blocks are completed but others remain. |
| recurringPattern | String? | Nullable, reserved for future use |
| createdAt | Instant | Auto-set, stored as UTC |
| updatedAt | Instant | Auto-set, stored as UTC |

### ScheduledBlock

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| taskId | Long | Foreign key to Task |
| startTime | Instant | Stored as UTC, displayed in local timezone |
| endTime | Instant | Stored as UTC, displayed in local timezone |
| googleCalendarEventId | String? | For sync with Google Calendar |
| status | Enum | PROPOSED, CONFIRMED, COMPLETED, CANCELLED |

A task can have multiple ScheduledBlocks if it is splittable.

### UserAvailability

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| dayOfWeek | DayOfWeek | MONDAY through SUNDAY |
| startTime | LocalTime | |
| endTime | LocalTime | |
| enabled | Boolean | |

### CalendarSelection

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| googleCalendarId | String | Calendar ID from Google |
| calendarName | String | Cached display name |
| calendarColor | String | Cached hex color string from Google Calendar API |
| enabled | Boolean | Whether to consider for conflict detection |

## Scheduling Algorithm

Runs on-device whenever a task is added/updated or a calendar conflict is detected.

### Priority Sort Order

Tasks are sorted into a priority-ordered list used as a lookup during slot assignment:
1. Urgent+Important > Important > Urgent > Neither
2. Within the same quadrant, nearer deadlines first
3. Tasks without deadlines go last within their quadrant
4. Tie-breaker: earlier `createdAt` first

### Steps

1. **Gather inputs** — all pending/scheduled tasks, user availability windows, free/busy data from selected Google calendars.
2. **Build priority-sorted task list** using the sort order above. This list is used as a lookup, not iterated directly.
3. **Find available slots** — intersect availability windows with calendar free time, respecting weekday/weekend preference. Sort slots chronologically.
4. **Assign time blocks (slot-centric best-fit):**
   - Iterate over available slots in chronological order
   - For each slot, find the highest-priority unscheduled task from the sorted list that fits the slot's duration
   - If the highest-priority task doesn't fit (and isn't splittable), try the next highest-priority task that does fit
   - For splittable tasks: if the slot is >= 30 minutes but smaller than the task's remaining duration, assign a partial block and continue scheduling remaining duration in later slots
   - Tasks with deadlines: only consider slots that fall before the deadline
   - This maximizes slot utilization — a 1-hour slot gets filled by an appropriately-sized task rather than left empty because the top-priority task needs 2 hours
5. **Conflict resolution** — when a new task can't fit without displacing others:
   - Identify lower-priority tasks that can be moved
   - Generate a proposed reschedule showing what moves where
   - Present to user for confirmation (suggest-and-confirm flow)

### ScheduledBlock Initial Status

- When a new task is auto-scheduled (no conflicts): blocks are created as CONFIRMED and the calendar event is created immediately.
- When rescheduling is needed: new and moved blocks are created as PROPOSED. On user approval, they transition to CONFIRMED and calendar events are created/updated. On rejection, PROPOSED blocks are discarded.

### Edge Cases

- **Deadline at risk:** If a task can't fit before its deadline even after reshuffling, alert the user with options: extend availability hours, push other tasks, or adjust the deadline.
- **No available slots:** Notify the user that no slots match the task's constraints.
- **Non-splittable task exceeds all slots:** If a non-splittable task's duration exceeds every available slot, notify the user and suggest either making it splittable or extending an availability window.
- **Splittable task partial completion:** When some blocks of a splittable task are completed, the task status moves to IN_PROGRESS. Only remaining blocks are considered for rescheduling. The task becomes COMPLETED when all blocks are completed.
- **Availability window shrunk retroactively:** If user edits availability such that a non-splittable task no longer fits any slot, notify the user and suggest making it splittable or restoring the availability window.
- **Calendar with limited permissions:** Some shared calendars may only expose "Busy" without event details. The Schedule View shows "Busy" for these slots; the scheduler still treats them as occupied.

## Google Calendar Integration

### Authentication

Google Sign-In with OAuth 2.0 via Credential Manager API. Required scopes:
- `calendar.readonly` — read free/busy data from selected calendars
- `calendar.events` — create/update events on the Tasks calendar

### Tasks Calendar

On first setup, the app creates a dedicated "Task Tracker" calendar. All scheduled task blocks are written as events on this calendar, keeping them visually distinct from user-created events.

### Calendar Selection

Users can select which of their Google calendars are checked for conflicts. Only enabled calendars contribute free/busy data to the scheduler. Configured in Settings.

### Sync Behavior

| Trigger | Action |
|---------|--------|
| Task created/scheduled | Create calendar event on Tasks calendar |
| Task completed | Update event title to prefix "Completed: " (event is preserved, not deleted) |
| Task rescheduled | Update existing calendar event times |
| App opened | Refresh free/busy data, trigger rescheduling if conflicts detected |
| Background sync | WorkManager `PeriodicWorkRequest` with 30-min interval and flex window to respect Doze mode. Uses `KEEP` existing work policy. Configurable interval in Settings (15 min / 30 min / 1 hour / manual only). Respects battery — uses `Constraints.Builder().setRequiredNetworkType(CONNECTED)`. Google Calendar FreeBusy API calls are lightweight and well within the standard API quota at this frequency. |

### Task Deletion

When a user deletes a task:
- All associated ScheduledBlocks are cancelled
- Corresponding Google Calendar events are deleted
- Freed-up slots become available for other pending tasks (scheduler re-runs)

### External Modifications to Task Tracker Calendar

During each sync (app open or background), the app compares local ScheduledBlocks with Google Calendar events on the "Task Tracker" calendar:
- **Event deleted externally:** Mark the ScheduledBlock as CANCELLED, set the task back to PENDING, and re-run the scheduler.
- **Event moved externally:** Update the ScheduledBlock times to match. If the new time conflicts with availability windows, notify the user.
- **Task Tracker calendar deleted:** Detect on next sync, prompt user to recreate it, and re-push all confirmed blocks.

### Tasks Calendar Detection

On app launch, the app looks for an existing "Task Tracker" calendar by a stored calendar ID (saved in local preferences after first creation). If the ID is not found, it searches by name before creating a new one, avoiding duplicates.

### Two-Way Flow

- **App → Calendar:** Task blocks are created/updated on the "Task Tracker" calendar
- **Calendar → App:** Free/busy data is read from all user-selected calendars (including primary) to detect occupied slots before scheduling

## Offline Behavior

The app is local-first — tasks can always be created and edited offline. Scheduling behavior when offline:

- **Task creation:** Task is saved locally with status PENDING. Scheduling runs using locally cached free/busy data (from last sync).
- **Stale data warning:** If cached free/busy data is older than 2 hours, the scheduler shows a warning that the schedule may conflict with recent calendar changes.
- **Sync on reconnect:** When connectivity is restored, the app syncs with Google Calendar: pushes any pending calendar events, fetches fresh free/busy data, and re-validates the schedule. If conflicts are detected, the reschedule proposal flow is triggered.
- **Calendar events queued:** Any calendar creates/updates that couldn't be pushed offline are queued and executed on reconnect.

## Notifications

- **Reschedule proposal (background sync):** Android system notification when background sync detects a conflict and generates a reschedule proposal. Tapping opens the Reschedule Proposal screen.
- **Deadline at risk:** System notification when a task can't be scheduled before its deadline.
- **Scheduling complete:** In-app notification (snackbar) when a newly added task is successfully scheduled.

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Google auth token expired | Prompt user to re-authenticate via in-app banner |
| Calendar API rate limit | Back off and retry with exponential delay; notify user if sync is delayed |
| Calendar creation fails | Retry on next sync; show error in Settings with manual retry option |
| Network failure during sync | Queue changes locally, sync on reconnect (see Offline Behavior) |
| Room migration needed | Use Room's built-in migration support; destructive migration only as last resort with user warning |

## Screens

### 1. Onboarding
- Google Sign-In
- Set availability windows (per day of week)
- Select which calendars to consider for conflicts

### 2. Task List
- All tasks grouped by Eisenhower quadrant
- Status indicators (pending, scheduled, completed)
- Tap to view/edit, swipe or tap to mark complete

### 3. Add/Edit Task
- Title, description
- Estimated duration
- Eisenhower quadrant selector
- Deadline picker (optional)
- Weekday/Weekend/Any preference
- Splittable toggle

### 4. Schedule View
- Daily and weekly calendar view
- Shows scheduled task blocks alongside regular calendar events from selected calendars
- Regular events are fetched via the Calendar Events API (not just free/busy) and cached locally for display
- Task blocks visually distinct (color-coded from dedicated calendar)
- Occupied non-task slots show event title and time; tapping opens the event in Google Calendar

### 5. Reschedule Proposal
- Shown when adding a task requires moving existing tasks
- Displays what will change: which tasks move, from when to when
- Approve or reject buttons
- On reject, user can manually adjust or cancel the new task

### 6. Settings
- Availability windows (edit per day)
- Google Calendar selection (toggle calendars on/off)
- Google account management

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Compose Navigation |
| State management | ViewModels + StateFlow |
| Dependency injection | Hilt |
| Local database | Room |
| Calendar API | Google Calendar REST API via Google Play Services |
| Background work | WorkManager |
| Auth | Google Sign-In (Credential Manager API) |

## Future Considerations (Not in v1)

- **Recurring tasks** — data model supports it (recurringPattern field), UI and scheduling logic deferred
- **Cross-device sync** — could add a backend later (Approach 3 from brainstorming)
- **Smarter scheduling** — could move algorithm to cloud for more sophisticated optimization
