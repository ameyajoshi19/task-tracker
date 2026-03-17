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
