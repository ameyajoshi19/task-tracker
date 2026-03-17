package com.tasktracker.ui.reschedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RescheduleChange(
    val taskTitle: String,
    val oldStart: Instant?,
    val oldEnd: Instant?,
    val newStart: Instant,
    val newEnd: Instant,
    val isNew: Boolean,
)

data class RescheduleUiState(
    val changes: List<RescheduleChange> = emptyList(),
    val isProcessing: Boolean = false,
    val isDone: Boolean = false,
)

@HiltViewModel
class RescheduleViewModel @Inject constructor(
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val syncManager: CalendarSyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RescheduleUiState())
    val uiState: StateFlow<RescheduleUiState> = _uiState.asStateFlow()

    init {
        loadProposedChanges()
    }

    private fun loadProposedChanges() {
        viewModelScope.launch {
            val proposed = blockRepository.getByStatuses(listOf(BlockStatus.PROPOSED))
            val changes = proposed.map { block ->
                val task = taskRepository.getById(block.taskId)
                val existingBlocks = blockRepository.getByTaskId(block.taskId)
                    .filter { it.status == BlockStatus.CONFIRMED }
                val oldBlock = existingBlocks.firstOrNull()
                RescheduleChange(
                    taskTitle = task?.title ?: "Unknown",
                    oldStart = oldBlock?.startTime,
                    oldEnd = oldBlock?.endTime,
                    newStart = block.startTime,
                    newEnd = block.endTime,
                    isNew = oldBlock == null,
                )
            }
            _uiState.update { it.copy(changes = changes) }
        }
    }

    fun approve() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val proposed = blockRepository.getByStatuses(listOf(BlockStatus.PROPOSED))
            for (block in proposed) {
                val oldBlocks = blockRepository.getByTaskId(block.taskId)
                    .filter { it.status == BlockStatus.CONFIRMED }
                for (old in oldBlocks) {
                    blockRepository.updateStatus(old.id, BlockStatus.CANCELLED)
                    if (old.googleCalendarEventId != null) {
                        syncManager.deleteTaskEvents(old.taskId)
                    }
                }
                blockRepository.updateStatus(block.id, BlockStatus.CONFIRMED)
                taskRepository.updateStatus(block.taskId, TaskStatus.SCHEDULED)
                syncManager.pushNewBlock(block.copy(status = BlockStatus.CONFIRMED))
            }
            _uiState.update { it.copy(isProcessing = false, isDone = true) }
        }
    }

    fun reject() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            blockRepository.deleteProposed()
            _uiState.update { it.copy(isProcessing = false, isDone = true) }
        }
    }
}
