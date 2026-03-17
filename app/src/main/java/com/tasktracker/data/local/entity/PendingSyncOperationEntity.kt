package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.SyncOperation
import com.tasktracker.domain.model.SyncOperationType
import java.time.Instant

@Entity(tableName = "pending_sync_operations")
data class PendingSyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SyncOperationType,
    val blockId: Long,
    val taskId: Long,
    val calendarId: String,
    val eventId: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    fun toDomain() = SyncOperation(
        id = id,
        type = type,
        blockId = blockId,
        taskId = taskId,
        calendarId = calendarId,
        eventId = eventId,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(op: SyncOperation) = PendingSyncOperationEntity(
            id = op.id,
            type = op.type,
            blockId = op.blockId,
            taskId = op.taskId,
            calendarId = op.calendarId,
            eventId = op.eventId,
            createdAt = op.createdAt,
        )
    }
}
