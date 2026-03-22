package com.tasktracker.data.local.dao

import com.tasktracker.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class TaskWithNextBlockTuple(
    val id: Long,
    val title: String,
    val description: String,
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant?,
    val dayPreference: DayPreference,
    val splittable: Boolean,
    val status: TaskStatus,
    val recurringPattern: String?,
    val recurringTaskId: Long?,
    val instanceDate: LocalDate?,
    val fixedTime: LocalTime?,
    val availabilitySlot: String?,
    val tagId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextBlockStart: Instant?,
    val nextBlockEnd: Instant?,
    val blockCount: Int,
    val tagName: String?,
    val tagColor: Long?,
)
