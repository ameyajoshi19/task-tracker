package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import java.time.Instant

@Entity(
    tableName = "scheduled_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class ScheduledBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val googleCalendarEventId: String? = null,
    val status: BlockStatus = BlockStatus.CONFIRMED,
) {
    fun toDomain() = ScheduledBlock(
        id = id,
        taskId = taskId,
        startTime = startTime,
        endTime = endTime,
        googleCalendarEventId = googleCalendarEventId,
        status = status,
    )

    companion object {
        fun fromDomain(block: ScheduledBlock) = ScheduledBlockEntity(
            id = block.id,
            taskId = block.taskId,
            startTime = block.startTime,
            endTime = block.endTime,
            googleCalendarEventId = block.googleCalendarEventId,
            status = block.status,
        )
    }
}
