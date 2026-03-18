package com.tasktracker.data.local.dao

import com.tasktracker.domain.model.*
import java.time.Instant

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
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextBlockStart: Instant?,
    val nextBlockEnd: Instant?,
    val blockCount: Int,
)
