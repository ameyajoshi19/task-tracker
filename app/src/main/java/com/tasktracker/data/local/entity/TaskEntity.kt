package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "tasks",
    indices = [
        Index("recurringTaskId"),
        Index(value = ["recurringTaskId", "instanceDate"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val recurringPattern: String? = null,
    val recurringTaskId: Long? = null,
    val instanceDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val availabilitySlot: AvailabilitySlotType? = null,
    val tagId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain() = Task(
        id = id,
        title = title,
        estimatedDurationMinutes = estimatedDurationMinutes,
        quadrant = quadrant,
        deadline = deadline,
        dayPreference = dayPreference,
        splittable = splittable,
        status = status,
        recurringPattern = recurringPattern,
        recurringTaskId = recurringTaskId,
        instanceDate = instanceDate,
        fixedTime = fixedTime,
        availabilitySlot = availabilitySlot,
        tagId = tagId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: Task) = TaskEntity(
            id = task.id,
            title = task.title,
            estimatedDurationMinutes = task.estimatedDurationMinutes,
            quadrant = task.quadrant,
            deadline = task.deadline,
            dayPreference = task.dayPreference,
            splittable = task.splittable,
            status = task.status,
            recurringPattern = task.recurringPattern,
            recurringTaskId = task.recurringTaskId,
            instanceDate = task.instanceDate,
            fixedTime = task.fixedTime,
            availabilitySlot = task.availabilitySlot,
            tagId = task.tagId,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
