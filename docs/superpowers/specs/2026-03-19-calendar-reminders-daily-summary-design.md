# Calendar Event Reminders & Daily Summary Notification

**Date:** 2026-03-19

## Overview

Two notification features: (1) add a 10-minute popup reminder to every Google Calendar event created by the app, and (2) a configurable daily morning notification summarizing the user's scheduled tasks for the day.

## 1. Google Calendar Event Reminders

### Current State

`CalendarEventMapper.toGoogleEvent()` creates events with summary, description, start/end times, and timezone. No reminders are set — events inherit the user's default Google Calendar reminder settings (which may be none).

### Design

When creating or updating a Google Calendar event, set a 10-minute popup reminder override:

```kotlin
event.reminders = Event.Reminders()
    .setUseDefault(false)
    .setOverrides(listOf(EventReminder().setMethod("popup").setMinutes(10)))
```

This uses the Google Calendar API's native reminder system. The user's device shows a Google Calendar notification 10 minutes before the event. No changes to Sortd's notification code, permissions, or scheduling are needed.

The reminder is set in `toGoogleEvent()` so it applies to both new events and updated/rescheduled events.

### Files Changed

- `CalendarEventMapper.kt` — add reminder override to `toGoogleEvent()`

## 2. Daily Summary Notification

### Current State

The app has notification infrastructure (`NotificationHelper` with `CHANNEL_RESCHEDULE` and `CHANNEL_DEADLINE`), WorkManager periodic sync (`SyncScheduler`, `CalendarSyncWorker`), and `AppPreferences` for DataStore-backed settings. No daily summary exists.

### Design

**Scheduling:** A `PeriodicWorkRequest` with a 24-hour interval scheduled via a new `DailySummaryScheduler`. Uses `setInitialDelay` to align the first run with the user's chosen time. If the target time has already passed today, the initial delay targets tomorrow at that time. Re-registered on app start (`TaskTrackerApplication.onCreate()`) using `ExistingPeriodicWorkPolicy.UPDATE` so that any time changes from settings take effect immediately, while normal app restarts just refresh the same schedule.

**Worker:** `DailySummaryWorker` (new `CoroutineWorker`):
1. Checks if daily summary is enabled (reads `AppPreferences.dailySummaryEnabled`)
2. If disabled, returns `Result.success()` immediately
3. Queries scheduled blocks for today from the repository
4. If no tasks scheduled today, returns `Result.success()` (no notification)
5. Builds and shows a summary notification via `NotificationHelper`

**Notification:**
- Channel: `CHANNEL_DAILY_SUMMARY`, importance HIGH (sound, heads-up, vibration)
- Title: "Ready to crush it today?"
- Body: "You have X tasks lined up. Let's get them done!" (singular "1 task" / "Let's get it done!" when count is 1)
- Tap action: opens app to task list (same pattern as existing reschedule notifications)

**Settings:**
- Two new `AppPreferences` entries:
  - `dailySummaryEnabled: Flow<Boolean>` (default `true`)
  - `dailySummaryTime: Flow<String>` (default `"08:00"`, stored as HH:mm)
- New settings sub-page: `DailySummaryScreen` with:
  - Toggle to enable/disable
  - Time picker to set the notification time (only shown when enabled)
- New row in the Settings hub: "Daily Summary" with subtitle showing the time or "Off"
- New route: `SettingsDailySummary`

**Rescheduling:** `DailySummaryScheduler` (new class, similar to `SyncScheduler`):
- `schedule(time: LocalTime)` — calculates initial delay from now to next occurrence of the target time, creates `PeriodicWorkRequest` with 24h interval
- `cancel()` — cancels the work
- Called from:
  - `TaskTrackerApplication.onCreate()` — ensures worker is always registered
  - `SettingsViewModel` — when user changes time or toggle

### Files Changed

- New: `data/sync/DailySummaryWorker.kt`
- New: `data/sync/DailySummaryScheduler.kt`
- New: `ui/settings/DailySummaryScreen.kt`
- Modify: `ui/notification/NotificationHelper.kt` — add `CHANNEL_DAILY_SUMMARY` channel and `showDailySummary(taskCount: Int)` method
- Modify: `data/preferences/AppPreferences.kt` — add `dailySummaryEnabled` and `dailySummaryTime` preferences
- Modify: `ui/settings/SettingsScreen.kt` — add Daily Summary row to hub
- Modify: `ui/settings/SettingsViewModel.kt` — add daily summary methods and subtitle
- Modify: `ui/navigation/Screen.kt` — add `SettingsDailySummary` route
- Modify: `ui/navigation/NavGraph.kt` — add `DailySummaryScreen` destination
- Modify: `TaskTrackerApplication.kt` — schedule daily summary on app start

## Testing

- **Calendar reminders:** Verify created events have a 10-minute popup reminder override. Verify updated/rescheduled events retain the reminder.
- **Daily summary worker:** Verify notification is shown when tasks exist, not shown when no tasks or disabled. Verify correct singular/plural text.
- **Settings:** Verify toggle enables/disables the worker. Verify time picker reschedules the worker. Verify settings persist across app restarts.
- **Scheduling:** Verify worker fires at the configured time. Verify rescheduling after time change.
