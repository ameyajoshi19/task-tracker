# Availability, Settings & Calendar Polish — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multiple availability slots per day, rename/hide the task calendar, show user display name in settings, reorder settings sections, and change sync defaults.

**Architecture:** Five independent changes. Feature 1 (multi-slot availability) touches the AvailabilityEditor UI and both Settings/Onboarding ViewModels. Features 2-5 are small, surgical changes to individual files.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Room, Google Calendar API, Google Sign-In

**Spec:** `docs/superpowers/specs/2026-03-18-availability-settings-calendar-polish-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt` | Modify | Group slots by day, add/remove slot UI |
| `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt` | Modify | Add/remove availability, filter task calendar, expose display name |
| `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt` | Modify | Pass new callbacks, reorder sections, show name, add sync helper text |
| `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt` | Modify | Fix updateAvailability to match by id, add/remove slots, filter calendar, change sync default |
| `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt` | Modify | Pass new callbacks to AvailabilityEditor |
| `app/src/main/java/com/tasktracker/data/calendar/GoogleCalendarApiClient.kt` | Modify | Rename constants, legacy name migration, implement renameCalendar |
| `app/src/main/java/com/tasktracker/domain/repository/CalendarRepository.kt` | Modify | Add renameCalendar method |
| `app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt` | Modify | Update description string |
| `app/src/main/java/com/tasktracker/data/calendar/GoogleAuthManager.kt` | Modify | Capture displayName |
| `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt` | Modify | Change sync default to 15 min |

---

## Task 1: AvailabilityEditor — Multi-Slot UI

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt`

- [ ] **Step 1: Update AvailabilityEditor signature and grouping logic**

Replace the entire `AvailabilityEditor` composable. The new version groups availabilities by `dayOfWeek`, accepts `onAdd` and `onRemove` callbacks, and renders multiple rows per day:

```kotlin
@Composable
fun AvailabilityEditor(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    onAdd: (UserAvailability) -> Unit = {},
    onRemove: (UserAvailability) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Availability", style = MaterialTheme.typography.titleMedium)
        Text(
            "Set your available hours for each day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        for (day in DayOfWeek.entries) {
            val daySlots = availabilities
                .filter { it.dayOfWeek == day }
                .sortedBy { it.startTime }
                .ifEmpty {
                    listOf(
                        UserAvailability(
                            dayOfWeek = day,
                            startTime = LocalTime.of(9, 0),
                            endTime = LocalTime.of(17, 0),
                            enabled = false,
                        )
                    )
                }

            AvailabilityDayGroup(
                day = day,
                slots = daySlots,
                onToggleDay = { enabled ->
                    daySlots.forEach { slot ->
                        if (slot.id == 0L) {
                            // Placeholder slot — add it to persist
                            onAdd(slot.copy(enabled = enabled))
                        } else {
                            onUpdate(slot.copy(enabled = enabled))
                        }
                    }
                },
                onUpdateSlot = onUpdate,
                onAddSlot = {
                    val lastSlot = daySlots.last()
                    val newStart = lastSlot.endTime.plusHours(1).let {
                        if (it.isAfter(LocalTime.of(22, 0))) LocalTime.of(22, 0) else it
                    }
                    val newEnd = newStart.plusHours(4).let {
                        if (it.isAfter(LocalTime.of(23, 0)) || it.isBefore(newStart)) LocalTime.of(23, 0) else it
                    }
                    // Check overlap with existing slots
                    val hasOverlap = daySlots.any { existing ->
                        newStart < existing.endTime && newEnd > existing.startTime
                    }
                    if (!hasOverlap && newEnd > newStart) {
                        onAdd(
                            UserAvailability(
                                dayOfWeek = day,
                                startTime = newStart,
                                endTime = newEnd,
                                enabled = true,
                            )
                        )
                    }
                },
                onRemoveSlot = onRemove,
                canAddSlot = daySlots.last().endTime.isBefore(LocalTime.of(22, 0)),
            )
        }
    }
}
```

- [ ] **Step 2: Add AvailabilityDayGroup composable**

Add this new composable below `AvailabilityEditor`. It renders a day with its toggle, all slots, and the add button:

```kotlin
@Composable
private fun AvailabilityDayGroup(
    day: DayOfWeek,
    slots: List<UserAvailability>,
    onToggleDay: (Boolean) -> Unit,
    onUpdateSlot: (UserAvailability) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (UserAvailability) -> Unit,
    canAddSlot: Boolean,
) {
    val dayEnabled = slots.any { it.enabled }
    val dayName = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // First slot row: toggle + day name + times
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = dayEnabled,
                onCheckedChange = { onToggleDay(it) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = dayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(48.dp),
            )
            if (dayEnabled && slots.isNotEmpty()) {
                TimeSlotRow(
                    slot = slots.first(),
                    onUpdate = onUpdateSlot,
                    showRemove = false,
                    onRemove = {},
                )
            }
        }

        // Additional slots: indented, with remove button
        if (dayEnabled) {
            for (slot in slots.drop(1)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 108.dp), // Align with time pickers
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeSlotRow(
                        slot = slot,
                        onUpdate = onUpdateSlot,
                        showRemove = true,
                        onRemove = { onRemoveSlot(slot) },
                    )
                }
            }

            // Add slot button
            if (canAddSlot) {
                TextButton(
                    onClick = onAddSlot,
                    modifier = Modifier.padding(start = 100.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add slot", color = SortdColors.accent)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Add TimeSlotRow composable**

Replace the old `AvailabilityRow` with this focused composable that handles just the time pickers and optional remove button:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotRow(
    slot: UserAvailability,
    onUpdate: (UserAvailability) -> Unit,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showStartPicker = true }) {
            Text(slot.startTime.toString())
        }
        Text(" - ")
        TextButton(onClick = { showEndPicker = true }) {
            Text(slot.endTime.toString())
        }
        if (showRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove slot",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(
            initialHour = slot.startTime.hour,
            initialMinute = slot.startTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(slot.copy(startTime = LocalTime.of(state.hour, state.minute), enabled = true))
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }

    if (showEndPicker) {
        val state = rememberTimePickerState(
            initialHour = slot.endTime.hour,
            initialMinute = slot.endTime.minute,
        )
        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(slot.copy(endTime = LocalTime.of(state.hour, state.minute), enabled = true))
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
```

- [ ] **Step 4: Update imports**

At the top of `AvailabilityEditor.kt`, ensure these imports are present:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import com.tasktracker.ui.theme.SortdColors
```

- [ ] **Step 5: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt
git commit -m "feat: multi-slot availability editor with add/remove support"
```

---

## Task 2: Wire Multi-Slot Availability into Settings & Onboarding

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt:55-59`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt:118-121`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt:96-114`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt:114-133`

- [ ] **Step 1: Add addAvailability and removeAvailability to SettingsViewModel**

In `SettingsViewModel.kt`, add these methods after `updateAvailability()` (line 59):

```kotlin
fun addAvailability(availability: UserAvailability) {
    viewModelScope.launch {
        availabilityRepository.insert(availability)
    }
}

fun removeAvailability(availability: UserAvailability) {
    viewModelScope.launch {
        availabilityRepository.delete(availability)
    }
}
```

- [ ] **Step 2: Update SettingsScreen to pass new callbacks**

In `SettingsScreen.kt`, find the `AvailabilityEditor` call (around line 118-121). Change it from:

```kotlin
AvailabilityEditor(
    availabilities = uiState.availabilities,
    onUpdate = viewModel::updateAvailability,
)
```

To:

```kotlin
AvailabilityEditor(
    availabilities = uiState.availabilities,
    onUpdate = viewModel::updateAvailability,
    onAdd = viewModel::addAvailability,
    onRemove = viewModel::removeAvailability,
)
```

- [ ] **Step 3: Fix OnboardingViewModel.updateAvailability() to match by id**

In `OnboardingViewModel.kt`, replace the `updateAvailability()` method (lines 96-104). The current implementation matches by `dayOfWeek` which breaks with multiple slots. Change to match by `id`:

```kotlin
fun updateAvailability(availability: UserAvailability) {
    _uiState.update { state ->
        state.copy(
            availabilities = state.availabilities.map {
                if (it.id == availability.id) availability else it
            },
        )
    }
}
```

- [ ] **Step 4: Add addAvailability and removeAvailability to OnboardingViewModel**

Add these methods after `updateAvailability()`:

```kotlin
fun addAvailability(availability: UserAvailability) {
    _uiState.update { state ->
        // Use negative IDs for in-memory slots to distinguish from persisted ones
        val newId = (state.availabilities.minOfOrNull { it.id } ?: 0L) - 1
        state.copy(
            availabilities = state.availabilities + availability.copy(id = newId),
        )
    }
}

fun removeAvailability(availability: UserAvailability) {
    _uiState.update { state ->
        state.copy(
            availabilities = state.availabilities.filter { it.id != availability.id },
        )
    }
}
```

- [ ] **Step 5: Update OnboardingScreen AvailabilityStep**

In `OnboardingScreen.kt`, find the `AvailabilityStep` composable (around line 114). Update its signature and the `AvailabilityEditor` call to include the new callbacks:

Change the function signature:

```kotlin
@Composable
private fun AvailabilityStep(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    onAdd: (UserAvailability) -> Unit,
    onRemove: (UserAvailability) -> Unit,
    onNext: () -> Unit,
)
```

Update the `AvailabilityEditor` call inside it:

```kotlin
AvailabilityEditor(
    availabilities = availabilities,
    onUpdate = onUpdate,
    onAdd = onAdd,
    onRemove = onRemove,
)
```

Then update the call site in the `when` block (around line 62) to pass the new callbacks:

```kotlin
OnboardingStep.AVAILABILITY -> AvailabilityStep(
    availabilities = uiState.availabilities,
    onUpdate = viewModel::updateAvailability,
    onAdd = viewModel::addAvailability,
    onRemove = viewModel::removeAvailability,
    onNext = viewModel::saveAvailabilityAndProceed,
)
```

- [ ] **Step 6: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt \
       app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt \
       app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt
git commit -m "feat: wire multi-slot availability into settings and onboarding"
```

---

## Task 3: Rename Task Calendar, Add Migration & Hide from UI

**Files:**
- Modify: `app/src/main/java/com/tasktracker/domain/repository/CalendarRepository.kt`
- Modify: `app/src/main/java/com/tasktracker/data/calendar/GoogleCalendarApiClient.kt`
- Modify: `app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add renameCalendar to CalendarRepository interface**

In `CalendarRepository.kt`, add this method after `getOrCreateTaskCalendar()`:

```kotlin
suspend fun renameCalendar(calendarId: String, newName: String)
```

- [ ] **Step 2: Update GoogleCalendarApiClient constants and implement renameCalendar**

In `GoogleCalendarApiClient.kt`, update the constants (lines 29-32):

```kotlin
companion object {
    private const val APP_NAME = "Sortd Task Tracker"
    private const val TASK_CALENDAR_NAME = "Sortd Task Tracker"
    private const val LEGACY_CALENDAR_NAME = "Task Tracker"
}
```

Add the `renameCalendar` implementation after `getOrCreateTaskCalendar()`:

```kotlin
override suspend fun renameCalendar(calendarId: String, newName: String) = withContext(Dispatchers.IO) {
    val calendar = com.google.api.services.calendar.model.Calendar()
    calendar.summary = newName
    service.calendars().patch(calendarId, calendar).execute()
}
```

- [ ] **Step 3: Update getOrCreateTaskCalendar() to handle legacy name migration**

In `GoogleCalendarApiClient.kt`, update the `getOrCreateTaskCalendar()` method. In the section where it searches the calendar list (around line 74), update the find logic to also search for the legacy name, and rename if found:

Change the calendar search from:

```kotlin
val existing = calendarList.items?.find { it.summary == TASK_CALENDAR_NAME }
```

To:

```kotlin
val existing = calendarList.items?.find {
    it.summary == TASK_CALENDAR_NAME || it.summary == LEGACY_CALENDAR_NAME
}
```

After finding an existing calendar with the legacy name, add a rename call before returning:

```kotlin
if (existing != null) {
    if (existing.summary == LEGACY_CALENDAR_NAME) {
        renameCalendar(existing.id, TASK_CALENDAR_NAME)
    }
    appPreferences.setTaskCalendarId(existing.id)
    return@withContext existing.id
}
```

Also update the new calendar creation description from `"Managed by Task Tracker app"` to `"Managed by Sortd"`.

- [ ] **Step 4: Update CalendarEventMapper description**

In `CalendarEventMapper.kt`, change line 21:

From: `"Scheduled by Task Tracker"`
To: `"Scheduled by Sortd"`

- [ ] **Step 5: Filter task calendar from OnboardingViewModel**

In `OnboardingViewModel.kt`, in the `loadCalendars()` method (around line 116), after fetching calendars, filter out the task calendar. Add this after `val calendars = calendarRepository.listCalendars()`:

```kotlin
val taskCalId = appPreferences.taskCalendarId.first()
val filtered = calendars.filter { cal ->
    cal.id != taskCalId &&
        cal.name != "Sortd Task Tracker" &&
        cal.name != "Task Tracker"
}
```

Then use `filtered` instead of `calendars` in the state update.

Add the required import: `import kotlinx.coroutines.flow.first`

- [ ] **Step 6: Filter task calendar from SettingsViewModel**

In `SettingsViewModel.kt`, add `appPreferences` to the constructor if not already there (it already is). Update the `combine` block to include `appPreferences.taskCalendarId` and filter the calendars list.

Change the combine from 5 flows to 6 flows. Add `appPreferences.taskCalendarId` as an additional flow. In the transform lambda, filter out the task calendar:

```kotlin
val uiState: StateFlow<SettingsUiState> = combine(
    authManager.signedInEmail,
    availabilityRepository.observeAll(),
    calendarSelectionRepository.observeAll(),
    appPreferences.syncInterval,
    appPreferences.themeMode,
    appPreferences.taskCalendarId,
) { values ->
    @Suppress("UNCHECKED_CAST")
    val email = values[0] as String?
    val availabilities = values[1] as List<UserAvailability>
    val allCalendars = values[2] as List<CalendarSelection>
    val syncInterval = values[3] as SyncInterval
    val themeMode = values[4] as String
    val taskCalId = values[5] as String?

    val calendars = allCalendars.filter { it.googleCalendarId != taskCalId }

    SettingsUiState(
        email = email,
        availabilities = availabilities,
        calendars = calendars,
        syncInterval = syncInterval,
        themeMode = themeMode,
    )
}
```

Note: With 6+ flows, `combine` uses the `Array<Flow<*>>` overload and returns an `Array<Any?>` in the lambda.

- [ ] **Step 7: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/repository/CalendarRepository.kt \
       app/src/main/java/com/tasktracker/data/calendar/GoogleCalendarApiClient.kt \
       app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt \
       app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt
git commit -m "feat: rename task calendar to Sortd, add migration, hide from UI"
```

---

## Task 4: Show User Display Name in Settings

**Depends on:** Task 3 (which refactors `SettingsViewModel.combine()` to the array-based overload). Task 4 adds another flow to this array.

**Files:**
- Modify: `app/src/main/java/com/tasktracker/data/calendar/GoogleAuthManager.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add displayName StateFlow to GoogleAuthManager**

In `GoogleAuthManager.kt`, add a new flow alongside `_signedInEmail` (line 25):

```kotlin
private val _signedInDisplayName = MutableStateFlow<String?>(null)
val signedInDisplayName: StateFlow<String?> = _signedInDisplayName.asStateFlow()
```

In the `init` block (around line 48-54), where it restores from `getLastSignedInAccount`, also capture the display name:

```kotlin
init {
    val account = GoogleSignIn.getLastSignedInAccount(context)
    _signedInEmail.value = account?.email
    _signedInDisplayName.value = account?.displayName
}
```

In `handleSignInResult()` (around line 58-64), also set the display name:

```kotlin
_signedInDisplayName.value = account.displayName
```

In `signOut()` (around line 66-69), also clear it:

```kotlin
_signedInDisplayName.value = null
```

- [ ] **Step 2: Add displayName to SettingsUiState and SettingsViewModel**

In `SettingsViewModel.kt`, add `displayName` to `SettingsUiState`:

```kotlin
data class SettingsUiState(
    val email: String? = null,
    val displayName: String? = null,
    // ... rest unchanged
)
```

In the `combine` block (already updated in Task 3 to use array form), add `authManager.signedInDisplayName` as an additional flow and include it in the state:

Add `authManager.signedInDisplayName` to the flows array, then in the lambda:

```kotlin
val displayName = values[6] as String?
```

And include `displayName = displayName` in the `SettingsUiState` constructor.

- [ ] **Step 3: Update SettingsScreen Account section**

In `SettingsScreen.kt`, find the Account section (around lines 94-116). Update the Column inside the Card that shows "Signed in as" + email to also show the display name:

```kotlin
Column {
    Text(
        "Signed in as",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (uiState.displayName != null) {
        Text(
            uiState.displayName,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Text(
        uiState.email ?: "Not signed in",
        style = MaterialTheme.typography.bodyMedium,
        color = if (uiState.displayName != null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/calendar/GoogleAuthManager.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt
git commit -m "feat: show user display name in settings account section"
```

---

## Task 5: Reorder Settings Sections

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Move Theme section to the bottom**

In `SettingsScreen.kt`, the current section order inside the `LazyColumn` or `Column` is:
1. Theme (lines ~58-92)
2. Account (lines ~94-116)
3. Availability (lines ~118-121)
4. Calendars (lines ~123-141)
5. Background Sync (lines ~143-174)

Cut the entire Theme section block and paste it after the Background Sync section, so the new order is:

1. Account
2. Availability
3. Calendars
4. Background Sync
5. Theme

This is a pure cut-and-paste of the composable block. No logic changes.

- [ ] **Step 2: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt
git commit -m "feat: move theme section to bottom of settings"
```

---

## Task 6: Default Sync to 15 Minutes & Helper Text

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Change onboarding default to 15 minutes**

In `OnboardingViewModel.kt`, in the `saveCalendarsAndFinish()` method (around line 164), change:

```kotlin
syncScheduler.schedule(SyncInterval.THIRTY_MINUTES)
```

To:

```kotlin
syncScheduler.schedule(SyncInterval.FIFTEEN_MINUTES)
```

- [ ] **Step 2: Change AppPreferences fallback**

In `AppPreferences.kt`, change the sync interval default (around line 39):

From: `val name = prefs[SYNC_INTERVAL] ?: SyncInterval.THIRTY_MINUTES.name`
To: `val name = prefs[SYNC_INTERVAL] ?: SyncInterval.FIFTEEN_MINUTES.name`

- [ ] **Step 3: Add helper text below sync dropdown**

In `SettingsScreen.kt`, after the `ExposedDropdownMenuBox` for Background Sync (around line 174), add:

```kotlin
Text(
    "More frequent syncs keep your schedule up to date but use more battery in the background.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 4.dp),
)
```

- [ ] **Step 4: Verify build**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all tests**

Run: `./gradlew :app:testDebugUnitTest --rerun 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (pre-existing SortdColorsTest failure is unrelated)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt \
       app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt
git commit -m "feat: default sync to 15 minutes with battery usage explanation"
```

---

## Manual Testing Checklist

After all tasks are complete, verify on device/emulator:

- [ ] **Multiple slots:** In Settings, add a second availability slot for a weekday (e.g., 9-12, 1-5). Verify both appear. Remove the second slot. Verify removal.
- [ ] **Onboarding slots:** Go through onboarding flow, add multiple slots on a day, complete setup. Verify all slots are persisted.
- [ ] **Calendar name:** Check Google Calendar — the app's calendar should be named "Sortd Task Tracker". Existing "Task Tracker" calendars should be renamed on first sync.
- [ ] **Calendar hidden:** The "Sortd Task Tracker" calendar should not appear in the calendar selection list in Settings or Onboarding.
- [ ] **User name:** Settings Account section shows display name above email.
- [ ] **Settings order:** Sections are: Account → Availability → Calendars → Background Sync → Theme.
- [ ] **Sync default:** New installs default to 15-minute sync. Helper text appears below the dropdown.
