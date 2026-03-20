package com.tasktracker.domain.model

import java.time.LocalDate

data class RecurringTaskException(
    val id: Long = 0,
    val recurringTaskId: Long,
    val exceptionDate: LocalDate,
)
