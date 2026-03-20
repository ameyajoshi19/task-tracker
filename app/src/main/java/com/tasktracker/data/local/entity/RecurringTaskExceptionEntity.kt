package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.RecurringTaskException
import java.time.LocalDate

@Entity(
    tableName = "recurring_task_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recurringTaskId"),
        Index(value = ["recurringTaskId", "exceptionDate"], unique = true),
    ],
)
data class RecurringTaskExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringTaskId: Long,
    val exceptionDate: LocalDate,
) {
    fun toDomain() = RecurringTaskException(
        id = id,
        recurringTaskId = recurringTaskId,
        exceptionDate = exceptionDate,
    )

    companion object {
        fun fromDomain(e: RecurringTaskException) = RecurringTaskExceptionEntity(
            id = e.id,
            recurringTaskId = e.recurringTaskId,
            exceptionDate = e.exceptionDate,
        )
    }
}
