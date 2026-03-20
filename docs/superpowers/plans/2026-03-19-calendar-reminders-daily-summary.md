# Calendar Reminders & Daily Summary Notification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 10-minute popup reminders to Google Calendar events and a configurable daily morning notification summarizing scheduled tasks.

**Architecture:** The calendar reminder is a one-line addition to the event mapper. The daily summary uses a new WorkManager `PeriodicWorkRequest` (24h interval) with a `DailySummaryWorker` that queries today's scheduled blocks and shows a notification via `NotificationHelper`. A new settings sub-page controls the time and toggle.

**Tech Stack:** Kotlin, Google Calendar API, WorkManager, NotificationCompat, Jetpack Compose, Hilt, DataStore.

---

### Task 1: Add 10-Minute Reminder to Google Calendar Events

**Files:**
- Modify: `app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt`

- [ ] **Step 1: Add reminder override to `toGoogleEvent()`**

In `CalendarEventMapper.kt`, add the reminder to the event builder chain. Import `EventReminder` from `com.google.api.services.calendar.model.EventReminder`.

Change the `toGoogleEvent()` method — after `.setEnd(...)`, add:

```kotlin
.setReminders(
    Event.Reminders()
        .setUseDefault(false)
        .setOverrides(listOf(EventReminder().setMethod("popup").setMinutes(10)))
)
```

The full method becomes:
```kotlin
fun toGoogleEvent(
    block: ScheduledBlock,
    taskTitle: String,
    completed: Boolean = false,
): Event {
    val title = if (completed) "Completed: $taskTitle" else taskTitle
    return Event()
        .setSummary(title)
        .setDescription("Scheduled by Sortd")
        .setStart(
            EventDateTime()
                .setDateTime(DateTime(block.startTime.toEpochMilli()))
                .setTimeZone("UTC")
        )
        .setEnd(
            EventDateTime()
                .setDateTime(DateTime(block.endTime.toEpochMilli()))
                .setTimeZone("UTC")
        )
        .setReminders(
            Event.Reminders()
                .setUseDefault(false)
                .setOverrides(listOf(EventReminder().setMethod("popup").setMinutes(10)))
        )
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt
git commit -m "feat: add 10-minute popup reminder to Google Calendar events"
```

---

### Task 2: Add Daily Summary Preferences to AppPreferences

**Files:**
- Modify: `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Add preference keys and flows**

In `AppPreferences.kt`, add to the companion object:

```kotlin
private val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
private val DAILY_SUMMARY_TIME = stringPreferencesKey("daily_summary_time")
```

Add the flows and setters after the existing `themeMode` entries:

```kotlin
val dailySummaryEnabled: Flow<Boolean> = context.dataStore.data
    .map { it[DAILY_SUMMARY_ENABLED] ?: true }

suspend fun setDailySummaryEnabled(enabled: Boolean) {
    context.dataStore.edit { it[DAILY_SUMMARY_ENABLED] = enabled }
}

val dailySummaryTime: Flow<String> = context.dataStore.data
    .map { it[DAILY_SUMMARY_TIME] ?: "08:00" }

suspend fun setDailySummaryTime(time: String) {
    context.dataStore.edit { it[DAILY_SUMMARY_TIME] = time }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt
git commit -m "feat: add daily summary enabled/time preferences"
```

---

### Task 3: Add Daily Summary Notification Channel and Method to NotificationHelper

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/notification/NotificationHelper.kt`

- [ ] **Step 1: Add channel constant and notification ID**

In the companion object, add:

```kotlin
const val CHANNEL_DAILY_SUMMARY = "daily_summary"
const val NOTIFICATION_ID_DAILY_SUMMARY = 1003
```

- [ ] **Step 2: Create the channel in `createChannels()`**

After the existing `deadlineChannel` creation, add:

```kotlin
val dailySummaryChannel = NotificationChannel(
    CHANNEL_DAILY_SUMMARY,
    "Daily Summary",
    NotificationManager.IMPORTANCE_HIGH,
).apply {
    description = "Morning notification with your scheduled tasks for the day"
}
manager.createNotificationChannel(dailySummaryChannel)
```

- [ ] **Step 3: Add `showDailySummary(taskCount: Int)` method**

```kotlin
fun showDailySummary(taskCount: Int) {
    if (!hasPermission()) return

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val body = if (taskCount == 1) {
        "You have 1 task lined up. Let's get it done!"
    } else {
        "You have $taskCount tasks lined up. Let's get them done!"
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
        .setSmallIcon(android.R.drawable.ic_menu_agenda)
        .setContentTitle("Ready to crush it today?")
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context)
        .notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/notification/NotificationHelper.kt
git commit -m "feat: add daily summary notification channel and method"
```

---

### Task 4: Create DailySummaryWorker

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/sync/DailySummaryWorker.kt`

- [ ] **Step 1: Create the worker**

```kotlin
package com.tasktracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.repository.ScheduledBlockRepository
import com.tasktracker.ui.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val blockRepository: ScheduledBlockRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = appPreferences.dailySummaryEnabled.first()
        if (!enabled) return Result.success()

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()

        val blocks = blockRepository.getByStatuses(
            listOf(BlockStatus.CONFIRMED)
        ).filter { it.startTime >= dayStart && it.startTime < dayEnd }

        if (blocks.isEmpty()) return Result.success()

        val taskCount = blocks.map { it.taskId }.distinct().size
        notificationHelper.showDailySummary(taskCount)

        return Result.success()
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/DailySummaryWorker.kt
git commit -m "feat: create DailySummaryWorker for morning task notifications"
```

---

### Task 5: Create DailySummaryScheduler

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/sync/DailySummaryScheduler.kt`

- [ ] **Step 1: Create the scheduler**

```kotlin
package com.tasktracker.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WORK_NAME = "daily_summary"
    }

    fun schedule(time: LocalTime) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var target = now.toLocalDate().atTime(time).atZone(ZoneId.systemDefault())
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        val initialDelay = Duration.between(now, target)

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            24, TimeUnit.HOURS,
        )
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/DailySummaryScheduler.kt
git commit -m "feat: create DailySummaryScheduler for daily WorkManager scheduling"
```

---

### Task 6: Schedule Daily Summary on App Start

**Files:**
- Modify: `app/src/main/java/com/tasktracker/TaskTrackerApplication.kt`

- [ ] **Step 1: Inject DailySummaryScheduler and AppPreferences, schedule on create**

Add injections:

```kotlin
@Inject
lateinit var dailySummaryScheduler: DailySummaryScheduler

@Inject
lateinit var appPreferences: AppPreferences
```

Add imports: `com.tasktracker.data.sync.DailySummaryScheduler`, `com.tasktracker.data.preferences.AppPreferences`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.runBlocking`, `java.time.LocalTime`.

In `onCreate()`, after `notificationHelper.createChannels()`, add:

```kotlin
runBlocking {
    val enabled = appPreferences.dailySummaryEnabled.first()
    if (enabled) {
        val timeStr = appPreferences.dailySummaryTime.first()
        val time = LocalTime.parse(timeStr)
        dailySummaryScheduler.schedule(time)
    } else {
        dailySummaryScheduler.cancel()
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/TaskTrackerApplication.kt
git commit -m "feat: schedule daily summary worker on app start"
```

---

### Task 7: Wire Daily Summary Settings into SettingsViewModel

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add daily summary state and methods**

Add `DailySummaryScheduler` to the constructor:

```kotlin
private val dailySummaryScheduler: DailySummaryScheduler,
```

Add import: `com.tasktracker.data.sync.DailySummaryScheduler`, `java.time.LocalTime`.

Add two new fields to `SettingsUiState`:

```kotlin
val dailySummaryEnabled: Boolean = true,
val dailySummaryTime: String = "08:00",
```

Add a computed property:

```kotlin
val dailySummarySubtitle: String
    get() = if (dailySummaryEnabled) dailySummaryTime else "Off"
```

Add `dailySummaryEnabled` and `dailySummaryTime` to the `combine` flow that builds `uiState` — add `appPreferences.dailySummaryEnabled` and `appPreferences.dailySummaryTime` to the combine inputs and map them into `SettingsUiState`.

Add methods:

```kotlin
fun setDailySummaryEnabled(enabled: Boolean) {
    viewModelScope.launch {
        appPreferences.setDailySummaryEnabled(enabled)
        if (enabled) {
            val timeStr = appPreferences.dailySummaryTime.first()
            dailySummaryScheduler.schedule(LocalTime.parse(timeStr))
        } else {
            dailySummaryScheduler.cancel()
        }
    }
}

fun setDailySummaryTime(time: String) {
    viewModelScope.launch {
        appPreferences.setDailySummaryTime(time)
        dailySummaryScheduler.schedule(LocalTime.parse(time))
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt
git commit -m "feat: add daily summary state and methods to SettingsViewModel"
```

---

### Task 8: Create DailySummaryScreen and Wire Navigation

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/settings/DailySummaryScreen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create `DailySummaryScreen.kt`**

```kotlin
package com.tasktracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Morning notification",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Get a summary of your tasks each morning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.dailySummaryEnabled,
                    onCheckedChange = { viewModel.setDailySummaryEnabled(it) },
                )
            }

            if (uiState.dailySummaryEnabled) {
                var showTimePicker by remember { mutableStateOf(false) }
                val time = LocalTime.parse(uiState.dailySummaryTime)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Notification time",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(uiState.dailySummaryTime)
                    }
                }

                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = time.hour,
                        initialMinute = time.minute,
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val newTime = String.format(
                                    "%02d:%02d",
                                    timePickerState.hour,
                                    timePickerState.minute,
                                )
                                viewModel.setDailySummaryTime(newTime)
                                showTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        },
                        text = { TimePicker(state = timePickerState) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add route to `Screen.kt`**

```kotlin
data object SettingsDailySummary : Screen("settings/daily_summary")
```

- [ ] **Step 3: Add composable destination to `NavGraph.kt`**

After the existing settings sub-page destinations, add:

```kotlin
composable(Screen.SettingsDailySummary.route) {
    DailySummaryScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}
```

Add import: `import com.tasktracker.ui.settings.DailySummaryScreen`

- [ ] **Step 4: Add Daily Summary row to Settings hub**

In `SettingsScreen.kt`, add a new `SettingsRow` after the Theme row. Use:
- Icon: `Icons.Outlined.Notifications`
- Icon color: `Color(0xFFEC4899)` (pink)
- Tint background: `Color(0xFFEC4899).copy(alpha = 0.15f)`
- Title: "Daily Summary"
- Subtitle: `uiState.dailySummarySubtitle`
- onClick: `onNavigateToDailySummary`

Add `onNavigateToDailySummary: () -> Unit` to the `SettingsScreen` signature.

Add `import androidx.compose.material.icons.outlined.Notifications`.

Update the NavGraph's `SettingsScreen` composable to pass the new callback:

```kotlin
onNavigateToDailySummary = { navController.navigate(Screen.SettingsDailySummary.route) },
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/DailySummaryScreen.kt \
      app/src/main/java/com/tasktracker/ui/navigation/Screen.kt \
      app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt \
      app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt
git commit -m "feat: add Daily Summary settings screen and navigation"
```

---

### Task 9: Run Tests and Final Verification

**Files:** None (verification only)

- [ ] **Step 1: Run existing unit tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 2: Build release**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit any test fixes**

If needed:
```bash
git add -A
git commit -m "fix: update tests for calendar reminders and daily summary"
```
