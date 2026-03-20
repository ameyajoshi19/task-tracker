// app/src/main/java/com/tasktracker/domain/validation/RecurringTaskValidator.kt
package com.tasktracker.domain.validation

import com.tasktracker.domain.model.RecurringTask
import com.tasktracker.domain.model.UserAvailability
import java.time.Duration
import java.time.LocalDate

/**
 * Validates a [RecurringTask] template before it is persisted. In addition to the duration rules
 * shared with [TaskValidator], it checks recurrence-specific constraints: interval length, start
 * date must not be in the past on creation, and end date must follow start date.
 */
class RecurringTaskValidator {

    fun validate(
        recurringTask: RecurringTask,
        availability: List<UserAvailability>,
        today: LocalDate = LocalDate.now(),
        isCreation: Boolean = true,
    ): ValidationResult {
        if (recurringTask.intervalDays < 1) {
            return ValidationResult.Invalid("Repeat interval must be at least 1 day.")
        }
        if (isCreation && recurringTask.startDate.isBefore(today)) {
            return ValidationResult.Invalid("Start date must not be in the past.")
        }
        if (recurringTask.endDate != null && recurringTask.endDate.isBefore(recurringTask.startDate)) {
            return ValidationResult.Invalid("The end date must be after the start date.")
        }
        if (recurringTask.estimatedDurationMinutes < 15) {
            return ValidationResult.Invalid("Duration must be at least 15 minutes.")
        }
        if (recurringTask.estimatedDurationMinutes > 480) {
            return ValidationResult.Invalid("Duration must not exceed 480 minutes (8 hours).")
        }
        if (recurringTask.estimatedDurationMinutes % 5 != 0) {
            return ValidationResult.Invalid("Duration must be in 5-minute increments.")
        }
        if (!recurringTask.splittable) {
            val longestWindowMinutes = availability
                .filter { it.enabled }
                .maxOfOrNull { Duration.between(it.startTime, it.endTime).toMinutes() } ?: 0L
            if (recurringTask.estimatedDurationMinutes > longestWindowMinutes) {
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
