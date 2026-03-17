# Plan 4: Background Sync, Offline Support & Notifications

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement periodic background calendar sync via WorkManager, offline queue processing on connectivity restore, stale data warnings, and Android system notifications for reschedule proposals and deadline alerts.

**Architecture:** A `CalendarSyncWorker` (WorkManager `CoroutineWorker`) runs periodically to fetch fresh free/busy data, detect external changes, process pending sync operations, and trigger rescheduling if conflicts are found. A `ConnectivityObserver` monitors network state. A `NotificationHelper` manages notification channels and posting system notifications. A `SyncScheduler` configures WorkManager with user-chosen intervals.

**Tech Stack:** WorkManager, ConnectivityManager, Android NotificationManager, Hilt Worker injection, DataStore Preferences

**Spec:** `docs/superpowers/specs/2026-03-16-smart-task-scheduler-design.md`
**Depends on:** Plan 1 (data layer), Plan 2 (calendar sync manager), Plan 3 (UI navigation for deep links)

---

## File Structure

```
app/src/main/java/com/tasktracker/
├── data/
│   ├── sync/
│   │   ├── CalendarSyncWorker.kt              # WorkManager worker for background sync
│   │   ├── SyncScheduler.kt                   # Configures WorkManager periodic requests
│   │   └── ExternalChangeDetector.kt          # Detects external edits to Task Tracker calendar
│   ├── connectivity/
│   │   └── ConnectivityObserver.kt            # Monitors network state via ConnectivityManager
│   └── preferences/
│       └── AppPreferences.kt                  # DataStore for sync interval, calendar ID, stale timestamp
├── domain/
│   └── model/
│       └── SyncInterval.kt                    # Enum: 15min, 30min, 1hr, manual
└── ui/
    └── notification/
        └── NotificationHelper.kt              # Creates channels, posts notifications
app/src/main/res/
└── values/
    └── strings.xml                            # Notification strings
app/src/test/java/com/tasktracker/
└── data/
    ├── sync/
    │   └── ExternalChangeDetectorTest.kt
    └── connectivity/
        └── ConnectivityObserverTest.kt        # (mock-based, optional)
```

---

## Task 1: SyncInterval Enum and App Preferences

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/SyncInterval.kt`
- Create: `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Create SyncInterval enum**

```kotlin
package com.tasktracker.domain.model

enum class SyncInterval(val minutes: Long, val label: String) {
    FIFTEEN_MINUTES(15, "15 minutes"),
    THIRTY_MINUTES(30, "30 minutes"),
    ONE_HOUR(60, "1 hour"),
    MANUAL(0, "Manual only");
}
```

- [ ] **Step 2: Create AppPreferences using DataStore**

```kotlin
package com.tasktracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tasktracker.domain.model.SyncInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val TASK_CALENDAR_ID = stringPreferencesKey("task_calendar_id")
        private val SYNC_INTERVAL = stringPreferencesKey("sync_interval")
        private val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private const val STALE_THRESHOLD_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    }

    val taskCalendarId: Flow<String?> = context.dataStore.data
        .map { it[TASK_CALENDAR_ID] }

    suspend fun setTaskCalendarId(id: String) {
        context.dataStore.edit { it[TASK_CALENDAR_ID] = id }
    }

    val syncInterval: Flow<SyncInterval> = context.dataStore.data
        .map { prefs ->
            val name = prefs[SYNC_INTERVAL] ?: SyncInterval.THIRTY_MINUTES.name
            SyncInterval.valueOf(name)
        }

    suspend fun setSyncInterval(interval: SyncInterval) {
        context.dataStore.edit { it[SYNC_INTERVAL] = interval.name }
    }

    val lastSyncTimestamp: Flow<Instant?> = context.dataStore.data
        .map { prefs ->
            prefs[LAST_SYNC_TIMESTAMP]?.let { Instant.ofEpochMilli(it) }
        }

    suspend fun setLastSyncTimestamp(timestamp: Instant) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP] = timestamp.toEpochMilli() }
    }

    val isFreeBusyDataStale: Flow<Boolean> = context.dataStore.data
        .map { prefs ->
            val lastSync = prefs[LAST_SYNC_TIMESTAMP] ?: return@map true
            (System.currentTimeMillis() - lastSync) > STALE_THRESHOLD_MILLIS
        }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/SyncInterval.kt app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt
git commit -m "feat: add sync interval enum and DataStore preferences"
```

---

## Task 2: Connectivity Observer

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/connectivity/ConnectivityObserver.kt`

- [ ] **Step 1: Create ConnectivityObserver**

```kotlin
package com.tasktracker.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected: Boolean
        get() {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(isConnected)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/connectivity/ConnectivityObserver.kt
git commit -m "feat: add connectivity observer using ConnectivityManager"
```

---

## Task 3: Notification Helper

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/notification/NotificationHelper.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add notification permission to AndroidManifest.xml**

Add inside `<manifest>`:
```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Create NotificationHelper**

```kotlin
package com.tasktracker.ui.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasktracker.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_RESCHEDULE = "reschedule_proposals"
        const val CHANNEL_DEADLINE = "deadline_alerts"
        const val NOTIFICATION_ID_RESCHEDULE = 1001
        const val NOTIFICATION_ID_DEADLINE = 1002
    }

    fun createChannels() {
        val rescheduleChannel = NotificationChannel(
            CHANNEL_RESCHEDULE,
            "Reschedule Proposals",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications when tasks need to be rescheduled"
        }

        val deadlineChannel = NotificationChannel(
            CHANNEL_DEADLINE,
            "Deadline Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications when a task can't be scheduled before its deadline"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(rescheduleChannel)
        manager.createNotificationChannel(deadlineChannel)
    }

    fun showRescheduleProposal(taskCount: Int) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reschedule")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RESCHEDULE)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Schedule Conflict Detected")
            .setContentText("$taskCount task(s) need to be rescheduled. Tap to review.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_RESCHEDULE, notification)
    }

    fun showDeadlineAtRisk(taskTitle: String, taskId: Long = 0) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Deadline at Risk")
            .setContentText("\"$taskTitle\" can't be scheduled before its deadline.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_DEADLINE + taskId.toInt(), notification)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
```

- [ ] **Step 3: Initialize notification channels in TaskTrackerApplication**

Modify `app/src/main/java/com/tasktracker/TaskTrackerApplication.kt`:

```kotlin
package com.tasktracker

import android.app.Application
import com.tasktracker.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TaskTrackerApplication : Application() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }
}
```

- [ ] **Step 4: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/notification/NotificationHelper.kt app/src/main/java/com/tasktracker/TaskTrackerApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add notification helper with reschedule and deadline channels"
```

---

## Task 4: External Change Detector

**Files:**
- Create: `app/src/test/java/com/tasktracker/data/sync/ExternalChangeDetectorTest.kt`
- Create: `app/src/main/java/com/tasktracker/data/sync/ExternalChangeDetector.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.tasktracker.data.sync

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ExternalChangeDetectorTest {

    private val detector = ExternalChangeDetector()
    private val now = Instant.parse("2026-03-16T14:00:00Z")

    @Test
    fun `detects externally deleted event`() {
        val blocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 10,
                startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
                googleCalendarEventId = "event1",
                status = BlockStatus.CONFIRMED,
            ),
        )
        val calendarEvents = emptyList<CalendarEvent>() // event1 missing from calendar

        val changes = detector.detectChanges(blocks, calendarEvents)
        assertThat(changes).hasSize(1)
        assertThat(changes[0]).isInstanceOf(ExternalChange.Deleted::class.java)
        assertThat((changes[0] as ExternalChange.Deleted).block.id).isEqualTo(1)
    }

    @Test
    fun `detects externally moved event`() {
        val blocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 10,
                startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
                googleCalendarEventId = "event1",
                status = BlockStatus.CONFIRMED,
            ),
        )
        val newStart = now.plus(2, ChronoUnit.HOURS)
        val calendarEvents = listOf(
            CalendarEvent(
                id = "event1", calendarId = "task-tracker",
                title = "Test", startTime = newStart,
                endTime = newStart.plus(60, ChronoUnit.MINUTES),
            ),
        )

        val changes = detector.detectChanges(blocks, calendarEvents)
        assertThat(changes).hasSize(1)
        assertThat(changes[0]).isInstanceOf(ExternalChange.Moved::class.java)
    }

    @Test
    fun `no changes when events match`() {
        val blocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 10,
                startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
                googleCalendarEventId = "event1",
                status = BlockStatus.CONFIRMED,
            ),
        )
        val calendarEvents = listOf(
            CalendarEvent(
                id = "event1", calendarId = "task-tracker",
                title = "Test", startTime = now,
                endTime = now.plus(60, ChronoUnit.MINUTES),
            ),
        )

        val changes = detector.detectChanges(blocks, calendarEvents)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `ignores blocks without calendar event ID`() {
        val blocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 10,
                startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
                googleCalendarEventId = null,
                status = BlockStatus.CONFIRMED,
            ),
        )

        val changes = detector.detectChanges(blocks, emptyList())
        assertThat(changes).isEmpty()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.data.sync.ExternalChangeDetectorTest"`
Expected: FAIL

- [ ] **Step 3: Implement ExternalChangeDetector**

```kotlin
package com.tasktracker.data.sync

import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import java.time.Instant
import javax.inject.Inject

sealed class ExternalChange {
    data class Deleted(val block: ScheduledBlock) : ExternalChange()
    data class Moved(
        val block: ScheduledBlock,
        val newStart: Instant,
        val newEnd: Instant,
    ) : ExternalChange()
}

class ExternalChangeDetector @Inject constructor() {

    fun detectChanges(
        localBlocks: List<ScheduledBlock>,
        calendarEvents: List<CalendarEvent>,
    ): List<ExternalChange> {
        val eventById = calendarEvents.associateBy { it.id }
        val changes = mutableListOf<ExternalChange>()

        for (block in localBlocks) {
            val eventId = block.googleCalendarEventId ?: continue
            val event = eventById[eventId]

            if (event == null) {
                changes.add(ExternalChange.Deleted(block))
            } else if (event.startTime != block.startTime || event.endTime != block.endTime) {
                changes.add(
                    ExternalChange.Moved(
                        block = block,
                        newStart = event.startTime,
                        newEnd = event.endTime,
                    )
                )
            }
        }

        return changes
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.data.sync.ExternalChangeDetectorTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/ExternalChangeDetector.kt app/src/test/java/com/tasktracker/data/sync/ExternalChangeDetectorTest.kt
git commit -m "feat: add external change detector for Task Tracker calendar events"
```

---

## Task 5: Calendar Sync Worker

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/sync/CalendarSyncWorker.kt`

- [ ] **Step 1: Create CalendarSyncWorker**

```kotlin
package com.tasktracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import com.tasktracker.domain.scheduler.TaskScheduler
import com.tasktracker.ui.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarRepository: CalendarRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val availabilityRepository: UserAvailabilityRepository,
    private val syncManager: CalendarSyncManager,
    private val externalChangeDetector: ExternalChangeDetector,
    private val taskScheduler: TaskScheduler,
    private val notificationHelper: NotificationHelper,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Process any pending offline operations
            syncManager.processPendingOperations()

            // 2. Get task calendar ID
            val taskCalendarId = appPreferences.taskCalendarId.first()
                ?: return Result.success() // Not set up yet

            // 3. Verify Task Tracker calendar still exists; recreate if deleted
            val resolvedCalendarId = try {
                calendarRepository.getOrCreateTaskCalendar()
            } catch (e: Exception) {
                return Result.retry()
            }
            if (resolvedCalendarId != taskCalendarId) {
                // Calendar was recreated — update stored ID and re-push all blocks
                appPreferences.setTaskCalendarId(resolvedCalendarId)
                val allConfirmed = blockRepository.getByStatuses(listOf(BlockStatus.CONFIRMED))
                for (block in allConfirmed) {
                    syncManager.pushNewBlock(block)
                }
            }

            // 4. Detect external changes to Task Tracker calendar events
            val confirmedBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED)
            )
            val now = Instant.now()
            val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
            val taskCalendarEvents = calendarRepository.getEvents(
                resolvedCalendarId, now, twoWeeksLater
            )
            val externalChanges = externalChangeDetector.detectChanges(
                confirmedBlocks, taskCalendarEvents
            )

            var needsReschedule = false

            for (change in externalChanges) {
                when (change) {
                    is ExternalChange.Deleted -> {
                        blockRepository.updateStatus(change.block.id, BlockStatus.CANCELLED)
                        taskRepository.updateStatus(change.block.taskId, TaskStatus.PENDING)
                        needsReschedule = true
                    }
                    is ExternalChange.Moved -> {
                        blockRepository.update(
                            change.block.copy(
                                startTime = change.newStart,
                                endTime = change.newEnd,
                            )
                        )
                    }
                }
            }

            // 4. Fetch fresh free/busy data
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val busySlots = calendarRepository.getFreeBusySlots(
                calendarIds = enabledCalendars.map { it.googleCalendarId },
                timeMin = now,
                timeMax = twoWeeksLater,
            )

            // 5. Check for conflicts between confirmed blocks and busy slots
            val currentBlocks = blockRepository.getByStatuses(listOf(BlockStatus.CONFIRMED))
            for (block in currentBlocks) {
                val hasConflict = busySlots.any { busy ->
                    block.startTime < busy.endTime && block.endTime > busy.startTime
                }
                if (hasConflict) {
                    needsReschedule = true
                    break
                }
            }

            // 6. Re-run scheduler if needed
            if (needsReschedule) {
                val pendingTasks = taskRepository.getByStatuses(
                    listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
                )
                val availability = availabilityRepository.getEnabled()
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val existingBlocks = blockRepository.getByStatuses(
                    listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
                )

                val result = taskScheduler.schedule(
                    tasks = pendingTasks,
                    existingBlocks = existingBlocks,
                    availability = availability,
                    busySlots = busySlots,
                    startDate = today,
                    endDate = today.plusDays(14),
                    zoneId = zoneId,
                )

                when (result) {
                    is SchedulingResult.Scheduled -> {
                        // Auto-apply and sync
                        for (block in result.blocks) {
                            val id = blockRepository.insert(block)
                            taskRepository.updateStatus(block.taskId, TaskStatus.SCHEDULED)
                            syncManager.pushNewBlock(block.copy(id = id))
                        }
                    }
                    is SchedulingResult.NeedsReschedule -> {
                        // Save as proposed, notify user
                        blockRepository.insertAll(
                            (result.newBlocks + result.movedBlocks.map { it.second })
                                .map { it.copy(status = BlockStatus.PROPOSED) }
                        )
                        notificationHelper.showRescheduleProposal(
                            result.newBlocks.size + result.movedBlocks.size
                        )
                    }
                    is SchedulingResult.DeadlineAtRisk -> {
                        notificationHelper.showDeadlineAtRisk(result.task.title, result.task.id)
                    }
                    is SchedulingResult.NoSlotsAvailable -> {
                        // Silent — user will see status in app
                    }
                }
            }

            // 7. Update last sync timestamp
            appPreferences.setLastSyncTimestamp(Instant.now())

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/CalendarSyncWorker.kt
git commit -m "feat: add background calendar sync worker with conflict detection"
```

---

## Task 6: Sync Scheduler (WorkManager Configuration)

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/sync/SyncScheduler.kt`

- [ ] **Step 1: Create SyncScheduler**

```kotlin
package com.tasktracker.data.sync

import android.content.Context
import androidx.work.*
import com.tasktracker.domain.model.SyncInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val SYNC_WORK_NAME = "calendar_sync"
    }

    fun schedule(interval: SyncInterval) {
        val workManager = WorkManager.getInstance(context)

        if (interval == SyncInterval.MANUAL) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            interval.minutes, TimeUnit.MINUTES,
            // Flex interval: allow WorkManager to schedule within the last third of the period
            interval.minutes / 3, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/sync/SyncScheduler.kt
git commit -m "feat: add sync scheduler for configurable WorkManager periodic sync"
```

---

## Task 7: Hilt WorkManager Integration

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/tasktracker/di/WorkerModule.kt`

- [ ] **Step 1: Add Hilt WorkManager dependency**

Add to `app/build.gradle.kts` dependencies:
```kotlin
    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
```

- [ ] **Step 2: Create WorkerModule**

No additional Hilt module is needed for this task. `ExternalChangeDetector` has an `@Inject constructor()` and Hilt can construct it automatically. The `CalendarSyncWorker` uses `@AssistedInject` with `@HiltWorker`, which handles its own injection.

- [ ] **Step 3: Initialize WorkManager with HiltWorkerFactory in Application**

Update `app/src/main/java/com/tasktracker/TaskTrackerApplication.kt`:

```kotlin
package com.tasktracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tasktracker.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TaskTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

Also add to `AndroidManifest.xml` inside `<application>` to disable default WorkManager initialization:
```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

Add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` tag if not present.

- [ ] **Step 4: Verify project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/tasktracker/di/WorkerModule.kt app/src/main/java/com/tasktracker/TaskTrackerApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: configure Hilt WorkManager integration for background sync"
```

---

## Task 8: Start Background Sync After Onboarding

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/MainActivity.kt`

- [ ] **Step 1: Start sync and mark onboarding complete in OnboardingViewModel**

Add these injections and update `saveCalendarsAndFinish()` in `OnboardingViewModel.kt`:

Add to constructor:
```kotlin
    private val syncScheduler: SyncScheduler,
    private val appPreferences: AppPreferences,
```

Add import:
```kotlin
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.SyncInterval
```

Update `saveCalendarsAndFinish()`:
```kotlin
    fun saveCalendarsAndFinish() {
        viewModelScope.launch {
            for (cal in _uiState.value.calendars) {
                calendarSelectionRepository.insert(
                    CalendarSelection(
                        googleCalendarId = cal.id,
                        calendarName = cal.name,
                        calendarColor = cal.color,
                        enabled = cal.enabled,
                    )
                )
            }
            try {
                val calendarId = calendarRepository.getOrCreateTaskCalendar()
                appPreferences.setTaskCalendarId(calendarId)
            } catch (_: Exception) { /* Will retry on first sync */ }
            appPreferences.setOnboardingCompleted(true)
            syncScheduler.schedule(SyncInterval.THIRTY_MINUTES)
            _uiState.update { it.copy(step = OnboardingStep.DONE) }
        }
    }
```

- [ ] **Step 2: Check onboarding status in MainActivity to set start destination**

Update `app/src/main/java/com/tasktracker/MainActivity.kt`:

```kotlin
package com.tasktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tasktracker.data.connectivity.ConnectivityObserver
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.ui.navigation.Screen
import com.tasktracker.ui.navigation.TaskTrackerNavGraph
import com.tasktracker.ui.theme.TaskTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Trigger sync on app open (only if onboarding complete)
        lifecycleScope.launch {
            if (appPreferences.onboardingCompleted.first()) {
                syncScheduler.syncNow()
            }
        }

        // Sync on connectivity restored
        lifecycleScope.launch {
            connectivityObserver.observe()
                .distinctUntilChanged()
                .filter { isConnected -> isConnected }
                .collect {
                    if (appPreferences.onboardingCompleted.first()) {
                        syncScheduler.syncNow()
                    }
                }
        }

        // Read deep link intent
        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            TaskTrackerTheme {
                val navController = rememberNavController()
                val onboardingCompleted by appPreferences.onboardingCompleted
                    .collectAsState(initial = null)

                // Handle deep link navigation from notifications
                LaunchedEffect(onboardingCompleted, navigateTo) {
                    if (onboardingCompleted == true && navigateTo == "reschedule") {
                        navController.navigate(Screen.Reschedule.route)
                    }
                }

                when (onboardingCompleted) {
                    null -> { /* Loading */ }
                    true -> TaskTrackerNavGraph(
                        navController = navController,
                        startDestination = Screen.TaskList.route,
                    )
                    false -> TaskTrackerNavGraph(
                        navController = navController,
                        startDestination = Screen.Onboarding.route,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt app/src/main/java/com/tasktracker/MainActivity.kt
git commit -m "feat: start background sync after onboarding, check status on launch"
```

---

## Task 9: Sync Interval Setting in Settings Screen

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add sync interval to SettingsViewModel**

Add to constructor:
```kotlin
    private val appPreferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
```

Add imports:
```kotlin
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.domain.model.SyncInterval
```

Update `SettingsUiState`:
```kotlin
data class SettingsUiState(
    val email: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
    val syncInterval: SyncInterval = SyncInterval.THIRTY_MINUTES,
)
```

Update the `uiState` flow to include sync interval:
```kotlin
    val uiState: StateFlow<SettingsUiState> = combine(
        authManager.signedInEmail,
        availabilityRepository.observeAll(),
        calendarSelectionRepository.observeAll(),
        appPreferences.syncInterval,
    ) { email, availabilities, calendars, interval ->
        SettingsUiState(
            email = email,
            availabilities = availabilities,
            calendars = calendars,
            syncInterval = interval,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )
```

Add method:
```kotlin
    fun updateSyncInterval(interval: SyncInterval) {
        viewModelScope.launch {
            appPreferences.setSyncInterval(interval)
            syncScheduler.schedule(interval)
        }
    }
```

- [ ] **Step 2: Add sync interval picker to SettingsScreen**

Add this section after the calendar selection section in `SettingsScreen.kt`:

```kotlin
            // Sync interval section
            Text("Background Sync", style = MaterialTheme.typography.titleMedium)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = uiState.syncInterval.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sync Interval") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    SyncInterval.entries.forEach { interval ->
                        DropdownMenuItem(
                            text = { Text(interval.label) },
                            onClick = {
                                viewModel.updateSyncInterval(interval)
                                expanded = false
                            },
                        )
                    }
                }
            }
```

Add imports:
```kotlin
import com.tasktracker.domain.model.SyncInterval
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/
git commit -m "feat: add sync interval configuration to settings screen"
```

---

## Task 10: Stale Data Warning in Task Edit

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt`

- [ ] **Step 1: Add stale data check to TaskEditViewModel**

Add to constructor:
```kotlin
    private val appPreferences: AppPreferences,
```

Add import:
```kotlin
import com.tasktracker.data.preferences.AppPreferences
```

Add to `TaskEditUiState`:
```kotlin
    val staleDataWarning: Boolean = false,
```

Add in the `init` block:
```kotlin
        viewModelScope.launch {
            appPreferences.isFreeBusyDataStale.collect { isStale ->
                _uiState.update { it.copy(staleDataWarning = isStale) }
            }
        }
```

- [ ] **Step 2: Show stale data banner in TaskEditScreen**

Add this right after the `Spacer(Modifier.height(8.dp))` at the top of the form column:

```kotlin
            // Stale data warning
            if (uiState.staleDataWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "Calendar data may be outdated. Schedule might conflict with recent calendar changes.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/
git commit -m "feat: add stale calendar data warning banner in task edit screen"
```

---

## Summary

After completing all 10 tasks, you will have:

- **Sync interval configuration** with DataStore preferences (15 min, 30 min, 1 hour, manual)
- **Connectivity observer** using ConnectivityManager with Flow-based reactive updates
- **Notification system** with two channels (reschedule proposals, deadline alerts) and system notification posting
- **External change detector** that identifies when Task Tracker calendar events were deleted or moved outside the app
- **Background sync worker** (WorkManager `CoroutineWorker`) that processes pending offline operations, detects external changes, fetches fresh free/busy data, checks for conflicts, and triggers rescheduling
- **Sync scheduler** that configures WorkManager periodic requests with proper flex windows, Doze mode compliance, and network constraints
- **Hilt WorkManager integration** with custom worker factory
- **Automatic sync triggers** on app open and after onboarding
- **Stale data warning** when free/busy data is older than 2 hours
- **Settings UI** for configuring sync interval

This completes the full Smart Task Scheduler v1 implementation across all 4 plans.
