// app/src/test/java/com/tasktracker/domain/scheduler/RecurrenceExpanderTest.kt
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceExpanderTest {

    private val expander = RecurrenceExpander()

    private fun recurringTask(
        id: Long = 1,
        intervalDays: Int = 1,
        startDate: LocalDate = LocalDate.of(2026, 3, 19),
        endDate: LocalDate? = null,
        fixedTime: LocalTime? = null,
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
        dayPreference: DayPreference = DayPreference.ANY,
        splittable: Boolean = false,
    ) = RecurringTask(
        id = id,
        title = "Recurring $id",
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        dayPreference = dayPreference,
        splittable = splittable,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        fixedTime = fixedTime,
    )

    // --- Basic generation ---

    @Test
    fun `generates daily instances within window`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(3) // Mar 19, 20, 21 (windowEnd is exclusive)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `generates every-3-days instances`() {
        val rt = recurringTask(intervalDays = 3, startDate = LocalDate.of(2026, 3, 19))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 4, 2),
        )
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 22),
            LocalDate.of(2026, 3, 25),
            LocalDate.of(2026, 3, 28),
            LocalDate.of(2026, 3, 31),
        )
    }

    @Test
    fun `copies template fields to generated instances`() {
        val rt = recurringTask(
            id = 42,
            duration = 90,
            quadrant = Quadrant.URGENT_IMPORTANT,
            dayPreference = DayPreference.WEEKDAY,
            splittable = true,
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        val task = result[0]
        assertThat(task.title).isEqualTo("Recurring 42")
        assertThat(task.estimatedDurationMinutes).isEqualTo(90)
        assertThat(task.quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(task.dayPreference).isEqualTo(DayPreference.WEEKDAY)
        assertThat(task.splittable).isTrue()
        assertThat(task.recurringTaskId).isEqualTo(42)
        assertThat(task.status).isEqualTo(TaskStatus.PENDING)
        assertThat(task.deadline).isNull()
    }

    // --- Boundary conditions ---

    @Test
    fun `respects endDate`() {
        val rt = recurringTask(
            intervalDays = 1,
            startDate = LocalDate.of(2026, 3, 19),
            endDate = LocalDate.of(2026, 3, 21),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        // endDate is inclusive — generates Mar 19, 20, 21
        assertThat(result).hasSize(3)
        assertThat(result.last().instanceDate).isEqualTo(LocalDate.of(2026, 3, 21))
    }

    @Test
    fun `skips dates before windowStart`() {
        val rt = recurringTask(
            intervalDays = 1,
            startDate = LocalDate.of(2026, 3, 15),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(3) // Mar 19, 20, 21
        assertThat(result.first().instanceDate).isEqualTo(LocalDate.of(2026, 3, 19))
    }

    @Test
    fun `returns empty list when startDate is after windowEnd`() {
        val rt = recurringTask(startDate = LocalDate.of(2026, 4, 1))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when endDate is before windowStart`() {
        val rt = recurringTask(
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 10),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        assertThat(result).isEmpty()
    }

    // --- Filtering ---

    @Test
    fun `skips exception dates`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val exceptions = listOf(
            RecurringTaskException(recurringTaskId = 1, exceptionDate = LocalDate.of(2026, 3, 20)),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = exceptions,
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(2) // Mar 19, 21 (skipped 20)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `skips dates with existing instances`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val existing = listOf(
            Task(
                id = 100,
                title = "Existing",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                recurringTaskId = 1,
                instanceDate = LocalDate.of(2026, 3, 19),
            ),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = existing,
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(2) // Mar 20, 21 (skipped 19)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `copies fixedTime to generated instances`() {
        val rt = recurringTask(fixedTime = LocalTime.of(7, 0))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].fixedTime).isEqualTo(LocalTime.of(7, 0))
    }

    @Test
    fun `flexible instances have null fixedTime`() {
        val rt = recurringTask(fixedTime = null)
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].fixedTime).isNull()
    }

    @Test
    fun `generated instances always have null deadline`() {
        val rt = recurringTask()
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].deadline).isNull()
    }

    @Test
    fun `every-3-days with startDate before window aligns correctly`() {
        // Start on Mar 15, every 3 days: 15, 18, 21, 24...
        val rt = recurringTask(intervalDays = 3, startDate = LocalDate.of(2026, 3, 15))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        // Only Mar 21 and Mar 24 fall in window
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 21),
            LocalDate.of(2026, 3, 24),
        )
    }
}
