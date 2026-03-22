package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ScheduledTimeComparatorTest {

    private val comparator = ScheduledTimeComparator()
    private val now = Instant.parse("2026-03-16T10:00:00Z")

    private fun task(
        quadrant: Quadrant = Quadrant.IMPORTANT,
        deadline: Instant? = null,
        createdAt: Instant = now,
    ) = Task(
        title = "Test",
        estimatedDurationMinutes = 60,
        quadrant = quadrant,
        deadline = deadline,
        createdAt = createdAt,
    )

    private fun scheduled(
        task: Task = task(),
        nextBlockStart: Instant? = null,
        nextBlockEnd: Instant? = null,
    ) = TaskWithScheduleInfo(
        task = task,
        nextBlockStart = nextBlockStart,
        nextBlockEnd = nextBlockEnd,
    )

    @Test
    fun `scheduled tasks sort before unscheduled tasks`() {
        val items = listOf(
            scheduled(task = task(), nextBlockStart = null),
            scheduled(task = task(), nextBlockStart = now.plus(1, ChronoUnit.HOURS)),
        )
        val sorted = items.sortedWith(comparator)
        assertThat(sorted[0].nextBlockStart).isNotNull()
        assertThat(sorted[1].nextBlockStart).isNull()
    }

    @Test
    fun `scheduled tasks sort by start time ascending`() {
        val early = now.plus(1, ChronoUnit.HOURS)
        val late = now.plus(3, ChronoUnit.HOURS)
        val items = listOf(
            scheduled(nextBlockStart = late),
            scheduled(nextBlockStart = early),
        )
        val sorted = items.sortedWith(comparator)
        assertThat(sorted[0].nextBlockStart).isEqualTo(early)
        assertThat(sorted[1].nextBlockStart).isEqualTo(late)
    }

    @Test
    fun `same start time falls back to priority comparator`() {
        val time = now.plus(1, ChronoUnit.HOURS)
        val items = listOf(
            scheduled(task = task(quadrant = Quadrant.NEITHER), nextBlockStart = time),
            scheduled(task = task(quadrant = Quadrant.URGENT_IMPORTANT), nextBlockStart = time),
        )
        val sorted = items.sortedWith(comparator)
        assertThat(sorted[0].task.quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(sorted[1].task.quadrant).isEqualTo(Quadrant.NEITHER)
    }

    @Test
    fun `unscheduled tasks fall back to priority comparator`() {
        val items = listOf(
            scheduled(task = task(quadrant = Quadrant.NEITHER)),
            scheduled(task = task(quadrant = Quadrant.URGENT_IMPORTANT)),
        )
        val sorted = items.sortedWith(comparator)
        assertThat(sorted[0].task.quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(sorted[1].task.quadrant).isEqualTo(Quadrant.NEITHER)
    }

    @Test
    fun `mixed scheduled and unscheduled with correct ordering`() {
        val items = listOf(
            scheduled(task = task(quadrant = Quadrant.URGENT_IMPORTANT), nextBlockStart = null),
            scheduled(task = task(quadrant = Quadrant.NEITHER), nextBlockStart = now.plus(2, ChronoUnit.HOURS)),
            scheduled(task = task(quadrant = Quadrant.IMPORTANT), nextBlockStart = now.plus(1, ChronoUnit.HOURS)),
            scheduled(task = task(quadrant = Quadrant.URGENT), nextBlockStart = null),
        )
        val sorted = items.sortedWith(comparator)
        // Scheduled first (by time), then unscheduled (by priority)
        assertThat(sorted[0].nextBlockStart).isEqualTo(now.plus(1, ChronoUnit.HOURS))
        assertThat(sorted[1].nextBlockStart).isEqualTo(now.plus(2, ChronoUnit.HOURS))
        assertThat(sorted[2].task.quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(sorted[3].task.quadrant).isEqualTo(Quadrant.URGENT)
    }
}
