package com.tasktracker.data.calendar

import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
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
