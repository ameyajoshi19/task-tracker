package com.tasktracker.domain.validation

import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.UserAvailability
import java.time.Duration

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * Validates a [Task] before it is persisted or submitted for scheduling. Enforces business rules
 * around duration bounds and, for non-splittable tasks, ensures the duration can actually fit
 * within at least one of the user's enabled availability windows.
 */
class TaskValidator {

    fun validate(task: Task, availability: List<UserAvailability>): ValidationResult {
        if (task.estimatedDurationMinutes < 15) {
            return ValidationResult.Invalid(
                "Duration must be at least 15 minutes."
            )
        }
        if (task.estimatedDurationMinutes > 480) {
            return ValidationResult.Invalid(
                "Duration must not exceed 480 minutes (8 hours)."
            )
        }
        if (task.estimatedDurationMinutes % 5 != 0) {
            return ValidationResult.Invalid(
                "Duration must be in 5-minute increments."
            )
        }
        if (!task.splittable) {
            val longestWindowMinutes = availability
                .filter { it.enabled }
                .maxOfOrNull {
                    Duration.between(it.startTime, it.endTime).toMinutes()
                } ?: 0L
            if (task.estimatedDurationMinutes > longestWindowMinutes) {
                return ValidationResult.Invalid(
                    "Non-splittable task duration exceeds the longest availability window " +
                        "($longestWindowMinutes min). Make the task splittable or extend " +
                        "an availability window."
                )
            }
        }
        return ValidationResult.Valid
    }
}
