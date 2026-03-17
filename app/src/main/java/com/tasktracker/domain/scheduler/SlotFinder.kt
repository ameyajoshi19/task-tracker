package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.DayPreference
import com.tasktracker.domain.model.TimeSlot
import com.tasktracker.domain.model.UserAvailability
import java.time.*

class SlotFinder {

    fun findAvailableSlots(
        availability: List<UserAvailability>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        dayPreference: DayPreference,
        zoneId: ZoneId,
    ): List<TimeSlot> {
        val result = mutableListOf<TimeSlot>()
        val enabledAvailability = availability.filter { it.enabled }

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek
            if (!matchesDayPreference(dayOfWeek, dayPreference)) {
                currentDate = currentDate.plusDays(1)
                continue
            }

            val windowsForDay = enabledAvailability.filter { it.dayOfWeek == dayOfWeek }
            for (window in windowsForDay) {
                val windowStart = currentDate.atTime(window.startTime)
                    .atZone(zoneId).toInstant()
                val windowEnd = currentDate.atTime(window.endTime)
                    .atZone(zoneId).toInstant()

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

    private fun maxOf(a: Instant, b: Instant): Instant = if (a > b) a else b
    private fun minOf(a: Instant, b: Instant): Instant = if (a < b) a else b
}
