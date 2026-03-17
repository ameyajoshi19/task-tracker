package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TaskPriorityComparatorTest {

    private val comparator = TaskPriorityComparator()
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

    @Test
    fun `URGENT_IMPORTANT sorts before IMPORTANT`() {
        val tasks = listOf(
            task(quadrant = Quadrant.IMPORTANT),
            task(quadrant = Quadrant.URGENT_IMPORTANT),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(sorted[1].quadrant).isEqualTo(Quadrant.IMPORTANT)
    }

    @Test
    fun `full quadrant ordering is correct`() {
        val tasks = listOf(
            task(quadrant = Quadrant.NEITHER),
            task(quadrant = Quadrant.URGENT),
            task(quadrant = Quadrant.URGENT_IMPORTANT),
            task(quadrant = Quadrant.IMPORTANT),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted.map { it.quadrant }).containsExactly(
            Quadrant.URGENT_IMPORTANT,
            Quadrant.IMPORTANT,
            Quadrant.URGENT,
            Quadrant.NEITHER,
        ).inOrder()
    }

    @Test
    fun `within same quadrant, nearer deadline sorts first`() {
        val tasks = listOf(
            task(deadline = now.plus(3, ChronoUnit.DAYS)),
            task(deadline = now.plus(1, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].deadline).isEqualTo(now.plus(1, ChronoUnit.DAYS))
    }

    @Test
    fun `tasks without deadlines sort after tasks with deadlines in same quadrant`() {
        val tasks = listOf(
            task(deadline = null),
            task(deadline = now.plus(5, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].deadline).isNotNull()
        assertThat(sorted[1].deadline).isNull()
    }

    @Test
    fun `tie-breaker is earlier createdAt`() {
        val early = now.minus(1, ChronoUnit.HOURS)
        val late = now
        val tasks = listOf(
            task(createdAt = late),
            task(createdAt = early),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].createdAt).isEqualTo(early)
        assertThat(sorted[1].createdAt).isEqualTo(late)
    }

    @Test
    fun `higher quadrant wins even with nearer deadline in lower quadrant`() {
        val tasks = listOf(
            task(quadrant = Quadrant.NEITHER, deadline = now.plus(1, ChronoUnit.HOURS)),
            task(quadrant = Quadrant.URGENT_IMPORTANT, deadline = now.plus(30, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
    }
}
