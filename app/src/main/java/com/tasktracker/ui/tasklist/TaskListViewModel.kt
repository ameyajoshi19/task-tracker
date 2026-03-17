package com.tasktracker.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<Task>> = emptyMap(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val syncManager: CalendarSyncManager,
) : ViewModel() {

    val uiState: StateFlow<TaskListUiState> = taskRepository.observeAll()
        .map { tasks ->
            TaskListUiState(
                tasksByQuadrant = tasks.groupBy { it.quadrant },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TaskListUiState(),
        )

    fun completeTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateStatus(task.id, TaskStatus.COMPLETED)
            syncManager.markTaskCompleted(task.id)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            syncManager.deleteTaskEvents(task.id)
            taskRepository.delete(task)
        }
    }
}
