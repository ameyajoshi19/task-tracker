package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "recurring_tasks")
data class RecurringTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val intervalDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain() = RecurringTask(
        id = id,
        title = title,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        quadrant = quadrant,
        dayPreference = dayPreference,
        splittable = splittable,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        fixedTime = fixedTime,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(rt: RecurringTask) = RecurringTaskEntity(
            id = rt.id,
            title = rt.title,
            description = rt.description,
            estimatedDurationMinutes = rt.estimatedDurationMinutes,
            quadrant = rt.quadrant,
            dayPreference = rt.dayPreference,
            splittable = rt.splittable,
            intervalDays = rt.intervalDays,
            startDate = rt.startDate,
            endDate = rt.endDate,
            fixedTime = rt.fixedTime,
            createdAt = rt.createdAt,
            updatedAt = rt.updatedAt,
        )
    }
}
