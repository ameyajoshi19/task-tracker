package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.*
import java.time.Instant

@Entity(tableName = "tasks")
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
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain() = Task(
        id = id,
        title = title,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        quadrant = quadrant,
        deadline = deadline,
        dayPreference = dayPreference,
        splittable = splittable,
        status = status,
        recurringPattern = recurringPattern,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: Task) = TaskEntity(
            id = task.id,
            title = task.title,
            description = task.description,
            estimatedDurationMinutes = task.estimatedDurationMinutes,
            quadrant = task.quadrant,
            deadline = task.deadline,
            dayPreference = task.dayPreference,
            splittable = task.splittable,
            status = task.status,
            recurringPattern = task.recurringPattern,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
