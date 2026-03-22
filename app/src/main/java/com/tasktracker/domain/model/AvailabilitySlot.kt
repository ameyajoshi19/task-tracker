package com.tasktracker.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class AvailabilitySlotType {
    BEFORE_WORK,
    DURING_WORK,
    AFTER_WORK;

    val displayName: String
        get() = when (this) {
            BEFORE_WORK -> "Before Work"
            DURING_WORK -> "During Work"
            AFTER_WORK -> "After Work"
        }
}

data class AvailabilitySlot(
    val id: Long = 0,
    val slotType: AvailabilitySlotType,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = false,
)
