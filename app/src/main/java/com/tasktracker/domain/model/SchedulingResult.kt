package com.tasktracker.domain.model

sealed class SchedulingResult {
    data class Scheduled(
        val blocks: List<ScheduledBlock>,
    ) : SchedulingResult()

    data class NeedsReschedule(
        val newBlocks: List<ScheduledBlock>,
        val movedBlocks: List<Pair<ScheduledBlock, ScheduledBlock>>,
        val displacedTasks: List<Task> = emptyList(),
    ) : SchedulingResult()

    data class DeadlineAtRisk(
        val task: Task,
        val message: String,
    ) : SchedulingResult()

    data class NoSlotsAvailable(
        val task: Task,
        val message: String,
    ) : SchedulingResult()
}
