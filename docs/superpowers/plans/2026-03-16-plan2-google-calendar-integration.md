# Plan 2: Google Calendar Integration

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Google Calendar for two-way sync — authenticate with Google, create/read/update/delete calendar events, detect external changes, and implement conflict resolution with the suggest-and-confirm reschedule flow.

**Architecture:** A `GoogleAuthManager` handles OAuth sign-in. A `GoogleCalendarApiClient` wraps all Calendar REST API calls. A `CalendarSyncManager` orchestrates sync operations (push task blocks, pull free/busy, detect external changes). A `PendingSyncOperation` Room entity queues calendar writes for offline resilience. The `TaskScheduler` is extended with conflict resolution (NeedsReschedule path).

**Tech Stack:** Google Identity Services (Credential Manager), Google Calendar API v3 (via google-api-client), Room, Hilt, Coroutines

**Spec:** `docs/superpowers/specs/2026-03-16-smart-task-scheduler-design.md`
**Depends on:** Plan 1 (data layer + scheduling algorithm)

---

## File Structure

```
app/
├── build.gradle.kts                                    # Add Google API dependencies
├── src/main/java/com/tasktracker/
│   ├── data/
│   │   ├── calendar/
│   │   │   ├── GoogleAuthManager.kt                    # OAuth sign-in/sign-out via Credential Manager
│   │   │   ├── GoogleCalendarApiClient.kt              # Wraps Calendar API v3 calls
│   │   │   ├── CalendarEventMapper.kt                  # Maps domain ScheduledBlock ↔ Google Event
│   │   │   └── CalendarSyncManager.kt                  # Orchestrates sync: push, pull, detect changes
│   │   └── local/
│   │       ├── entity/
│   │       │   └── PendingSyncOperationEntity.kt       # Offline queue entity
│   │       ├── dao/
│   │       │   └── PendingSyncOperationDao.kt          # Offline queue DAO
│   │       └── TaskTrackerDatabase.kt                  # Modified: add PendingSyncOperation + migration
│   ├── domain/
│   │   ├── model/
│   │   │   ├── SyncOperation.kt                        # Domain model for pending sync ops
│   │   │   └── CalendarEvent.kt                        # Domain model for external calendar events
│   │   ├── repository/
│   │   │   ├── CalendarRepository.kt                   # Interface for calendar API operations
│   │   │   └── SyncOperationRepository.kt              # Interface for offline queue
│   │   └── scheduler/
│   │       └── TaskScheduler.kt                        # Modified: add conflict resolution
│   └── di/
│       └── CalendarModule.kt                           # Hilt module for calendar dependencies
├── src/test/java/com/tasktracker/
│   ├── data/calendar/
│   │   ├── CalendarEventMapperTest.kt
│   │   └── CalendarSyncManagerTest.kt
│   └── domain/scheduler/
│       └── TaskSchedulerConflictTest.kt                # Tests for NeedsReschedule path
└── src/main/res/values/
    └── strings.xml                                     # Add Google sign-in related strings
```

---

## Task 1: Add Google API Dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add dependencies to build.gradle.kts**

Add to the `dependencies` block in `app/build.gradle.kts`:
```kotlin
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Calendar API
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-calendar:v3-rev20241101-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.45.1")

    // DataStore for preferences (calendar ID storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
```

- [ ] **Step 2: Add internet and account permissions to AndroidManifest.xml**

Add inside `<manifest>` before `<application>`:
```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
```

Note: `INTERNET` may already exist from Plan 1. Only add `GET_ACCOUNTS` if missing.

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: add Google Calendar API and auth dependencies"
```

---

## Task 2: Google Auth Manager

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/calendar/GoogleAuthManager.kt`

- [ ] **Step 1: Create GoogleAuthManager**

```kotlin
package com.tasktracker.data.calendar

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val credentialManager = CredentialManager.create(context)

    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    val isSignedIn: Boolean get() = _signedInEmail.value != null

    private val scopes = listOf(
        CalendarScopes.CALENDAR_READONLY,
        CalendarScopes.CALENDAR_EVENTS,
    )

    suspend fun signIn(activityContext: Context): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getWebClientId())
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                _signedInEmail.value = email
                Result.success(email)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        _signedInEmail.value = null
    }

    fun getCalendarCredential(): GoogleAccountCredential? {
        val email = _signedInEmail.value ?: return null
        return GoogleAccountCredential.usingOAuth2(context, scopes)
            .setBackOff(ExponentialBackOff())
            .setSelectedAccountName(email)
    }

    private fun getWebClientId(): String {
        // This should come from google-services.json or BuildConfig
        // For now, read from string resources
        return context.getString(
            context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
        )
    }
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/calendar/GoogleAuthManager.kt
git commit -m "feat: add Google auth manager with Credential Manager API"
```

---

## Task 3: Calendar Event Domain Model and Mapper

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/CalendarEvent.kt`
- Create: `app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt`
- Create: `app/src/test/java/com/tasktracker/data/calendar/CalendarEventMapperTest.kt`

- [ ] **Step 1: Create CalendarEvent domain model**

```kotlin
package com.tasktracker.domain.model

import java.time.Instant

data class CalendarEvent(
    val id: String? = null,
    val calendarId: String,
    val title: String,
    val description: String = "",
    val startTime: Instant,
    val endTime: Instant,
    val isTaskEvent: Boolean = false,
)
```

- [ ] **Step 2: Write failing tests for CalendarEventMapper**

```kotlin
package com.tasktracker.data.calendar

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CalendarEventMapperTest {

    private val mapper = CalendarEventMapper()
    private val now = Instant.parse("2026-03-16T14:00:00Z")

    @Test
    fun `maps ScheduledBlock to Google Event with correct times`() {
        val block = ScheduledBlock(
            id = 1,
            taskId = 10,
            startTime = now,
            endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
        )
        val event = mapper.toGoogleEvent(block, taskTitle = "Write report")
        assertThat(event.summary).isEqualTo("Write report")
        assertThat(event.start.dateTime.value)
            .isEqualTo(now.toEpochMilli())
        assertThat(event.end.dateTime.value)
            .isEqualTo(now.plus(60, ChronoUnit.MINUTES).toEpochMilli())
    }

    @Test
    fun `completed task event title is prefixed`() {
        val block = ScheduledBlock(
            id = 1,
            taskId = 10,
            startTime = now,
            endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.COMPLETED,
        )
        val event = mapper.toGoogleEvent(block, taskTitle = "Write report", completed = true)
        assertThat(event.summary).isEqualTo("Completed: Write report")
    }

    @Test
    fun `maps Google Event back to CalendarEvent domain model`() {
        val googleEvent = com.google.api.services.calendar.model.Event()
            .setId("event123")
            .setSummary("Team standup")
            .setStart(
                com.google.api.services.calendar.model.EventDateTime()
                    .setDateTime(com.google.api.client.util.DateTime(now.toEpochMilli()))
            )
            .setEnd(
                com.google.api.services.calendar.model.EventDateTime()
                    .setDateTime(
                        com.google.api.client.util.DateTime(
                            now.plus(30, ChronoUnit.MINUTES).toEpochMilli()
                        )
                    )
            )
        val calendarEvent = mapper.toDomain(googleEvent, calendarId = "primary")
        assertThat(calendarEvent.id).isEqualTo("event123")
        assertThat(calendarEvent.title).isEqualTo("Team standup")
        assertThat(calendarEvent.startTime).isEqualTo(now)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.data.calendar.CalendarEventMapperTest"`
Expected: FAIL

- [ ] **Step 4: Implement CalendarEventMapper**

```kotlin
package com.tasktracker.data.calendar

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import java.time.Instant
import javax.inject.Inject

class CalendarEventMapper @Inject constructor() {

    fun toGoogleEvent(
        block: ScheduledBlock,
        taskTitle: String,
        completed: Boolean = false,
    ): Event {
        val title = if (completed) "Completed: $taskTitle" else taskTitle
        return Event()
            .setSummary(title)
            .setDescription("Scheduled by Task Tracker")
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
    }

    fun toDomain(event: Event, calendarId: String): CalendarEvent {
        return CalendarEvent(
            id = event.id,
            calendarId = calendarId,
            title = event.summary ?: "",
            description = event.description ?: "",
            startTime = Instant.ofEpochMilli(event.start.dateTime.value),
            endTime = Instant.ofEpochMilli(event.end.dateTime.value),
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.data.calendar.CalendarEventMapperTest"`
Expected: All 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/CalendarEvent.kt app/src/main/java/com/tasktracker/data/calendar/CalendarEventMapper.kt app/src/test/java/com/tasktracker/data/calendar/
git commit -m "feat: add calendar event mapper between domain and Google API models"
```

---

## Task 4: Google Calendar API Client

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/calendar/GoogleCalendarApiClient.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/CalendarRepository.kt` (replaces CalendarSelectionRepository for API operations)

- [ ] **Step 1: Create CalendarRepository interface**

This is the domain-layer interface for calendar API operations (distinct from `CalendarSelectionRepository` which handles local selection preferences).

```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import com.tasktracker.domain.model.TimeSlot
import java.time.Instant

interface CalendarRepository {
    suspend fun listCalendars(): List<CalendarInfo>
    suspend fun getOrCreateTaskCalendar(): String
    suspend fun getFreeBusySlots(
        calendarIds: List<String>,
        timeMin: Instant,
        timeMax: Instant,
    ): List<TimeSlot>
    suspend fun getEvents(
        calendarId: String,
        timeMin: Instant,
        timeMax: Instant,
    ): List<CalendarEvent>
    suspend fun createEvent(calendarId: String, block: ScheduledBlock, taskTitle: String): String
    suspend fun updateEvent(calendarId: String, eventId: String, block: ScheduledBlock, taskTitle: String, completed: Boolean = false)
    suspend fun deleteEvent(calendarId: String, eventId: String)
}

data class CalendarInfo(
    val id: String,
    val name: String,
    val color: String,
    val isPrimary: Boolean,
)
```

- [ ] **Step 2: Implement GoogleCalendarApiClient**

```kotlin
package com.tasktracker.data.calendar

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.FreeBusyRequest
import com.google.api.services.calendar.model.FreeBusyRequestItem
import com.google.api.client.util.DateTime
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import com.tasktracker.domain.model.TimeSlot
import com.tasktracker.domain.repository.CalendarInfo
import com.tasktracker.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarApiClient @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val eventMapper: CalendarEventMapper,
    private val appPreferences: AppPreferences,
) : CalendarRepository {

    companion object {
        private const val APP_NAME = "Task Tracker"
        private const val TASK_CALENDAR_NAME = "Task Tracker"
    }

    private fun getService(): Calendar {
        val credential = authManager.getCalendarCredential()
            ?: throw IllegalStateException("Not signed in")
        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName(APP_NAME).build()
    }

    override suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val service = getService()
        val calendarList = service.calendarList().list().execute()
        calendarList.items.map { entry ->
            CalendarInfo(
                id = entry.id,
                name = entry.summary ?: entry.id,
                color = entry.backgroundColor ?: "#4285F4",
                isPrimary = entry.isPrimary == true,
            )
        }
    }

    override suspend fun getOrCreateTaskCalendar(): String = withContext(Dispatchers.IO) {
        // Check stored calendar ID first
        val storedId = appPreferences.taskCalendarId.first()
        if (storedId != null) {
            // Verify it still exists
            try {
                val service = getService()
                service.calendars().get(storedId).execute()
                return@withContext storedId
            } catch (_: Exception) {
                // Calendar was deleted externally, fall through to search/create
            }
        }

        val service = getService()
        // Search by name
        val calendarList = service.calendarList().list().execute()
        val existing = calendarList.items.find { it.summary == TASK_CALENDAR_NAME }
        if (existing != null) {
            appPreferences.setTaskCalendarId(existing.id)
            return@withContext existing.id
        }
        // Create new calendar
        val newCalendar = com.google.api.services.calendar.model.Calendar()
            .setSummary(TASK_CALENDAR_NAME)
            .setDescription("Managed by Task Tracker app")
        val created = service.calendars().insert(newCalendar).execute()
        appPreferences.setTaskCalendarId(created.id)
        created.id
    }

    override suspend fun getFreeBusySlots(
        calendarIds: List<String>,
        timeMin: Instant,
        timeMax: Instant,
    ): List<TimeSlot> = withContext(Dispatchers.IO) {
        val service = getService()
        val request = FreeBusyRequest()
            .setTimeMin(DateTime(timeMin.toEpochMilli()))
            .setTimeMax(DateTime(timeMax.toEpochMilli()))
            .setItems(calendarIds.map { FreeBusyRequestItem().setId(it) })
        val response = service.freebusy().query(request).execute()
        response.calendars.flatMap { (_, busyInfo) ->
            (busyInfo.busy ?: emptyList()).map { period ->
                TimeSlot(
                    startTime = Instant.ofEpochMilli(period.start.value),
                    endTime = Instant.ofEpochMilli(period.end.value),
                )
            }
        }
    }

    override suspend fun getEvents(
        calendarId: String,
        timeMin: Instant,
        timeMax: Instant,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val service = getService()
        val events = service.events().list(calendarId)
            .setTimeMin(DateTime(timeMin.toEpochMilli()))
            .setTimeMax(DateTime(timeMax.toEpochMilli()))
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .execute()
        events.items.mapNotNull { event ->
            if (event.start?.dateTime == null) return@mapNotNull null
            eventMapper.toDomain(event, calendarId)
        }
    }

    override suspend fun createEvent(
        calendarId: String,
        block: ScheduledBlock,
        taskTitle: String,
    ): String = withContext(Dispatchers.IO) {
        val service = getService()
        val event = eventMapper.toGoogleEvent(block, taskTitle)
        val created = service.events().insert(calendarId, event).execute()
        created.id
    }

    override suspend fun updateEvent(
        calendarId: String,
        eventId: String,
        block: ScheduledBlock,
        taskTitle: String,
        completed: Boolean,
    ) = withContext(Dispatchers.IO) {
        val service = getService()
        val event = eventMapper.toGoogleEvent(block, taskTitle, completed)
        service.events().update(calendarId, eventId, event).execute()
        Unit
    }

    override suspend fun deleteEvent(
        calendarId: String,
        eventId: String,
    ) = withContext(Dispatchers.IO) {
        val service = getService()
        service.events().delete(calendarId, eventId).execute()
        Unit
    }
}
```

- [ ] **Step 3: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/repository/CalendarRepository.kt app/src/main/java/com/tasktracker/data/calendar/GoogleCalendarApiClient.kt
git commit -m "feat: add Google Calendar API client with CRUD and free/busy operations"
```

---

## Task 5: Pending Sync Operation (Offline Queue)

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/SyncOperation.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/entity/PendingSyncOperationEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/PendingSyncOperationDao.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/SyncOperationRepository.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/SyncOperationRepositoryImpl.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt`

- [ ] **Step 1: Create SyncOperation domain model**

```kotlin
package com.tasktracker.domain.model

import java.time.Instant

enum class SyncOperationType {
    CREATE_EVENT,
    UPDATE_EVENT,
    DELETE_EVENT,
    MARK_COMPLETED,
}

data class SyncOperation(
    val id: Long = 0,
    val type: SyncOperationType,
    val blockId: Long,
    val taskId: Long,
    val calendarId: String,
    val eventId: String? = null,
    val createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 2: Create PendingSyncOperationEntity**

```kotlin
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.SyncOperation
import com.tasktracker.domain.model.SyncOperationType
import java.time.Instant

@Entity(tableName = "pending_sync_operations")
data class PendingSyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SyncOperationType,
    val blockId: Long,
    val taskId: Long,
    val calendarId: String,
    val eventId: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    fun toDomain() = SyncOperation(
        id = id,
        type = type,
        blockId = blockId,
        taskId = taskId,
        calendarId = calendarId,
        eventId = eventId,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(op: SyncOperation) = PendingSyncOperationEntity(
            id = op.id,
            type = op.type,
            blockId = op.blockId,
            taskId = op.taskId,
            calendarId = op.calendarId,
            eventId = op.eventId,
            createdAt = op.createdAt,
        )
    }
}
```

- [ ] **Step 3: Create PendingSyncOperationDao**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.PendingSyncOperationEntity

@Dao
interface PendingSyncOperationDao {
    @Insert
    suspend fun insert(operation: PendingSyncOperationEntity): Long

    @Delete
    suspend fun delete(operation: PendingSyncOperationEntity)

    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSyncOperationEntity>

    @Query("DELETE FROM pending_sync_operations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    suspend fun count(): Int
}
```

- [ ] **Step 4: Create SyncOperationRepository interface and implementation**

`SyncOperationRepository.kt`:
```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.SyncOperation

interface SyncOperationRepository {
    suspend fun enqueue(operation: SyncOperation): Long
    suspend fun dequeue(operation: SyncOperation)
    suspend fun getAll(): List<SyncOperation>
    suspend fun clear()
    suspend fun hasPending(): Boolean
}
```

`SyncOperationRepositoryImpl.kt`:
```kotlin
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.PendingSyncOperationDao
import com.tasktracker.data.local.entity.PendingSyncOperationEntity
import com.tasktracker.domain.model.SyncOperation
import com.tasktracker.domain.repository.SyncOperationRepository
import javax.inject.Inject

class SyncOperationRepositoryImpl @Inject constructor(
    private val dao: PendingSyncOperationDao,
) : SyncOperationRepository {

    override suspend fun enqueue(operation: SyncOperation): Long =
        dao.insert(PendingSyncOperationEntity.fromDomain(operation))

    override suspend fun dequeue(operation: SyncOperation) =
        dao.delete(PendingSyncOperationEntity.fromDomain(operation))

    override suspend fun getAll(): List<SyncOperation> =
        dao.getAll().map { it.toDomain() }

    override suspend fun clear() = dao.deleteAll()

    override suspend fun hasPending(): Boolean = dao.count() > 0
}
```

- [ ] **Step 5: Add SyncOperationType converter to Converters.kt**

Add to `app/src/main/java/com/tasktracker/data/local/converter/Converters.kt`:
```kotlin
    @TypeConverter
    fun fromSyncOperationType(value: SyncOperationType?): String? = value?.name

    @TypeConverter
    fun toSyncOperationType(value: String?): SyncOperationType? =
        value?.let { SyncOperationType.valueOf(it) }
```

Also add the import: `import com.tasktracker.domain.model.SyncOperationType`

- [ ] **Step 6: Update TaskTrackerDatabase to include PendingSyncOperation**

Modify `app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt`:

Update the `@Database` annotation to add the new entity and bump version:
```kotlin
@Database(
    entities = [
        TaskEntity::class,
        ScheduledBlockEntity::class,
        UserAvailabilityEntity::class,
        CalendarSelectionEntity::class,
        PendingSyncOperationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
```

Add the import:
```kotlin
import com.tasktracker.data.local.entity.PendingSyncOperationEntity
```

Add the new DAO accessor:
```kotlin
    abstract fun pendingSyncOperationDao(): PendingSyncOperationDao
```

Add a migration companion:
```kotlin
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_sync_operations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        blockId INTEGER NOT NULL,
                        taskId INTEGER NOT NULL,
                        calendarId TEXT NOT NULL,
                        eventId TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
```

- [ ] **Step 7: Update DatabaseModule to provide new DAO and add migration**

Add to `app/src/main/java/com/tasktracker/di/DatabaseModule.kt`:

Update the `provideDatabase` function to add the migration:
```kotlin
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskTrackerDatabase =
        Room.databaseBuilder(
            context,
            TaskTrackerDatabase::class.java,
            "task_tracker.db",
        )
        .addMigrations(TaskTrackerDatabase.MIGRATION_1_2)
        .build()
```

Add the new DAO provider:
```kotlin
    @Provides
    fun providePendingSyncOperationDao(db: TaskTrackerDatabase): PendingSyncOperationDao =
        db.pendingSyncOperationDao()
```

- [ ] **Step 8: Update RepositoryModule with SyncOperationRepository binding**

Add to `app/src/main/java/com/tasktracker/di/RepositoryModule.kt`:
```kotlin
    @Binds
    @Singleton
    abstract fun bindSyncOperationRepository(
        impl: SyncOperationRepositoryImpl
    ): SyncOperationRepository
```

- [ ] **Step 9: Verify project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/SyncOperation.kt app/src/main/java/com/tasktracker/data/local/entity/PendingSyncOperationEntity.kt app/src/main/java/com/tasktracker/data/local/dao/PendingSyncOperationDao.kt app/src/main/java/com/tasktracker/domain/repository/SyncOperationRepository.kt app/src/main/java/com/tasktracker/data/repository/SyncOperationRepositoryImpl.kt app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt app/src/main/java/com/tasktracker/data/local/converter/Converters.kt app/src/main/java/com/tasktracker/di/
git commit -m "feat: add offline sync queue with Room-backed pending operations"
```

---

## Task 6: Calendar Sync Manager

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/calendar/CalendarSyncManager.kt`
- Create: `app/src/test/java/com/tasktracker/data/calendar/CalendarSyncManagerTest.kt`

- [ ] **Step 1: Write failing tests for CalendarSyncManager**

```kotlin
package com.tasktracker.data.calendar

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CalendarSyncManagerTest {

    private lateinit var syncManager: CalendarSyncManager
    private lateinit var fakeCalendarRepo: FakeCalendarRepository
    private lateinit var fakeBlockRepo: FakeScheduledBlockRepository
    private lateinit var fakeTaskRepo: FakeTaskRepository
    private lateinit var fakeSyncOpRepo: FakeSyncOperationRepository

    private val now = Instant.parse("2026-03-16T14:00:00Z")
    private val taskCalendarId = "task-tracker-calendar-id"

    @Before
    fun setup() {
        fakeCalendarRepo = FakeCalendarRepository(taskCalendarId)
        fakeBlockRepo = FakeScheduledBlockRepository()
        fakeTaskRepo = FakeTaskRepository()
        fakeSyncOpRepo = FakeSyncOperationRepository()
        syncManager = CalendarSyncManager(
            calendarRepository = fakeCalendarRepo,
            blockRepository = fakeBlockRepo,
            taskRepository = fakeTaskRepo,
            syncOperationRepository = fakeSyncOpRepo,
        )
    }

    @Test
    fun `pushNewBlock creates event and updates block with event ID`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        syncManager.pushNewBlock(block)
        assertThat(fakeCalendarRepo.createdEvents).hasSize(1)
        assertThat(fakeBlockRepo.updatedBlocks).hasSize(1)
        assertThat(fakeBlockRepo.updatedBlocks[0].googleCalendarEventId).isNotNull()
    }

    @Test
    fun `markTaskCompleted updates event title with Completed prefix`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
            googleCalendarEventId = "event123",
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        fakeBlockRepo.blocks[1] = block
        syncManager.markTaskCompleted(taskId = 10)
        assertThat(fakeCalendarRepo.updatedEvents["event123"]?.completed).isTrue()
    }

    @Test
    fun `pushNewBlock queues operation when offline`() = runTest {
        fakeCalendarRepo.simulateOffline = true
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        syncManager.pushNewBlock(block)
        assertThat(fakeSyncOpRepo.operations).hasSize(1)
        assertThat(fakeSyncOpRepo.operations[0].type).isEqualTo(SyncOperationType.CREATE_EVENT)
    }

    @Test
    fun `deleteTaskEvents deletes calendar events and cancels blocks`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
            googleCalendarEventId = "event123",
        )
        fakeBlockRepo.blocks[1] = block
        syncManager.deleteTaskEvents(taskId = 10)
        assertThat(fakeCalendarRepo.deletedEventIds).contains("event123")
    }

    // Fake implementations for testing
    class FakeCalendarRepository(private val taskCalendarId: String) : CalendarRepository {
        var simulateOffline = false
        val createdEvents = mutableListOf<Pair<ScheduledBlock, String>>()
        val updatedEvents = mutableMapOf<String, UpdatedEvent>()
        val deletedEventIds = mutableListOf<String>()
        private var eventCounter = 0

        data class UpdatedEvent(val block: ScheduledBlock, val title: String, val completed: Boolean)

        override suspend fun listCalendars() = emptyList<CalendarInfo>()
        override suspend fun getOrCreateTaskCalendar() = taskCalendarId
        override suspend fun getFreeBusySlots(calendarIds: List<String>, timeMin: Instant, timeMax: Instant) = emptyList<TimeSlot>()
        override suspend fun getEvents(calendarId: String, timeMin: Instant, timeMax: Instant) = emptyList<CalendarEvent>()
        override suspend fun createEvent(calendarId: String, block: ScheduledBlock, taskTitle: String): String {
            if (simulateOffline) throw java.io.IOException("No network")
            val id = "generated-event-${eventCounter++}"
            createdEvents.add(block to taskTitle)
            return id
        }
        override suspend fun updateEvent(calendarId: String, eventId: String, block: ScheduledBlock, taskTitle: String, completed: Boolean) {
            if (simulateOffline) throw java.io.IOException("No network")
            updatedEvents[eventId] = UpdatedEvent(block, taskTitle, completed)
        }
        override suspend fun deleteEvent(calendarId: String, eventId: String) {
            if (simulateOffline) throw java.io.IOException("No network")
            deletedEventIds.add(eventId)
        }
    }

    class FakeScheduledBlockRepository : ScheduledBlockRepository {
        val blocks = mutableMapOf<Long, ScheduledBlock>()
        val updatedBlocks = mutableListOf<ScheduledBlock>()
        override suspend fun insert(block: ScheduledBlock) = block.id
        override suspend fun insertAll(blocks: List<ScheduledBlock>) = blocks.map { it.id }
        override suspend fun update(block: ScheduledBlock) { updatedBlocks.add(block); blocks[block.id] = block }
        override suspend fun delete(block: ScheduledBlock) { blocks.remove(block.id) }
        override suspend fun getByTaskId(taskId: Long) = blocks.values.filter { it.taskId == taskId }
        override fun observeByTaskId(taskId: Long) = kotlinx.coroutines.flow.flowOf(blocks.values.filter { it.taskId == taskId })
        override suspend fun getByStatuses(statuses: List<BlockStatus>) = blocks.values.filter { it.status in statuses }
        override fun observeInRange(start: Instant, end: Instant) = kotlinx.coroutines.flow.flowOf(emptyList<ScheduledBlock>())
        override suspend fun updateStatus(id: Long, status: BlockStatus) { blocks[id] = blocks[id]!!.copy(status = status) }
        override suspend fun deleteByTaskId(taskId: Long) { blocks.entries.removeAll { it.value.taskId == taskId } }
        override suspend fun deleteProposed() { blocks.entries.removeAll { it.value.status == BlockStatus.PROPOSED } }
    }

    class FakeTaskRepository : TaskRepository {
        val tasks = mutableMapOf<Long, Task>()
        override suspend fun insert(task: Task) = task.id
        override suspend fun update(task: Task) { tasks[task.id] = task }
        override suspend fun delete(task: Task) { tasks.remove(task.id) }
        override suspend fun getById(id: Long) = tasks[id]
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(tasks.values.toList())
        override suspend fun getByStatus(status: TaskStatus) = tasks.values.filter { it.status == status }
        override suspend fun getByStatuses(statuses: List<TaskStatus>) = tasks.values.filter { it.status in statuses }
        override suspend fun updateStatus(id: Long, status: TaskStatus) { tasks[id] = tasks[id]!!.copy(status = status) }
    }

    class FakeSyncOperationRepository : SyncOperationRepository {
        val operations = mutableListOf<SyncOperation>()
        override suspend fun enqueue(operation: SyncOperation): Long { operations.add(operation); return operation.id }
        override suspend fun dequeue(operation: SyncOperation) { operations.removeAll { it.id == operation.id } }
        override suspend fun getAll() = operations.toList()
        override suspend fun clear() { operations.clear() }
        override suspend fun hasPending() = operations.isNotEmpty()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.data.calendar.CalendarSyncManagerTest"`
Expected: FAIL

- [ ] **Step 3: Implement CalendarSyncManager**

```kotlin
package com.tasktracker.data.calendar

import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncManager @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val syncOperationRepository: SyncOperationRepository,
) {
    private suspend fun getTaskCalendarId(): String =
        calendarRepository.getOrCreateTaskCalendar()

    suspend fun pushNewBlock(block: ScheduledBlock) {
        val task = taskRepository.getById(block.taskId) ?: return
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.CREATE_EVENT, block, task)
            return
        }

        try {
            val eventId = calendarRepository.createEvent(calendarId, block, task.title)
            blockRepository.update(block.copy(googleCalendarEventId = eventId))
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.CREATE_EVENT, block, task)
        }
    }

    suspend fun markTaskCompleted(taskId: Long) {
        val task = taskRepository.getById(taskId) ?: return
        val blocks = blockRepository.getByTaskId(taskId)
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            blocks.filter { it.googleCalendarEventId != null }.forEach { block ->
                enqueueOperation(SyncOperationType.MARK_COMPLETED, block, task)
            }
            return
        }

        for (block in blocks) {
            val eventId = block.googleCalendarEventId ?: continue
            try {
                calendarRepository.updateEvent(
                    calendarId = calendarId,
                    eventId = eventId,
                    block = block,
                    taskTitle = task.title,
                    completed = true,
                )
            } catch (e: IOException) {
                enqueueOperation(SyncOperationType.MARK_COMPLETED, block, task)
            }
        }
    }

    suspend fun deleteTaskEvents(taskId: Long) {
        val blocks = blockRepository.getByTaskId(taskId)
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            blocks.filter { it.googleCalendarEventId != null }.forEach { block ->
                val task = taskRepository.getById(taskId) ?: return
                enqueueOperation(SyncOperationType.DELETE_EVENT, block, task)
            }
            return
        }

        for (block in blocks) {
            val eventId = block.googleCalendarEventId ?: continue
            try {
                calendarRepository.deleteEvent(calendarId, eventId)
            } catch (e: IOException) {
                val task = taskRepository.getById(taskId) ?: return
                enqueueOperation(SyncOperationType.DELETE_EVENT, block, task)
            }
        }
    }

    suspend fun updateBlockEvent(block: ScheduledBlock) {
        val task = taskRepository.getById(block.taskId) ?: return
        val eventId = block.googleCalendarEventId ?: return
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.UPDATE_EVENT, block, task)
            return
        }

        try {
            calendarRepository.updateEvent(calendarId, eventId, block, task.title)
        } catch (e: IOException) {
            enqueueOperation(SyncOperationType.UPDATE_EVENT, block, task)
        }
    }

    suspend fun processPendingOperations() {
        val pending = syncOperationRepository.getAll()
        val calendarId = try {
            getTaskCalendarId()
        } catch (e: IOException) {
            return // Still offline
        }
        for (op in pending) {
            try {
                val task = taskRepository.getById(op.taskId) ?: continue
                val block = blockRepository.getByTaskId(op.taskId)
                    .find { it.id == op.blockId }
                val resolvedCalendarId = op.calendarId.ifEmpty { calendarId }
                when (op.type) {
                    SyncOperationType.CREATE_EVENT -> {
                        if (block != null) {
                            val eventId = calendarRepository.createEvent(
                                resolvedCalendarId, block, task.title
                            )
                            blockRepository.update(
                                block.copy(googleCalendarEventId = eventId)
                            )
                        }
                    }
                    SyncOperationType.UPDATE_EVENT -> {
                        if (block != null && op.eventId != null) {
                            calendarRepository.updateEvent(
                                resolvedCalendarId, op.eventId, block, task.title
                            )
                        }
                    }
                    SyncOperationType.DELETE_EVENT -> {
                        if (op.eventId != null) {
                            calendarRepository.deleteEvent(resolvedCalendarId, op.eventId)
                        }
                    }
                    SyncOperationType.MARK_COMPLETED -> {
                        if (block != null && op.eventId != null) {
                            calendarRepository.updateEvent(
                                resolvedCalendarId, op.eventId, block, task.title,
                                completed = true,
                            )
                        }
                    }
                }
                syncOperationRepository.dequeue(op)
            } catch (e: IOException) {
                break // Still offline, stop processing
            }
        }
    }

    private suspend fun enqueueOperation(
        type: SyncOperationType,
        block: ScheduledBlock,
        task: Task,
    ) {
        // Store empty calendarId — processPendingOperations resolves it fresh
        syncOperationRepository.enqueue(
            SyncOperation(
                type = type,
                blockId = block.id,
                taskId = task.id,
                calendarId = "",
                eventId = block.googleCalendarEventId,
            )
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.data.calendar.CalendarSyncManagerTest"`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/calendar/CalendarSyncManager.kt app/src/test/java/com/tasktracker/data/calendar/CalendarSyncManagerTest.kt
git commit -m "feat: add calendar sync manager with offline queue fallback"
```

---

## Task 7: Conflict Resolution in TaskScheduler

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerConflictTest.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt`

- [ ] **Step 1: Write failing tests for conflict resolution**

```kotlin
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.*
import java.time.temporal.ChronoUnit

class TaskSchedulerConflictTest {

    private val scheduler = TaskScheduler(
        priorityComparator = TaskPriorityComparator(),
        slotFinder = SlotFinder(),
    )
    private val zoneId = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 3, 16)

    private fun availability(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(12, 0),
    ) = UserAvailability(dayOfWeek = day, startTime = start, endTime = end)

    private fun task(
        id: Long = 1,
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
        deadline: Instant? = null,
        createdAt: Instant = Instant.parse("2026-03-16T00:00:00Z"),
    ) = Task(
        id = id, title = "Task $id",
        estimatedDurationMinutes = duration, quadrant = quadrant,
        deadline = deadline, createdAt = createdAt,
    )

    @Test
    fun `NeedsReschedule returned when new high-priority task displaces existing`() {
        // Existing: 3 hours of low-priority tasks fill 9am-12pm
        val existingBlocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 2,
                startTime = monday.atTime(9, 0).atZone(zoneId).toInstant(),
                endTime = monday.atTime(12, 0).atZone(zoneId).toInstant(),
                status = BlockStatus.CONFIRMED,
            ),
        )
        val existingTask = task(id = 2, duration = 180, quadrant = Quadrant.NEITHER)
        val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT)

        val result = scheduler.scheduleWithConflictResolution(
            newTask = newTask,
            allTasks = listOf(existingTask, newTask),
            existingBlocks = existingBlocks,
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
        val reschedule = result as SchedulingResult.NeedsReschedule
        assertThat(reschedule.newBlocks).isNotEmpty()
        assertThat(reschedule.newBlocks[0].taskId).isEqualTo(3)
    }

    @Test
    fun `no reschedule needed when space exists`() {
        // Existing: 1 hour used, 2 hours free
        val existingBlocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 2,
                startTime = monday.atTime(9, 0).atZone(zoneId).toInstant(),
                endTime = monday.atTime(10, 0).atZone(zoneId).toInstant(),
                status = BlockStatus.CONFIRMED,
            ),
        )
        val existingTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER)
        val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT)

        val result = scheduler.scheduleWithConflictResolution(
            newTask = newTask,
            allTasks = listOf(existingTask, newTask),
            existingBlocks = existingBlocks,
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
    }

    @Test
    fun `reschedule blocks have PROPOSED status`() {
        val existingBlocks = listOf(
            ScheduledBlock(
                id = 1, taskId = 2,
                startTime = monday.atTime(9, 0).atZone(zoneId).toInstant(),
                endTime = monday.atTime(12, 0).atZone(zoneId).toInstant(),
                status = BlockStatus.CONFIRMED,
            ),
        )
        val existingTask = task(id = 2, duration = 180, quadrant = Quadrant.NEITHER)
        val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT)

        val result = scheduler.scheduleWithConflictResolution(
            newTask = newTask,
            allTasks = listOf(existingTask, newTask),
            existingBlocks = existingBlocks,
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        val reschedule = result as SchedulingResult.NeedsReschedule
        assertThat(reschedule.newBlocks.all { it.status == BlockStatus.PROPOSED }).isTrue()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerConflictTest"`
Expected: FAIL

- [ ] **Step 3: Add scheduleWithConflictResolution to TaskScheduler**

Add this method to `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt`:

```kotlin
    fun scheduleWithConflictResolution(
        newTask: Task,
        allTasks: List<Task>,
        existingBlocks: List<ScheduledBlock>,
        availability: List<UserAvailability>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
    ): SchedulingResult {
        // First, try to schedule the new task without touching existing blocks
        val directResult = schedule(
            tasks = listOf(newTask),
            existingBlocks = existingBlocks,
            availability = availability,
            busySlots = busySlots,
            startDate = startDate,
            endDate = endDate,
            zoneId = zoneId,
        )
        if (directResult is SchedulingResult.Scheduled && directResult.blocks.isNotEmpty()) {
            return directResult
        }

        // Can't fit — try displacing lower-priority tasks
        val lowerPriorityBlocks = existingBlocks.filter { block ->
            val blockTask = allTasks.find { it.id == block.taskId }
            blockTask != null &&
                block.status == BlockStatus.CONFIRMED &&
                priorityComparator.compare(newTask, blockTask) < 0
        }

        if (lowerPriorityBlocks.isEmpty()) {
            return if (newTask.deadline != null) {
                SchedulingResult.DeadlineAtRisk(newTask, "Cannot fit before deadline.")
            } else {
                SchedulingResult.NoSlotsAvailable(newTask, "No slots available.")
            }
        }

        // Re-run scheduler with lower-priority blocks removed
        val remainingBlocks = existingBlocks - lowerPriorityBlocks.toSet()
        val tasksToReschedule = allTasks.filter { task ->
            task.id == newTask.id || lowerPriorityBlocks.any { it.taskId == task.id }
        }

        val rescheduleResult = schedule(
            tasks = tasksToReschedule,
            existingBlocks = remainingBlocks,
            availability = availability,
            busySlots = busySlots,
            startDate = startDate,
            endDate = endDate,
            zoneId = zoneId,
        )

        return when (rescheduleResult) {
            is SchedulingResult.Scheduled -> {
                val proposedBlocks = rescheduleResult.blocks.map {
                    it.copy(status = BlockStatus.PROPOSED)
                }
                val movedPairs = lowerPriorityBlocks.mapNotNull { oldBlock ->
                    val newBlock = proposedBlocks.find { it.taskId == oldBlock.taskId }
                    if (newBlock != null) oldBlock to newBlock else null
                }
                val newTaskBlocks = proposedBlocks.filter { it.taskId == newTask.id }
                SchedulingResult.NeedsReschedule(
                    newBlocks = newTaskBlocks,
                    movedBlocks = movedPairs,
                )
            }
            else -> rescheduleResult
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerConflictTest"`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerConflictTest.kt
git commit -m "feat: add conflict resolution with suggest-and-confirm reschedule flow"
```

---

## Task 8: Calendar Hilt Module

**Files:**
- Create: `app/src/main/java/com/tasktracker/di/CalendarModule.kt`

- [ ] **Step 1: Create CalendarModule**

```kotlin
package com.tasktracker.di

import com.tasktracker.data.calendar.GoogleCalendarApiClient
import com.tasktracker.domain.repository.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: GoogleCalendarApiClient,
    ): CalendarRepository
}
```

- [ ] **Step 2: Verify full project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/di/CalendarModule.kt
git commit -m "feat: add Hilt module for calendar repository binding"
```

---

## Summary

After completing all 8 tasks, you will have:

- Google OAuth sign-in via Credential Manager API
- A Google Calendar API client that can list calendars, create/read/update/delete events, and query free/busy data
- A calendar event mapper for converting between domain models and Google API models
- A calendar sync manager that orchestrates push/pull operations with offline queue fallback
- A Room-backed offline queue (PendingSyncOperation) for calendar writes that fail due to network issues
- Conflict resolution in the scheduler (NeedsReschedule path) with PROPOSED status blocks
- Full Hilt DI wiring for all calendar dependencies
- Unit tests for the mapper, sync manager, and conflict resolution

**Deferred to Plan 4:** External change detection (comparing local blocks against Google Calendar events to detect deletions/moves) is implemented in Plan 4's `ExternalChangeDetector` and `CalendarSyncWorker`, not in this plan. The `CalendarSyncManager` here handles outbound sync only.

**Next plan:** Plan 3 — UI Layer (screens, ViewModels, navigation)
