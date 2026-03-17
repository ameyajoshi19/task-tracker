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
