package com.tasktracker.domain.model

import java.time.Duration
import java.time.Instant

data class TimeSlot(
    val startTime: Instant,
    val endTime: Instant,
) {
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}
