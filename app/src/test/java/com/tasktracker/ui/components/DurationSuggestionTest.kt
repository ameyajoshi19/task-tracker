// app/src/test/java/com/tasktracker/ui/components/DurationSuggestionTest.kt
package com.tasktracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationSuggestionTest {
    @Test
    fun `meeting suggests 30 minutes`() {
        assertEquals(30, suggestDuration("Team meeting"))
    }

    @Test
    fun `standup suggests 30 minutes`() {
        assertEquals(30, suggestDuration("Daily standup"))
    }

    @Test
    fun `review suggests 60 minutes`() {
        assertEquals(60, suggestDuration("Design review with team"))
    }

    @Test
    fun `email suggests 15 minutes`() {
        assertEquals(15, suggestDuration("Reply to vendor email"))
    }

    @Test
    fun `build suggests 240 minutes`() {
        assertEquals(240, suggestDuration("Build payment integration"))
    }

    @Test
    fun `write suggests 120 minutes`() {
        assertEquals(120, suggestDuration("Write API documentation"))
    }

    @Test
    fun `research suggests 120 minutes`() {
        assertEquals(120, suggestDuration("Research caching strategies"))
    }

    @Test
    fun `no keyword returns null`() {
        assertNull(suggestDuration("Fix bug"))
    }

    @Test
    fun `case insensitive matching`() {
        assertEquals(30, suggestDuration("TEAM MEETING"))
    }

    @Test
    fun `first match wins`() {
        // "review meeting" — "review" comes first alphabetically but
        // we check title left-to-right, so whichever keyword appears first wins
        assertEquals(60, suggestDuration("review meeting"))
    }

    @Test
    fun `empty title returns null`() {
        assertNull(suggestDuration(""))
    }
}
