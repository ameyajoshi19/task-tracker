package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.Task

/**
 * Orders tasks for the scheduling algorithm using a three-level sort:
 * 1. Eisenhower quadrant priority (Q1 urgent+important first, Q4 last).
 * 2. Within the same quadrant, tasks with a deadline come before those without; among tasks that
 *    both have deadlines, the nearer deadline wins.
 * 3. Tie-breaker: earlier [Task.createdAt] first (first-in, first-scheduled).
 */
class TaskPriorityComparator : Comparator<Task> {

    override fun compare(a: Task, b: Task): Int {
        // 1. Quadrant priority (lower priority value = higher priority)
        val quadrantDiff = a.quadrant.priority - b.quadrant.priority
        if (quadrantDiff != 0) return quadrantDiff

        // 2. Within same quadrant: tasks with deadlines before tasks without
        val aDeadline = a.deadline
        val bDeadline = b.deadline
        if (aDeadline != null && bDeadline == null) return -1
        if (aDeadline == null && bDeadline != null) return 1

        // 3. Both have deadlines: nearer deadline first
        if (aDeadline != null && bDeadline != null) {
            val deadlineDiff = aDeadline.compareTo(bDeadline)
            if (deadlineDiff != 0) return deadlineDiff
        }

        // 4. Tie-breaker: earlier createdAt first
        return a.createdAt.compareTo(b.createdAt)
    }
}
