package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.*
import java.time.temporal.ChronoUnit

class TaskSchedulerTest {

    private val scheduler = TaskScheduler(
        priorityComparator = TaskPriorityComparator(),
        slotFinder = SlotFinder(),
    )
    private val zoneId = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 3, 16)
    private val testNow = monday.atStartOfDay(zoneId).toInstant()

    private fun availability(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(17, 0),
        slotType: AvailabilitySlotType = AvailabilitySlotType.DURING_WORK,
    ) = AvailabilitySlot(slotType = slotType, dayOfWeek = day, startTime = start, endTime = end, enabled = true)

    private fun task(
        id: Long = 1,
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
        deadline: Instant? = null,
        splittable: Boolean = false,
        dayPreference: DayPreference = DayPreference.ANY,
        createdAt: Instant = Instant.parse("2026-03-16T00:00:00Z"),
    ) = Task(
        id = id,
        title = "Task $id",
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        deadline = deadline,
        splittable = splittable,
        dayPreference = dayPreference,
        createdAt = createdAt,
    )

    @Test
    fun `schedules single task into first available slot`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].taskId).isEqualTo(1)
        // Should start at 9am
        val expectedStart = monday.atTime(9, 0).atZone(zoneId).toInstant()
        assertThat(blocks[0].startTime).isEqualTo(expectedStart)
        assertThat(blocks[0].endTime).isEqualTo(expectedStart.plus(60, ChronoUnit.MINUTES))
    }

    @Test
    fun `best-fit - smaller task fills small slot instead of leaving it empty`() {
        // 1-hour slot (9-10), then busy (10-12), then open (12-5)
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(12, 0).atZone(zoneId).toInstant(),
            ),
        )
        val bigTask = task(id = 1, duration = 120, quadrant = Quadrant.URGENT_IMPORTANT)
        val smallTask = task(id = 2, duration = 60, quadrant = Quadrant.IMPORTANT)

        val result = scheduler.schedule(
            tasks = listOf(bigTask, smallTask),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks

        // Small task should be in 9-10 slot, big task in 12-2 slot
        val smallBlock = blocks.find { it.taskId == 2L }!!
        val bigBlock = blocks.find { it.taskId == 1L }!!

        val nineAm = monday.atTime(9, 0).atZone(zoneId).toInstant()
        val noon = monday.atTime(12, 0).atZone(zoneId).toInstant()

        assertThat(smallBlock.startTime).isEqualTo(nineAm)
        assertThat(bigBlock.startTime).isEqualTo(noon)
    }

    @Test
    fun `splittable task splits across multiple slots`() {
        // Two 1-hour slots with a busy gap between
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(14, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 120, splittable = true)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(2)
        assertThat(blocks.sumOf { it.endTime.epochSecond - it.startTime.epochSecond })
            .isEqualTo(7200) // 120 minutes total
    }

    @Test
    fun `splittable task does not create blocks smaller than 30 minutes`() {
        // 20-min slot then big slot
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 20).atZone(zoneId).toInstant(),
                monday.atTime(12, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, splittable = true)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        // 9:00-9:20 is only 20 min — too small for 30-min minimum
        // Should schedule full 60 min at 12:00
        assertThat(blocks).hasSize(1)
        val noon = monday.atTime(12, 0).atZone(zoneId).toInstant()
        assertThat(blocks[0].startTime).isEqualTo(noon)
    }

    @Test
    fun `respects deadline - only uses slots before deadline`() {
        val tuesday = monday.plusDays(1)
        val deadlineInstant = monday.atTime(12, 0).atZone(zoneId).toInstant()

        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, deadline = deadlineInstant)),
            existingBlocks = emptyList(),
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.TUESDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = tuesday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].endTime).isAtMost(deadlineInstant)
    }

    @Test
    fun `returns NoSlotsAvailable when task cannot fit`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = emptyList<AvailabilitySlot>(),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.NoSlotsAvailable::class.java)
    }

    @Test
    fun `returns DeadlineAtRisk when task cannot fit before deadline`() {
        val deadlineInstant = monday.atTime(10, 0).atZone(zoneId).toInstant()
        // Busy from 9-10, deadline at 10 → no room
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 0).atZone(zoneId).toInstant(),
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, deadline = deadlineInstant)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.DeadlineAtRisk::class.java)
    }

    @Test
    fun `blocks are created with CONFIRMED status`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks.all { it.status == BlockStatus.CONFIRMED }).isTrue()
    }

    @Test
    fun `schedules multiple tasks across slots by best-fit`() {
        // 3 tasks of varying sizes, 2 slots: 1-hour (9-10) and 3-hour (11-14)
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(11, 0).atZone(zoneId).toInstant(),
            ),
        )
        val tasks = listOf(
            task(id = 1, duration = 120, quadrant = Quadrant.URGENT_IMPORTANT),
            task(id = 2, duration = 60, quadrant = Quadrant.IMPORTANT),
            task(id = 3, duration = 30, quadrant = Quadrant.URGENT),
        )
        val result = scheduler.schedule(
            tasks = tasks,
            existingBlocks = emptyList(),
            availability = listOf(availability(end = LocalTime.of(14, 0))),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(3)
        // All 3 tasks should be scheduled
        assertThat(blocks.map { it.taskId }.toSet()).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `respects WEEKDAY day preference in scheduler`() {
        val saturday = monday.plusDays(5) // 2026-03-21
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, dayPreference = DayPreference.WEEKDAY)),
            existingBlocks = emptyList(),
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SATURDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = saturday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        val scheduledDay = blocks[0].startTime.atZone(zoneId).dayOfWeek
        assertThat(scheduledDay).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `non-splittable task exceeding all slots returns NoSlotsAvailable with suggestion`() {
        // Only a 30-min slot available, task needs 60 min
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 30).atZone(zoneId).toInstant(),
                monday.atTime(17, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, splittable = false)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(result).isInstanceOf(SchedulingResult.NoSlotsAvailable::class.java)
        assertThat((result as SchedulingResult.NoSlotsAvailable).message)
            .contains("splittable")
    }

    @Test
    fun `scheduleWithConflictResolution displaces lower-priority task when no slots available`() {
        val tuesday = monday.plusDays(1)
        val avail = availability(day = DayOfWeek.MONDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))
        val tuesdayAvail = availability(day = DayOfWeek.TUESDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))

        val existingTask = task(id = 1, duration = 120, quadrant = Quadrant.IMPORTANT)
        val existingBlock = ScheduledBlock(
            id = 1,
            taskId = 1,
            startTime = monday.atTime(18, 0).atZone(zoneId).toInstant(),
            endTime = monday.atTime(20, 0).atZone(zoneId).toInstant(),
            status = BlockStatus.CONFIRMED,
        )

        val deadlineTonight = monday.atTime(23, 59).atZone(zoneId).toInstant()
        val newTask = task(id = 2, duration = 15, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadlineTonight)

        val result = scheduler.scheduleWithConflictResolution(
            newTask = newTask,
            allTasks = listOf(existingTask, newTask),
            existingBlocks = listOf(existingBlock),
            availability = listOf(avail, tuesdayAvail),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday.plusDays(7),
            zoneId = zoneId,
            now = testNow,
        )

        assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
        val reschedule = result as SchedulingResult.NeedsReschedule
        assertThat(reschedule.newBlocks).hasSize(1)
        assertThat(reschedule.newBlocks[0].taskId).isEqualTo(2L)
        val newBlockDay = reschedule.newBlocks[0].startTime.atZone(zoneId).toLocalDate()
        assertThat(newBlockDay).isEqualTo(monday)
        assertThat(reschedule.movedBlocks).hasSize(1)
        assertThat(reschedule.movedBlocks[0].first.taskId).isEqualTo(1L)
        assertThat(reschedule.movedBlocks[0].second.taskId).isEqualTo(1L)
    }

    @Test
    fun `scheduleWithConflictResolution schedules new task even when displaced task loses its deadline`() {
        val avail = availability(day = DayOfWeek.MONDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))

        val deadlineTonight = monday.atTime(23, 59).atZone(zoneId).toInstant()
        val existingTask = task(id = 1, duration = 120, quadrant = Quadrant.IMPORTANT, deadline = deadlineTonight)
        val existingBlock = ScheduledBlock(
            id = 1,
            taskId = 1,
            startTime = monday.atTime(18, 0).atZone(zoneId).toInstant(),
            endTime = monday.atTime(20, 0).atZone(zoneId).toInstant(),
            status = BlockStatus.CONFIRMED,
        )

        val newTask = task(id = 2, duration = 15, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadlineTonight)

        val result = scheduler.scheduleWithConflictResolution(
            newTask = newTask,
            allTasks = listOf(existingTask, newTask),
            existingBlocks = listOf(existingBlock),
            availability = listOf(avail),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )

        assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
        val reschedule = result as SchedulingResult.NeedsReschedule
        assertThat(reschedule.newBlocks).hasSize(1)
        assertThat(reschedule.newBlocks[0].taskId).isEqualTo(2L)
        assertThat(reschedule.displacedTasks).hasSize(1)
        assertThat(reschedule.displacedTasks[0].id).isEqualTo(1L)
    }

    @Test
    fun `scheduler avoids busySlots even when they contain the tasks own event`() {
        // Documents scheduler behavior: busySlots are always honored.
        // When a task's own calendar event appears in busySlots (as happens
        // when the free/busy API includes it), the scheduler correctly skips
        // that slot. The fix for the edit bug must happen in the caller
        // (TaskEditViewModel) by filtering busySlots before calling the scheduler.
        val ownSlotStart = monday.atTime(9, 0).atZone(zoneId).toInstant()
        val ownSlotEnd = monday.atTime(11, 0).atZone(zoneId).toInstant()

        val busySlotsWithOwnEvent = listOf(
            TimeSlot(ownSlotStart, ownSlotEnd),
        )

        val editedTask = task(id = 1, duration = 120)

        val result = scheduler.schedule(
            tasks = listOf(editedTask),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlotsWithOwnEvent,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )

        // Task lands at 11am — the scheduler correctly avoids the busy slot.
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startTime).isEqualTo(
            monday.atTime(11, 0).atZone(zoneId).toInstant()
        )
    }

    @Test
    fun `scheduler places task at 9am when busySlots are filtered`() {
        // After the fix: busySlots no longer include the task's own event.
        // The scheduler should place the task at 9am.
        val editedTask = task(id = 1, duration = 120)

        val result = scheduler.schedule(
            tasks = listOf(editedTask),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
            now = testNow,
        )

        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startTime).isEqualTo(
            monday.atTime(9, 0).atZone(zoneId).toInstant()
        )
    }
}
