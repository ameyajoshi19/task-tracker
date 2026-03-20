// app/src/main/java/com/tasktracker/domain/scheduler/RecurrenceExpander.kt
package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.*
import java.time.LocalDate

class RecurrenceExpander {

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
