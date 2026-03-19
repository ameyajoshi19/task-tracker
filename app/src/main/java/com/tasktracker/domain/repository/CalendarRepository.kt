package com.tasktracker.domain.repository

import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import com.tasktracker.domain.model.TimeSlot
import java.time.Instant

interface CalendarRepository {
    suspend fun listCalendars(): List<CalendarInfo>
    suspend fun getOrCreateTaskCalendar(): String
    suspend fun renameCalendar(calendarId: String, newName: String)
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
