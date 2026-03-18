package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.*
import java.time.*
import java.time.temporal.ChronoUnit

class TaskScheduler(
    private val priorityComparator: TaskPriorityComparator,
    private val slotFinder: SlotFinder,
) {
    companion object {
        private const val MIN_SPLIT_BLOCK_MINUTES = 30L
        private const val DEADLINE_PRESSURE_THRESHOLD = 0.25
    }

    fun schedule(
        tasks: List<Task>,
        existingBlocks: List<ScheduledBlock>,
        availability: List<UserAvailability>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
    ): SchedulingResult {
        if (tasks.isEmpty()) {
            return SchedulingResult.Scheduled(emptyList())
        }

        val sortedTasks = tasks
            .filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.SCHEDULED }
            .sortedWith(priorityComparator)
            .toMutableList()

        if (sortedTasks.isEmpty()) {
            return SchedulingResult.Scheduled(emptyList())
        }

        // Track remaining duration for splittable tasks
        val remainingMinutes = mutableMapOf<Long, Int>()
        sortedTasks.forEach { remainingMinutes[it.id] = it.estimatedDurationMinutes }

        // Include existing confirmed blocks as busy time
        val allBusySlots = busySlots + existingBlocks
            .filter { it.status == BlockStatus.CONFIRMED || it.status == BlockStatus.COMPLETED }
            .map { TimeSlot(it.startTime, it.endTime) }

        val resultBlocks = mutableListOf<ScheduledBlock>()
        val scheduledTaskIds = mutableSetOf<Long>()

        // Find all available slots across all day preferences (we'll filter per task)
        val allSlots = slotFinder.findAvailableSlots(
            availability = availability,
            busySlots = allBusySlots,
            startDate = startDate,
            endDate = endDate,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        ).toMutableList()

        // Slot-centric best-fit: iterate over slots, fill each with best-fitting task
        val slotsToProcess = allSlots.toMutableList()
        while (slotsToProcess.isNotEmpty() && sortedTasks.any { it.id !in scheduledTaskIds || remainingMinutes.getOrDefault(it.id, 0) > 0 }) {
            val slot = slotsToProcess.removeAt(0)
            val slotMinutes = slot.durationMinutes

            if (slotMinutes < MIN_SPLIT_BLOCK_MINUTES) continue

            val candidateTasks = sortedTasks.filter { task ->
                val remaining = remainingMinutes.getOrDefault(task.id, 0)
                if (remaining <= 0) return@filter false

                // Check day preference
                val slotDay = slot.startTime.atZone(zoneId).dayOfWeek
                if (!matchesDayPreference(slotDay, task.dayPreference)) return@filter false

                // Check deadline
                if (task.deadline != null && slot.startTime >= task.deadline) return@filter false

                true
            }

            // Find highest-priority task that fits
            val bestFit = candidateTasks.firstOrNull { task ->
                val remaining = remainingMinutes[task.id]!!
                if (task.splittable) {
                    true // Can always partially fit if slot >= 30 min
                } else {
                    remaining <= slotMinutes
                }
            } ?: continue

            val remaining = remainingMinutes[bestFit.id]!!
            val blockDuration: Long
            if (bestFit.splittable && remaining > slotMinutes) {
                blockDuration = slotMinutes
            } else {
                blockDuration = remaining.toLong()
            }

            // For deadline tasks, clamp end time
            var blockEnd = slot.startTime.plus(blockDuration, ChronoUnit.MINUTES)
            if (bestFit.deadline != null && blockEnd > bestFit.deadline) {
                val available = Duration.between(slot.startTime, bestFit.deadline).toMinutes()
                if (available < MIN_SPLIT_BLOCK_MINUTES && bestFit.splittable) continue
                if (available < remaining && !bestFit.splittable) continue
                blockEnd = slot.startTime.plus(
                    minOf(available, blockDuration), ChronoUnit.MINUTES
                )
            }

            val actualDuration = Duration.between(slot.startTime, blockEnd).toMinutes()
            if (actualDuration < MIN_SPLIT_BLOCK_MINUTES) continue

            resultBlocks.add(
                ScheduledBlock(
                    taskId = bestFit.id,
                    startTime = slot.startTime,
                    endTime = blockEnd,
                    status = BlockStatus.CONFIRMED,
                )
            )

            remainingMinutes[bestFit.id] = remaining - actualDuration.toInt()
            if (remainingMinutes[bestFit.id]!! <= 0) {
                scheduledTaskIds.add(bestFit.id)
            }

            // If slot has remaining time, add it back
            if (blockEnd < slot.endTime) {
                val remainingSlot = TimeSlot(blockEnd, slot.endTime)
                if (remainingSlot.durationMinutes >= MIN_SPLIT_BLOCK_MINUTES) {
                    slotsToProcess.add(0, remainingSlot)
                }
            }
        }

        // Check for unscheduled tasks
        val unscheduledWithDeadline = sortedTasks.find { task ->
            val remaining = remainingMinutes.getOrDefault(task.id, 0)
            remaining > 0 && task.deadline != null
        }
        if (unscheduledWithDeadline != null) {
            return SchedulingResult.DeadlineAtRisk(
                task = unscheduledWithDeadline,
                message = "Cannot schedule \"${unscheduledWithDeadline.title}\" before its deadline.",
            )
        }

        val unscheduled = sortedTasks.find { task ->
            remainingMinutes.getOrDefault(task.id, 0) > 0
        }
        if (unscheduled != null) {
            val remaining = remainingMinutes[unscheduled.id]!!
            val message = if (!unscheduled.splittable) {
                "No available slot is long enough for \"${unscheduled.title}\" " +
                    "($remaining min). Consider making it splittable or extending " +
                    "an availability window."
            } else {
                "No available slots match the constraints for \"${unscheduled.title}\"."
            }
            if (resultBlocks.isEmpty()) {
                return SchedulingResult.NoSlotsAvailable(
                    task = unscheduled,
                    message = message,
                )
            }
        }

        return SchedulingResult.Scheduled(resultBlocks)
    }

    fun scheduleWithConflictResolution(
        newTask: Task,
        allTasks: List<Task>,
        existingBlocks: List<ScheduledBlock>,
        availability: List<UserAvailability>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
        now: Instant = Instant.now(),
    ): SchedulingResult {
        // First, try to schedule the new task without touching existing blocks
        val directResult = schedule(
            tasks = listOf(newTask),
            existingBlocks = existingBlocks,
            availability = availability,
            busySlots = busySlots,
            startDate = startDate,
            endDate = endDate,
            zoneId = zoneId,
        )
        if (directResult is SchedulingResult.Scheduled && directResult.blocks.isNotEmpty()) {
            // Check deadline pressure — should we try to get an earlier slot?
            val deadline = newTask.deadline
            if (deadline == null) {
                return directResult
            }

            val minutesUntilDeadline = Duration.between(now, deadline).toMinutes()
            val pressure = if (minutesUntilDeadline <= 0) {
                1.0
            } else {
                newTask.estimatedDurationMinutes.toDouble() / minutesUntilDeadline
            }

            if (pressure < DEADLINE_PRESSURE_THRESHOLD) {
                return directResult
            }

            // Pressure is high — try reshuffling lower-priority tasks for an earlier slot
            val lowerPriorityBlocksForPressure = existingBlocks.filter { block ->
                val blockTask = allTasks.find { it.id == block.taskId }
                blockTask != null &&
                    block.status == BlockStatus.CONFIRMED &&
                    priorityComparator.compare(newTask, blockTask) < 0
            }

            if (lowerPriorityBlocksForPressure.isEmpty()) {
                return directResult
            }

            val remainingBlocksForPressure = existingBlocks - lowerPriorityBlocksForPressure.toSet()
            val tasksToRescheduleForPressure = allTasks.filter { task ->
                task.id == newTask.id || lowerPriorityBlocksForPressure.any { it.taskId == task.id }
            }

            val pressureRescheduleResult = schedule(
                tasks = tasksToRescheduleForPressure,
                existingBlocks = remainingBlocksForPressure,
                availability = availability,
                busySlots = busySlots,
                startDate = startDate,
                endDate = endDate,
                zoneId = zoneId,
            )

            // Abandon if reshuffle failed (displaced tasks lost their slots/deadlines)
            if (pressureRescheduleResult !is SchedulingResult.Scheduled) {
                return directResult
            }

            // Compare earliest start time for the new task
            val directEarliest = directResult.blocks
                .filter { it.taskId == newTask.id }
                .minOf { it.startTime }
            val reshuffleNewTaskBlocks = pressureRescheduleResult.blocks
                .filter { it.taskId == newTask.id }
            if (reshuffleNewTaskBlocks.isEmpty()) {
                return directResult
            }
            val reshuffleEarliest = reshuffleNewTaskBlocks.minOf { it.startTime }

            // Only propose reshuffle if it actually gives an earlier start
            if (reshuffleEarliest >= directEarliest) {
                return directResult
            }

            // Build NeedsReschedule with proposed blocks
            val proposedBlocks = pressureRescheduleResult.blocks.map {
                it.copy(status = BlockStatus.PROPOSED)
            }
            val movedPairs = lowerPriorityBlocksForPressure.mapNotNull { oldBlock ->
                val newBlock = proposedBlocks.find { it.taskId == oldBlock.taskId }
                if (newBlock != null) oldBlock to newBlock else null
            }
            val newTaskBlocks = proposedBlocks.filter { it.taskId == newTask.id }

            return SchedulingResult.NeedsReschedule(
                newBlocks = newTaskBlocks,
                movedBlocks = movedPairs,
            )
        }

        // Can't fit — try displacing lower-priority tasks
        val lowerPriorityBlocks = existingBlocks.filter { block ->
            val blockTask = allTasks.find { it.id == block.taskId }
            blockTask != null &&
                block.status == BlockStatus.CONFIRMED &&
                priorityComparator.compare(newTask, blockTask) < 0
        }

        if (lowerPriorityBlocks.isEmpty()) {
            return if (newTask.deadline != null) {
                SchedulingResult.DeadlineAtRisk(newTask, "Cannot fit before deadline.")
            } else {
                SchedulingResult.NoSlotsAvailable(newTask, "No slots available.")
            }
        }

        // Re-run scheduler with lower-priority blocks removed
        val remainingBlocks = existingBlocks - lowerPriorityBlocks.toSet()
        val tasksToReschedule = allTasks.filter { task ->
            task.id == newTask.id || lowerPriorityBlocks.any { it.taskId == task.id }
        }

        val rescheduleResult = schedule(
            tasks = tasksToReschedule,
            existingBlocks = remainingBlocks,
            availability = availability,
            busySlots = busySlots,
            startDate = startDate,
            endDate = endDate,
            zoneId = zoneId,
        )

        return when (rescheduleResult) {
            is SchedulingResult.Scheduled -> {
                val proposedBlocks = rescheduleResult.blocks.map {
                    it.copy(status = BlockStatus.PROPOSED)
                }
                val movedPairs = lowerPriorityBlocks.mapNotNull { oldBlock ->
                    val newBlock = proposedBlocks.find { it.taskId == oldBlock.taskId }
                    if (newBlock != null) oldBlock to newBlock else null
                }
                val newTaskBlocks = proposedBlocks.filter { it.taskId == newTask.id }
                SchedulingResult.NeedsReschedule(
                    newBlocks = newTaskBlocks,
                    movedBlocks = movedPairs,
                )
            }
            else -> rescheduleResult
        }
    }

    private fun matchesDayPreference(
        dayOfWeek: DayOfWeek,
        preference: DayPreference,
    ): Boolean {
        val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
        return when (preference) {
            DayPreference.WEEKDAY -> !isWeekend
            DayPreference.WEEKEND -> isWeekend
            DayPreference.ANY -> true
        }
    }
}
