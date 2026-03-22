package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.Tag
import java.time.Instant

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val createdAt: Instant = Instant.now(),
) {
    fun toDomain() = Tag(
        id = id,
        name = name,
        color = color,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(tag: Tag) = TagEntity(
            id = tag.id,
            name = tag.name,
            color = tag.color,
            createdAt = tag.createdAt,
        )
    }
}
