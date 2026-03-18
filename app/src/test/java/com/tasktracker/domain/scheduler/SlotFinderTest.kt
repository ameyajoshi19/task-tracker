package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.*
import java.time.temporal.ChronoUnit

class SlotFinderTest {

    private val finder = SlotFinder()
    private val zoneId = ZoneId.of("America/New_York")

    // Monday 2026-03-16
    private val monday = LocalDate.of(2026, 3, 16)
    private val testNow = monday.atStartOfDay(zoneId).toInstant()

    private fun availability(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(17, 0),
    ) = UserAvailability(
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        enabled = true,
    )

    private fun busySlot(
        date: LocalDate = monday,
        startHour: Int,
        endHour: Int,
    ) = TimeSlot(
        startTime = date.atTime(startHour, 0).atZone(zoneId).toInstant(),
        endTime = date.atTime(endHour, 0).atZone(zoneId).toInstant(),
    )

    @Test
    fun `returns full window when no busy slots`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(1)
        assertThat(slots[0].durationMinutes).isEqualTo(480) // 9am-5pm
    }

    @Test
    fun `subtracts busy slot from middle of window`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(busySlot(startHour = 12, endHour = 13)),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(2)
        assertThat(slots[0].durationMinutes).isEqualTo(180) // 9-12
        assertThat(slots[1].durationMinutes).isEqualTo(240) // 1-5
    }

    @Test
    fun `busy slot at start of window trims correctly`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(busySlot(startHour = 9, endHour = 11)),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(1)
        assertThat(slots[0].durationMinutes).isEqualTo(360) // 11am-5pm
    }

    @Test
    fun `WEEKDAY preference excludes weekends`() {
        val saturday = monday.plusDays(5) // 2026-03-21
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SATURDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = saturday,
            dayPreference = DayPreference.WEEKDAY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(1) // Only Monday
    }

    @Test
    fun `WEEKEND preference excludes weekdays`() {
        val sunday = monday.plusDays(6) // 2026-03-22
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SUNDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = sunday,
            dayPreference = DayPreference.WEEKEND,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(1) // Only Sunday
    }

    @Test
    fun `disabled availability windows are excluded`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability().copy(enabled = false),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).isEmpty()
    }

    @Test
    fun `multiple busy slots carve out multiple gaps`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(
                busySlot(startHour = 10, endHour = 11),
                busySlot(startHour = 14, endHour = 15),
            ),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(3)
        assertThat(slots[0].durationMinutes).isEqualTo(60)  // 9-10
        assertThat(slots[1].durationMinutes).isEqualTo(180) // 11-14
        assertThat(slots[2].durationMinutes).isEqualTo(120) // 15-17
    }

    @Test
    fun `slots are sorted chronologically`() {
        val tuesday = monday.plusDays(1)
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.TUESDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = tuesday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
            now = testNow,
        )
        assertThat(slots).hasSize(2)
        assertThat(slots[0].startTime).isLessThan(slots[1].startTime)
    }
}
