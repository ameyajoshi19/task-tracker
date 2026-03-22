package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.TaskWithScheduleInfo

/**
 * Orders tasks for display: scheduled tasks (by ascending start time) come before
 * unscheduled tasks. Unscheduled tasks fall back to [TaskPriorityComparator] ordering.
 */
class ScheduledTimeComparator(
    private val priorityComparator: TaskPriorityComparator = TaskPriorityComparator(),
) : Comparator<TaskWithScheduleInfo> {

    override fun compare(a: TaskWithScheduleInfo, b: TaskWithScheduleInfo): Int {
        val aTime = a.nextBlockStart
        val bTime = b.nextBlockStart

        // Both scheduled — sort by start time ascending
        if (aTime != null && bTime != null) {
            val timeDiff = aTime.compareTo(bTime)
            if (timeDiff != 0) return timeDiff
            // Same start time — fall back to priority
            return priorityComparator.compare(a.task, b.task)
        }

        // Scheduled before unscheduled
        if (aTime != null && bTime == null) return -1
        if (aTime == null && bTime != null) return 1

        // Both unscheduled — fall back to priority
        return priorityComparator.compare(a.task, b.task)
    }
}
