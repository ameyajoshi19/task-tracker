# Availability, Settings & Calendar Polish

## Overview

Five independent improvements to the app's configuration and scheduling infrastructure:

1. **Multiple availability slots per day** — allow users to specify non-contiguous available hours (e.g., 9-12, 1-5)
2. **Rename task calendar & hide from UI** — rename to "Sortd Task Tracker", hide from calendar selection in onboarding and settings
3. **Show user's name in Account section** — display Google account display name above email
4. **Reorder Settings sections** — move Theme to the bottom
5. **Default sync to 15 minutes with side effects explanation** — change default and add helper text

## Feature 1: Multiple Availability Slots Per Day

### Problem

Users can only specify one contiguous availability window per day. Someone available 9am-12pm and 1pm-5pm (with a lunch break) must set 9am-5pm and accept that tasks may be scheduled during lunch.

### Design

**Data model:** No schema change needed. `UserAvailability` already represents a single slot (`dayOfWeek`, `startTime`, `endTime`, `enabled`). The DAO orders by `dayOfWeek, startTime`. Multiple rows with the same `dayOfWeek` are already supported at the data layer — only the UI was constrained to one.

**AvailabilityEditor UI changes:**

Group slots by `dayOfWeek`. For each day:
- Day toggle and day name on the first row, followed by start/end time pickers
- Additional slots render below, indented to align with the time pickers (no day name/toggle repeated)
- Each additional slot has an `IconButton` with `Icons.Default.Close` (`onSurfaceVariant` tint) to remove it
- An "+ Add slot" `TextButton` with `SortdColors.accent` text appears below the last slot
- The first slot cannot be removed — only toggled via the day switch
- The day toggle enables/disables all slots for that day

**Spacing:** 4.dp between slots within a day (new), 8.dp between days (existing).

**Add slot defaults:** New slots start with a reasonable next window. If the last slot for the day ends at 12:00, the new slot defaults to 13:00-17:00. Otherwise, default to the hour after the last slot's end time, with a 4-hour duration, capped at 23:00. If the last slot ends at or after 22:00, disable the "+ Add slot" button (not enough room for a meaningful slot).

**Overlap validation:** Prevent overlapping slots for the same day. When adding or updating a slot, validate that it doesn't overlap with other slots for that `dayOfWeek`. If overlap is detected, show a validation error or auto-adjust times.

**Callback changes:** The `AvailabilityEditor` currently takes `onUpdate: (UserAvailability) -> Unit`. It needs two additional callbacks:
- `onAdd: (UserAvailability) -> Unit` — insert a new slot
- `onRemove: (UserAvailability) -> Unit` — delete a slot

The `onUpdate` callback must identify slots by `id`, not by `dayOfWeek`, since multiple slots can share the same day.

**Onboarding state handling:** In onboarding, availabilities are held in an in-memory list (`MutableStateFlow` in `OnboardingUiState`), not persisted to Room until `saveAvailabilityAndProceed()`. The `updateAvailability()` function currently matches by `dayOfWeek`, which breaks with multiple slots per day — it must match by slot `id` instead. Add/remove operate on the in-memory list. `saveAvailabilityAndProceed()` already iterates and inserts all entries, so it handles variable-length lists.

In Settings, add/remove go directly through the repository (Room), and changes are observed via `observeAll()`.

**Onboarding defaults:** Unchanged — one slot per weekday (9:00-17:00), weekends disabled.

### Files Changed

| File | Change |
|------|--------|
| `AvailabilityEditor.kt` | Group by day, support multiple rows, add/remove buttons |
| `SettingsViewModel.kt` | Add `addAvailability()` and `removeAvailability()` functions |
| `SettingsScreen.kt` | Pass new callbacks to AvailabilityEditor |
| `OnboardingViewModel.kt` | Fix `updateAvailability()` to match by id, add `addAvailability()` and `removeAvailability()` on in-memory list |
| `OnboardingScreen.kt` | Pass new callbacks to AvailabilityEditor |

## Feature 2: Rename Task Calendar & Hide from UI

### Problem

The task calendar is named "Task Tracker" (old branding) and shows up in the calendar selection list where users can accidentally disable it.

### Design

**Rename constants** in `GoogleCalendarApiClient.kt`:
- `APP_NAME`: `"Task Tracker"` → `"Sortd Task Tracker"`
- `TASK_CALENDAR_NAME`: `"Task Tracker"` → `"Sortd Task Tracker"`
- Add `LEGACY_CALENDAR_NAME = "Task Tracker"` for migration lookup
- Update calendar description: `"Managed by Task Tracker app"` → `"Managed by Sortd"`

**Update event description** in `CalendarEventMapper.kt`:
- `"Scheduled by Task Tracker"` → `"Scheduled by Sortd"`

**Migration for existing users:** The `getOrCreateTaskCalendar()` method in `GoogleCalendarApiClient` must be updated to search for both `TASK_CALENDAR_NAME` and `LEGACY_CALENDAR_NAME`. If it finds a calendar with the legacy name, it renames it via a new `renameCalendar()` API call before returning the ID. This ensures the migration runs *before* any calendar-not-found logic that would create a duplicate.

**Add `renameCalendar()` to `CalendarRepository`:** Add `suspend fun renameCalendar(calendarId: String, newName: String)` to the interface. Implement in `GoogleCalendarApiClient` using the Google Calendar API `calendars.patch()`.

**Hide from UI:**
- `OnboardingViewModel.loadCalendars()`: After loading calendars from the API, filter out any calendar whose ID matches the stored `taskCalendarId`. If the task calendar hasn't been created yet (first run), filter by name matching `TASK_CALENDAR_NAME` or `LEGACY_CALENDAR_NAME`.
- `SettingsViewModel`: Same filter when displaying `calendarSelections`. The `SettingsViewModel` needs access to `appPreferences.taskCalendarId` flow to filter against — add it to the `combine()` block.

**Always enabled:** The task calendar is created and its ID stored during onboarding via `getOrCreateTaskCalendar()`. It's managed separately from user-selected calendars and is always active. No additional "always checked" logic needed since it's not in the selection list.

### Files Changed

| File | Change |
|------|--------|
| `GoogleCalendarApiClient.kt` | Rename constants, add `LEGACY_CALENDAR_NAME`, update `getOrCreateTaskCalendar()` to handle migration, implement `renameCalendar()` |
| `CalendarRepository.kt` | Add `suspend fun renameCalendar(calendarId: String, newName: String)` |
| `CalendarEventMapper.kt` | Update event description |
| `OnboardingViewModel.kt` | Filter task calendar from selection list |
| `SettingsViewModel.kt` | Add `taskCalendarId` flow to `combine()`, filter task calendar from displayed calendars |

## Feature 3: Show User's Name in Account Section

### Problem

The Account section only shows the email address. Showing the display name makes it feel more personal and helps users confirm they're signed into the right account.

### Design

**GoogleAuthManager:** In `handleSignInResult()`, capture `account.displayName` alongside `account.email`. Add a new `MutableStateFlow<String?>` called `_signedInDisplayName` with public `signedInDisplayName: StateFlow<String?>`. Also update the `init` block (which restores sign-in state from `GoogleSignIn.getLastSignedInAccount()`) to capture `account?.displayName` — otherwise the display name will be null after app restart.

**SettingsViewModel:** Expose `displayName` in `SettingsUiState`, sourced from `authManager.signedInDisplayName`.

**SettingsScreen:** Update the Account card to show:
```
Signed in as
Jane Doe                [Sign out]
jane@gmail.com
```

Display name in `bodyLarge`, email in `bodyMedium` with `onSurfaceVariant` color. If display name is null, fall back to current layout (email only).

### Files Changed

| File | Change |
|------|--------|
| `GoogleAuthManager.kt` | Add `signedInDisplayName` StateFlow |
| `SettingsViewModel.kt` | Expose display name in UI state |
| `SettingsScreen.kt` | Show display name above email |

## Feature 4: Reorder Settings Sections

### Problem

Theme is the first section but the least frequently changed. More important sections (Account, Availability) should be first.

### Design

Reorder sections in `SettingsScreen.kt`:

**Current:** Theme → Account → Availability → Calendars → Background Sync
**New:** Account → Availability → Calendars → Background Sync → Theme

Pure composable reorder. No logic changes.

### Files Changed

| File | Change |
|------|--------|
| `SettingsScreen.kt` | Move Theme section composable call to the end |

## Feature 5: Default Sync to 15 Minutes & Side Effects Explanation

### Problem

Default sync interval is 30 minutes, but 15 minutes is more responsive. Users also have no context for what changing the interval means.

### Design

**Default change:** Update `OnboardingViewModel.saveCalendarsAndFinish()` to use `SyncInterval.FIFTEEN_MINUTES` instead of `SyncInterval.THIRTY_MINUTES`. Also update the default fallback in `AppPreferences`. Note: existing users who already went through onboarding have `THIRTY_MINUTES` persisted — this change only affects new installs, which is intentional.

**Helper text:** Add descriptive text below the Background Sync dropdown in `SettingsScreen.kt`:

> "More frequent syncs keep your schedule up to date but use more battery in the background."

Style: `MaterialTheme.typography.bodySmall`, color `MaterialTheme.colorScheme.onSurfaceVariant`. Consistent with existing description text patterns in Settings (e.g., the Calendars section description).

### Files Changed

| File | Change |
|------|--------|
| `OnboardingViewModel.kt` | Change default to `FIFTEEN_MINUTES` |
| `AppPreferences.kt` | Update default fallback if applicable |
| `SettingsScreen.kt` | Add helper text below sync dropdown |

## Testing

- **Multiple slots:** Add two availability slots for Monday, verify scheduler uses both windows. Remove a slot, verify it's deleted.
- **Calendar rename:** Existing "Task Tracker" calendar gets renamed to "Sortd Task Tracker" on next sync. New installs create "Sortd Task Tracker" directly.
- **Calendar hidden:** Task calendar does not appear in onboarding or settings calendar lists.
- **User name:** Account section shows display name and email. Falls back to email-only if name is null.
- **Settings order:** Theme section appears last.
- **Sync default:** New onboarding sets 15-minute sync. Helper text visible below dropdown.
