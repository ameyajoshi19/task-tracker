package com.tasktracker.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class UserAvailability(
    val id: Long = 0,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
)
