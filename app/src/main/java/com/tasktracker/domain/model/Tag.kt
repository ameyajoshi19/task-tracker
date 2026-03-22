package com.tasktracker.domain.model

import java.time.Instant

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: Long,
    val createdAt: Instant = Instant.now(),
)
