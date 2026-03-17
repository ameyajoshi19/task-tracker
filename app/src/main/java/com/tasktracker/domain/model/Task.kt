package com.tasktracker.domain.model

import java.time.Instant

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val recurringPattern: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
