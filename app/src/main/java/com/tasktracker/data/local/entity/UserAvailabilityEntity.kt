package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.UserAvailability
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "user_availability")
data class UserAvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
) {
    fun toDomain() = UserAvailability(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(ua: UserAvailability) = UserAvailabilityEntity(
            id = ua.id,
            dayOfWeek = ua.dayOfWeek,
            startTime = ua.startTime,
            endTime = ua.endTime,
            enabled = ua.enabled,
        )
    }
}
