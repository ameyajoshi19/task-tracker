package com.tasktracker.domain.model

import java.time.Instant

data class CalendarEvent(
    val id: String? = null,
    val calendarId: String,
    val title: String,
    val description: String = "",
    val startTime: Instant,
    val endTime: Instant,
    val isTaskEvent: Boolean = false,
)
