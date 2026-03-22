package com.tasktracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Core domain entity representing a single unit of schedulable work.
 *
 * @property splittable When true, the task may be broken across multiple time blocks with a
 *   minimum block size of 30 minutes, allowing partial progress across separate slots.
 * @property recurringTaskId ID of the [RecurringTask] template that generated this instance,
 *   or null for one-off tasks.
 * @property instanceDate The specific calendar date this instance represents within a recurring
 *   series; null for non-recurring tasks.
 * @property fixedTime When set, the task must start at this exact local time rather than being
 *   placed freely within an availability window.
 */
data class Task(
    val id: Long = 0,
    val title: String,
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val recurringPattern: String? = null,
    val recurringTaskId: Long? = null,
    val instanceDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val availabilitySlot: AvailabilitySlotType? = null,
    val tagId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
