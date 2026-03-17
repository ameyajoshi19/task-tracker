package com.tasktracker.domain.model

import java.time.Instant

data class ScheduledBlock(
    val id: Long = 0,
    val taskId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val googleCalendarEventId: String? = null,
    val status: BlockStatus = BlockStatus.CONFIRMED,
)
