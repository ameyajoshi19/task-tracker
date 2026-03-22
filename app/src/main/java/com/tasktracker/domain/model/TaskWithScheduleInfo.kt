package com.tasktracker.domain.model

import java.time.Instant
import java.time.LocalDate

data class TaskWithScheduleInfo(
    val task: Task,
    val nextBlockStart: Instant? = null,
    val nextBlockEnd: Instant? = null,
    val blockCount: Int = 0,
    val recurringTaskId: Long? = null,
    val instanceDate: LocalDate? = null,
    val tagName: String? = null,
    val tagColor: Long? = null,
    val availabilitySlot: AvailabilitySlotType? = null,
)
