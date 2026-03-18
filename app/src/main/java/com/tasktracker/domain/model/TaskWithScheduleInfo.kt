package com.tasktracker.domain.model

import java.time.Instant

data class TaskWithScheduleInfo(
    val task: Task,
    val nextBlockStart: Instant? = null,
    val nextBlockEnd: Instant? = null,
    val blockCount: Int = 0,
)
