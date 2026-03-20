# Sortd — Smart Task Scheduler

An Android app that intelligently schedules tasks on Google Calendar using Eisenhower matrix prioritization and a slot-centric best-fit algorithm. Users define their availability windows and the app finds optimal time slots for tasks across a 2-week scheduling horizon.

---

## Features

- **Smart scheduling** — slot-centric best-fit algorithm fills time slots chronologically with the highest-priority task that fits
- **Eisenhower matrix prioritization** — Urgent & Important → Important → Urgent → Neither
- **Google Calendar 2-way sync** — reads free/busy data, creates events for scheduled tasks
- **Recurring tasks** — "every X days" with fixed-time (immovable) or flexible-time modes
- **Splittable tasks** — break large tasks across multiple time slots (30-minute minimum blocks)
- **Conflict resolution** — when new tasks displace existing ones, user reviews proposed changes before applying
- **Deadline awareness** — tasks with deadlines receive priority pressure in the scheduling engine
- **Background sync** — WorkManager-based sync with offline queue for failed operations
- **Daily summary notification** — morning digest of the day's scheduled tasks
- **Day preference** — schedule tasks on weekdays, weekends, or any day
- **Dark and light theme support**

---

## How Scheduling Works

1. User creates a task with title, duration, priority quadrant, and an optional deadline
2. `TaskValidator` checks constraints (duration 15–480 min, fits within availability windows)
3. `SlotFinder` generates available time windows from user availability minus Google Calendar busy slots
4. `TaskScheduler` iterates slots chronologically, filling each with the highest-priority task that fits
5. Outcomes: `Scheduled` (success), `NeedsReschedule` (displacement required), `DeadlineAtRisk`, or `NoSlotsAvailable`
6. Scheduled blocks sync to Google Calendar as events

## How Recurring Tasks Work

- User creates a recurring task template with an interval (every X days) and an optional end date
- **Fixed-time mode** — always scheduled at a specific time; immovable and displaces flexible tasks if there is a conflict
- **Flexible-time mode** — the scheduler picks the best available slot each cycle
- `RecurrenceExpander` generates instances within the 14-day scheduling window
- Deletion options: single instance, this and future instances, or the entire recurring task

---

## Tech Stack

| Layer | Technologies |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, Clean Architecture, Hilt |
| Local storage | Room |
| Background work | WorkManager |
| Calendar integration | Google Calendar API |
| Navigation | Compose Navigation |
| Preferences | DataStore |

---

## Architecture

MVVM with Clean Architecture, organized into three layers:

- **Domain** (`domain/`) — pure Kotlin business logic: models, repository interfaces, scheduling engine, validation. No Android dependencies.
- **Data** (`data/`) — Android-dependent implementations: Room database, repository implementations, Google Calendar API client, WorkManager sync workers.
- **UI** (`ui/`) — Jetpack Compose screens, ViewModels with StateFlow, navigation graph.

Data flows: UI → ViewModel → Use Case → Repository → Data Source. Calendar free/busy data flows inward to inform the scheduler.

---

## Project Structure

```
app/src/main/java/com/tasktracker/
├── domain/              # Pure Kotlin business logic
│   ├── model/           # Task, ScheduledBlock, RecurringTask, etc.
│   ├── repository/      # Repository interfaces
│   ├── scheduler/       # TaskScheduler, SlotFinder, RecurrenceExpander
│   └── validation/      # TaskValidator, RecurringTaskValidator
├── data/                # Android-dependent implementations
│   ├── local/           # Room database, entities, DAOs, converters
│   ├── repository/      # Repository implementations
│   ├── calendar/        # Google Calendar API client, sync manager
│   ├── sync/            # WorkManager workers and schedulers
│   └── preferences/     # DataStore preferences
├── ui/                  # Jetpack Compose UI
│   ├── tasklist/        # Main task list screen
│   ├── taskedit/        # Task creation and editing
│   ├── schedule/        # Calendar view of scheduled blocks
│   ├── reschedule/      # Conflict resolution screen
│   ├── settings/        # Settings screens (account, availability, calendars, sync, theme, daily summary)
│   ├── onboarding/      # First-time setup (availability + calendar selection)
│   ├── signin/          # Google Sign-In
│   ├── components/      # Shared composables
│   ├── navigation/      # Nav graph and screen routes
│   ├── notification/    # NotificationHelper
│   └── theme/           # Colors, typography, theme
├── di/                  # Hilt dependency injection modules
├── MainActivity.kt
└── TaskTrackerApplication.kt
```

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (JVM)
./gradlew test

# Run a single test class
./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest"

# Run instrumented tests (requires emulator or connected device)
./gradlew connectedDebugAndroidTest

# Install on connected device
./gradlew installDebug
```
