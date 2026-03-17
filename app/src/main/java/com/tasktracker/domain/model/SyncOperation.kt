package com.tasktracker.domain.model

import java.time.Instant

enum class SyncOperationType {
    CREATE_EVENT,
    UPDATE_EVENT,
    DELETE_EVENT,
    MARK_COMPLETED,
}

data class SyncOperation(
    val id: Long = 0,
    val type: SyncOperationType,
    val blockId: Long,
    val taskId: Long,
    val calendarId: String,
    val eventId: String? = null,
    val createdAt: Instant = Instant.now(),
)
