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
