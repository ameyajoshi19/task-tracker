package com.tasktracker.data.calendar

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
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
