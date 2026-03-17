package com.tasktracker.domain.model

data class CalendarSelection(
    val id: Long = 0,
    val googleCalendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val enabled: Boolean = true,
)
