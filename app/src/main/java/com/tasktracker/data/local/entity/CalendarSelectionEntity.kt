package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.CalendarSelection

@Entity(tableName = "calendar_selections")
data class CalendarSelectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val googleCalendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val enabled: Boolean = true,
) {
    fun toDomain() = CalendarSelection(
        id = id,
        googleCalendarId = googleCalendarId,
        calendarName = calendarName,
        calendarColor = calendarColor,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(cs: CalendarSelection) = CalendarSelectionEntity(
            id = cs.id,
            googleCalendarId = cs.googleCalendarId,
            calendarName = cs.calendarName,
            calendarColor = cs.calendarColor,
            enabled = cs.enabled,
        )
    }
}
