package com.tasktracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RecurringTask(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val intervalDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
