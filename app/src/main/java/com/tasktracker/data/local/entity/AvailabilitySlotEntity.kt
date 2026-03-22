package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(
    tableName = "availability_slots",
    indices = [
        Index(value = ["slotType", "dayOfWeek"], unique = true),
    ],
)
data class AvailabilitySlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotType: AvailabilitySlotType,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = false,
) {
    fun toDomain() = AvailabilitySlot(
        id = id,
        slotType = slotType,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(slot: AvailabilitySlot) = AvailabilitySlotEntity(
            id = slot.id,
            slotType = slot.slotType,
            dayOfWeek = slot.dayOfWeek,
            startTime = slot.startTime,
            endTime = slot.endTime,
            enabled = slot.enabled,
        )
    }
}
