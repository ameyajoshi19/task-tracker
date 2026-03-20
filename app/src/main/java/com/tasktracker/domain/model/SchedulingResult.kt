package com.tasktracker.domain.model

/** Represents the outcome of a scheduling or conflict-resolution attempt. */
sealed class SchedulingResult {
    /** All requested tasks were placed successfully; [blocks] contains the confirmed assignments. */
    data class Scheduled(
        val blocks: List<ScheduledBlock>,
    ) : SchedulingResult()

    /**
     * One or more existing blocks must move to accommodate a new or higher-priority task.
     * All blocks in [newBlocks] and [movedBlocks] carry [BlockStatus.PROPOSED] and require
     * explicit user confirmation before being committed to the calendar.
     *
     * @property newBlocks Proposed blocks for the task being newly inserted.
     * @property movedBlocks Pairs of (original block → proposed replacement block) for displaced tasks.
     * @property displacedTasks Tasks that could not be re-placed anywhere after being displaced.
     */
    data class NeedsReschedule(
        val newBlocks: List<ScheduledBlock>,
        val movedBlocks: List<Pair<ScheduledBlock, ScheduledBlock>>,
        val displacedTasks: List<Task> = emptyList(),
    ) : SchedulingResult()

    /** A task with a hard deadline could not be placed before that deadline expires. */
    data class DeadlineAtRisk(
        val task: Task,
        val message: String,
    ) : SchedulingResult()

    /** No availability window exists that can accommodate the task given its current constraints. */
    data class NoSlotsAvailable(
        val task: Task,
        val message: String,
    ) : SchedulingResult()
}
