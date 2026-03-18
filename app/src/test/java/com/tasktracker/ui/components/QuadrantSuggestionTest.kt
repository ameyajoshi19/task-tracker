// app/src/test/java/com/tasktracker/ui/components/QuadrantSuggestionTest.kt
package com.tasktracker.ui.components

import com.tasktracker.domain.model.Quadrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class QuadrantSuggestionTest {
    private val now = Instant.now()

    @Test
    fun `no deadline returns null`() {
        assertNull(suggestQuadrant(null, now))
    }

    @Test
    fun `deadline today suggests Now`() {
        val deadline = now.plus(6, ChronoUnit.HOURS)
        assertEquals(Quadrant.URGENT_IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `overdue deadline suggests Now`() {
        val deadline = now.minus(1, ChronoUnit.HOURS)
        assertEquals(Quadrant.URGENT_IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline in 2 days suggests Soon`() {
        val deadline = now.plus(2, ChronoUnit.DAYS)
        assertEquals(Quadrant.URGENT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline in 5 days suggests Next`() {
        val deadline = now.plus(5, ChronoUnit.DAYS)
        assertEquals(Quadrant.IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline beyond 1 week returns null`() {
        val deadline = now.plus(10, ChronoUnit.DAYS)
        assertNull(suggestQuadrant(deadline, now))
    }
}
