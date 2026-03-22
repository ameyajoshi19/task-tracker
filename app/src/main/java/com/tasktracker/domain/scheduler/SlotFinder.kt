package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.domain.model.DayPreference
import com.tasktracker.domain.model.TimeSlot
import java.time.*

/**
 * Computes free time windows by subtracting busy periods from the user's availability windows.
 * Given a set of recurring daily availability entries and a list of already-occupied
 * [TimeSlot]s, it returns the contiguous free slots available for scheduling across a date range.
 */
class SlotFinder {

    fun findAvailableSlots(
        availability: List<AvailabilitySlot>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        dayPreference: DayPreference,
        zoneId: ZoneId,
        now: Instant = Instant.now(),
        slotType: AvailabilitySlotType? = null,
    ): List<TimeSlot> {
        val result = mutableListOf<TimeSlot>()
        val filteredAvailability = availability
            .filter { it.enabled }
            .let { enabled ->
                if (slotType != null) enabled.filter { it.slotType == slotType } else enabled
            }

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek
            if (!matchesDayPreference(dayOfWeek, dayPreference)) {
                currentDate = currentDate.plusDays(1)
                continue
            }

            val windowsForDay = filteredAvailability.filter { it.dayOfWeek == dayOfWeek }
            for (window in windowsForDay) {
                var windowStart = currentDate.atTime(window.startTime)
                    .atZone(zoneId).toInstant()
                val windowEnd = currentDate.atTime(window.endTime)
                    .atZone(zoneId).toInstant()

                // Don't offer past time as available; snap to next clean boundary
                if (windowStart < now) {
                    windowStart = snapUpToCleanBoundary(now, zoneId)
                }
                if (windowStart >= windowEnd) {
                    continue
                }

                val freeSlots = subtractBusySlots(
                    windowStart, windowEnd, busySlots
                )
                result.addAll(freeSlots)
            }
            currentDate = currentDate.plusDays(1)
        }

        return result.sortedBy { it.startTime }
    }

    private fun matchesDayPreference(
        dayOfWeek: DayOfWeek,
        preference: DayPreference,
    ): Boolean {
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
        return when (preference) {
            DayPreference.WEEKDAY -> !isWeekend
            DayPreference.WEEKEND -> isWeekend
            DayPreference.ANY -> true
        }
    }

    private fun subtractBusySlots(
        windowStart: Instant,
        windowEnd: Instant,
        busySlots: List<TimeSlot>,
    ): List<TimeSlot> {
        val relevant = busySlots
            .filter { it.startTime < windowEnd && it.endTime > windowStart }
            .sortedBy { it.startTime }

        if (relevant.isEmpty()) {
            return listOf(TimeSlot(windowStart, windowEnd))
        }

        val freeSlots = mutableListOf<TimeSlot>()
        var cursor = windowStart

        for (busy in relevant) {
            val busyStart = maxOf(busy.startTime, windowStart)
            if (cursor < busyStart) {
                freeSlots.add(TimeSlot(cursor, busyStart))
            }
            cursor = maxOf(cursor, minOf(busy.endTime, windowEnd))
        }

        if (cursor < windowEnd) {
            freeSlots.add(TimeSlot(cursor, windowEnd))
        }

        return freeSlots
    }

    /**
     * Snaps an instant forward to the next 15-minute boundary (:00, :15, :30, :45).
     * If already on a boundary, returns as-is (with seconds/nanos zeroed).
     */
    private fun snapUpToCleanBoundary(instant: Instant, zoneId: ZoneId): Instant {
        val zoned = instant.atZone(zoneId)
        val minute = zoned.minute
        val second = zoned.second
        val nano = zoned.nano
        val nextBoundary = ((minute + 14) / 15) * 15  // ceiling to next 15-min mark
        return if (minute % 15 == 0 && second == 0 && nano == 0) {
            zoned.withSecond(0).withNano(0).toInstant()
        } else if (nextBoundary < 60) {
            zoned.withMinute(nextBoundary).withSecond(0).withNano(0).toInstant()
        } else {
            zoned.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant()
        }
    }

    private fun maxOf(a: Instant, b: Instant): Instant = if (a > b) a else b
    private fun minOf(a: Instant, b: Instant): Instant = if (a < b) a else b
}
