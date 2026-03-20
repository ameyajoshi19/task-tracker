// app/src/main/java/com/tasktracker/domain/scheduler/RecurrenceExpander.kt
package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.*
import java.time.LocalDate

/**
 * Materialises concrete [Task] instances from a [RecurringTask] template for a given date window.
 * Acts as the bridge between the recurrence template and the scheduling engine, which operates
 * only on individual task instances.
 */
class RecurrenceExpander {

    /**
     * Generates new [Task] instances for [recurringTask] within [[windowStart], [windowEnd]).
     *
     * Skips dates covered by [exceptions] (user-cancelled occurrences) and dates for which
     * [existingInstances] already exist, ensuring idempotent expansion on repeated calls.
     *
     * @param recurringTask Template defining the recurrence pattern and task properties.
     * @param exceptions Dates on which no instance should be generated.
     * @param existingInstances Previously persisted instances used to avoid duplicates.
     * @return New (unsaved) task instances ready to be persisted and scheduled.
     */
    fun expand(
        recurringTask: RecurringTask,
        exceptions: List<RecurringTaskException>,
        existingInstances: List<Task>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): List<Task> {
        val exceptionDates = exceptions.map { it.exceptionDate }.toSet()
        val existingDates = existingInstances
            .filter { it.recurringTaskId == recurringTask.id }
            .mapNotNull { it.instanceDate }
            .toSet()

        val result = mutableListOf<Task>()
        var date = recurringTask.startDate

        // Advance date to first occurrence in or after windowStart
        if (date < windowStart) {
            val daysBehind = windowStart.toEpochDay() - date.toEpochDay()
            val intervalsToSkip = (daysBehind + recurringTask.intervalDays - 1) / recurringTask.intervalDays
            date = date.plusDays(intervalsToSkip * recurringTask.intervalDays)
        }

        val effectiveEnd = if (recurringTask.endDate != null && recurringTask.endDate < windowEnd) {
            recurringTask.endDate.plusDays(1) // endDate is inclusive
        } else {
            windowEnd
        }

        while (date < effectiveEnd) {
            if (date >= windowStart && date !in exceptionDates && date !in existingDates) {
                result.add(
                    Task(
                        title = recurringTask.title,
                        description = recurringTask.description,
                        estimatedDurationMinutes = recurringTask.estimatedDurationMinutes,
                        quadrant = recurringTask.quadrant,
                        dayPreference = recurringTask.dayPreference,
                        splittable = recurringTask.splittable,
                        recurringTaskId = recurringTask.id,
                        instanceDate = date,
                        fixedTime = recurringTask.fixedTime,
                        status = TaskStatus.PENDING,
                    )
                )
            }
            date = date.plusDays(recurringTask.intervalDays.toLong())
        }

        return result
    }
}
