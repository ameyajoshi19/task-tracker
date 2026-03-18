# Scheduling Fixes & Task UX Improvements — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix scheduling conflict resolution so higher-priority tasks displace lower-priority ones, make task saves atomic, add swipe-to-delete/reschedule gestures, show scheduled time and deadline on task cards, and add a "Due Today" urgency section.

**Architecture:** Domain layer changes to `TaskScheduler` fix the displacement algorithm. ViewModel changes make saves atomic (schedule-before-persist). UI layer adds `SwipeToDismissBox` gestures, enriches `TaskCard` with block/deadline info, and adds a "Due Today" section. A new `TaskWithScheduleInfo` domain model bridges the joined Room query to the UI.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 `SwipeToDismissBox`), Room, Hilt, Google Calendar API via `CalendarSyncManager`.

**Spec:** `docs/superpowers/specs/2026-03-18-scheduling-fixes-and-task-ux.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `domain/scheduler/TaskScheduler.kt` | Modify | Fix displacement path in `scheduleWithConflictResolution` |
| `domain/model/SchedulingResult.kt` | Modify | Add `displacedTasks` field to `NeedsReschedule` for tasks that couldn't be rescheduled |
| `test/.../scheduler/TaskSchedulerTest.kt` | Modify | Add displacement scenario tests |
| `ui/taskedit/TaskEditViewModel.kt` | Modify | Atomic save flow, fix NeedsReschedule block mapping |
| `domain/model/TaskWithScheduleInfo.kt` | Create | Domain model pairing a Task with its next block start/end |
| `data/local/dao/TaskDao.kt` | Modify | Add joined query for tasks with next block |
| `domain/repository/TaskRepository.kt` | Modify | Add `observeAllWithScheduleInfo()` |
| `data/repository/TaskRepositoryImpl.kt` | Modify | Implement joined query mapping |
| `ui/tasklist/TaskListViewModel.kt` | Modify | Switch to `TaskWithScheduleInfo`, add `dueTodayTasks`, add `rescheduleTask()` |
| `ui/components/TaskCard.kt` | Modify | Show scheduled time and deadline rows |
| `ui/tasklist/TaskListScreen.kt` | Modify | Swipe gestures, delete confirmation dialog, "Due Today" section |
| `ui/theme/Color.kt` | Modify | Add `deadlineWarning` color |

---

## Task 1: Fix Conflict Resolution — Displacement Path

**Files:**
- Modify: `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt:293-342`
- Modify: `app/src/main/java/com/tasktracker/domain/model/SchedulingResult.kt:8-11`
- Test: `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt`

- [ ] **Step 1: Write failing test — high-priority task displaces lower-priority task**

In `TaskSchedulerTest.kt`, add:

```kotlin
@Test
fun `scheduleWithConflictResolution displaces lower-priority task when no slots available`() {
    // Availability: 6-8 PM on Monday only (2 hours)
    val avail = availability(day = DayOfWeek.MONDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))
    val tuesdayAvail = availability(day = DayOfWeek.TUESDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))

    // Existing: 2h Important task fills the entire window
    val existingTask = task(id = 1, duration = 120, quadrant = Quadrant.IMPORTANT)
    val existingBlock = ScheduledBlock(
        id = 1,
        taskId = 1,
        startTime = monday.atTime(18, 0).atZone(zoneId).toInstant(),
        endTime = monday.atTime(20, 0).atZone(zoneId).toInstant(),
        status = BlockStatus.CONFIRMED,
    )

    // New: 15m Urgent & Important task with deadline today
    val deadlineTonight = monday.atTime(23, 59).atZone(zoneId).toInstant()
    val newTask = task(id = 2, duration = 15, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadlineTonight)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(existingTask, newTask),
        existingBlocks = listOf(existingBlock),
        availability = listOf(avail, tuesdayAvail),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday.plusDays(7),
        zoneId = zoneId,
        now = testNow,
    )

    assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
    val reschedule = result as SchedulingResult.NeedsReschedule
    // New task should have a block
    assertThat(reschedule.newBlocks).hasSize(1)
    assertThat(reschedule.newBlocks[0].taskId).isEqualTo(2L)
    // The new task block should be on Monday (before deadline)
    val newBlockDay = reschedule.newBlocks[0].startTime.atZone(zoneId).toLocalDate()
    assertThat(newBlockDay).isEqualTo(monday)
    // Displaced task should have a moved block pair
    assertThat(reschedule.movedBlocks).hasSize(1)
    assertThat(reschedule.movedBlocks[0].first.taskId).isEqualTo(1L)
    assertThat(reschedule.movedBlocks[0].second.taskId).isEqualTo(1L)
}
```

- [ ] **Step 2: Write failing test — displaced task with deadline that can't be met still allows new task**

```kotlin
@Test
fun `scheduleWithConflictResolution schedules new task even when displaced task loses its deadline`() {
    val avail = availability(day = DayOfWeek.MONDAY, start = LocalTime.of(18, 0), end = LocalTime.of(20, 0))

    // Existing: 2h Important task with deadline tonight (can't be moved)
    val deadlineTonight = monday.atTime(23, 59).atZone(zoneId).toInstant()
    val existingTask = task(id = 1, duration = 120, quadrant = Quadrant.IMPORTANT, deadline = deadlineTonight)
    val existingBlock = ScheduledBlock(
        id = 1,
        taskId = 1,
        startTime = monday.atTime(18, 0).atZone(zoneId).toInstant(),
        endTime = monday.atTime(20, 0).atZone(zoneId).toInstant(),
        status = BlockStatus.CONFIRMED,
    )

    // New: 15m Urgent & Important task with same deadline
    val newTask = task(id = 2, duration = 15, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadlineTonight)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(existingTask, newTask),
        existingBlocks = listOf(existingBlock),
        availability = listOf(avail),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday, // Only Monday available
        zoneId = zoneId,
        now = testNow,
    )

    // Should still schedule the new task — displaced task is collateral but new task must go in
    assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
    val reschedule = result as SchedulingResult.NeedsReschedule
    assertThat(reschedule.newBlocks).hasSize(1)
    assertThat(reschedule.newBlocks[0].taskId).isEqualTo(2L)
    // Displaced task may have no new block (couldn't fit in remaining time with its deadline)
    assertThat(reschedule.displacedTasks).hasSize(1)
    assertThat(reschedule.displacedTasks[0].id).isEqualTo(1L)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest" -x lint`
Expected: Two new tests FAIL (compilation error for `displacedTasks` field, and wrong result type)

- [ ] **Step 4: Update `SchedulingResult.NeedsReschedule` to include `displacedTasks`**

In `app/src/main/java/com/tasktracker/domain/model/SchedulingResult.kt`, change:

```kotlin
data class NeedsReschedule(
    val newBlocks: List<ScheduledBlock>,
    val movedBlocks: List<Pair<ScheduledBlock, ScheduledBlock>>,
    val displacedTasks: List<Task> = emptyList(),
) : SchedulingResult()
```

The `displacedTasks` field lists tasks whose blocks were removed but could not be rescheduled within the window. Default to empty for backward compatibility.

- [ ] **Step 5: Fix the displacement path in `scheduleWithConflictResolution`**

Replace lines 293-342 of `TaskScheduler.kt` with:

```kotlin
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

// Step 1: Schedule the new task alone with lower-priority blocks removed
val remainingBlocks = existingBlocks - lowerPriorityBlocks.toSet()
val newTaskResult = schedule(
    tasks = listOf(newTask),
    existingBlocks = remainingBlocks,
    availability = availability,
    busySlots = busySlots,
    startDate = startDate,
    endDate = endDate,
    zoneId = zoneId,
    now = now,
)

if (newTaskResult !is SchedulingResult.Scheduled || newTaskResult.blocks.isEmpty()) {
    // Even with freed slots, can't fit — return original error
    return if (newTask.deadline != null) {
        SchedulingResult.DeadlineAtRisk(newTask, "Cannot schedule \"${newTask.title}\" before its deadline.")
    } else {
        SchedulingResult.NoSlotsAvailable(newTask, "No slots available for \"${newTask.title}\".")
    }
}

// Step 2: Reschedule displaced tasks around the new task's blocks
val newTaskBlocks = newTaskResult.blocks
val blocksForDisplaced = remainingBlocks + newTaskBlocks.map {
    it.copy(status = BlockStatus.CONFIRMED)
}
val displacedTaskIds = lowerPriorityBlocks.map { it.taskId }.toSet()
val displacedTasks = allTasks.filter { it.id in displacedTaskIds }

val displacedResult = schedule(
    tasks = displacedTasks,
    existingBlocks = blocksForDisplaced,
    availability = availability,
    busySlots = busySlots,
    startDate = startDate,
    endDate = endDate,
    zoneId = zoneId,
    now = now,
)

// Build result: new task always gets scheduled; displaced tasks may or may not
val proposedNewBlocks = newTaskBlocks.map { it.copy(status = BlockStatus.PROPOSED) }
val movedPairs: List<Pair<ScheduledBlock, ScheduledBlock>>
val unscheduledDisplaced: List<Task>

when (displacedResult) {
    is SchedulingResult.Scheduled -> {
        val displacedBlocks = displacedResult.blocks.map { it.copy(status = BlockStatus.PROPOSED) }
        movedPairs = lowerPriorityBlocks.mapNotNull { oldBlock ->
            val newBlock = displacedBlocks.find { it.taskId == oldBlock.taskId }
            if (newBlock != null) oldBlock to newBlock else null
        }
        val rescheduledIds = displacedBlocks.map { it.taskId }.toSet()
        unscheduledDisplaced = displacedTasks.filter { it.id !in rescheduledIds }
    }
    else -> {
        // Some or all displaced tasks couldn't be rescheduled
        movedPairs = emptyList()
        unscheduledDisplaced = displacedTasks
    }
}

return SchedulingResult.NeedsReschedule(
    newBlocks = proposedNewBlocks,
    movedBlocks = movedPairs,
    displacedTasks = unscheduledDisplaced,
)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest" -x lint`
Expected: All tests PASS (including existing tests)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt \
       app/src/main/java/com/tasktracker/domain/model/SchedulingResult.kt \
       app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt
git commit -m "fix: displacement path schedules new task first, handles partial reschedule"
```

---

## Task 2: Atomic Save — Schedule Before Persist

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt:136-249`

- [ ] **Step 1: Restructure `save()` — schedule before persisting**

Replace the `save()` method (lines 136-249) in `TaskEditViewModel.kt` with:

```kotlin
fun save() {
    val state = _uiState.value
    if (state.title.isBlank()) {
        _uiState.update { it.copy(validationError = "Title is required.") }
        return
    }

    viewModelScope.launch {
        _uiState.update { it.copy(isSaving = true) }

        val task = Task(
            id = if (taskId != -1L) taskId else 0,
            title = state.title.trim(),
            description = state.description.trim(),
            estimatedDurationMinutes = state.durationMinutes,
            quadrant = state.quadrant,
            deadline = state.deadline,
            dayPreference = state.dayPreference,
            splittable = state.splittable,
        )

        val availability = availabilityRepository.getAll()
        val validation = taskValidator.validate(task, availability)
        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(validationError = validation.reason, isSaving = false) }
            return@launch
        }

        // Gather scheduling inputs BEFORE persisting
        val allTasks = taskRepository.getByStatuses(
            listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
        )
        val existingBlocks = blockRepository.getByStatuses(
            listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
        )
        val enabledCalendars = calendarSelectionRepository.getEnabled()
        val busySlots = try {
            val now = Instant.now()
            val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
            calendarRepository.getFreeBusySlots(
                calendarIds = enabledCalendars.map { it.googleCalendarId },
                timeMin = now,
                timeMax = twoWeeksLater,
            )
        } catch (_: Exception) {
            emptyList()
        }

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val isEditing = savedTaskId != -1L

        // For edits, exclude the task's own old blocks from existingBlocks
        val blocksForScheduling = if (isEditing) {
            existingBlocks.filter { it.taskId != savedTaskId }
        } else {
            existingBlocks
        }

        // Run scheduler with in-memory task (not yet persisted)
        val result = taskScheduler.scheduleWithConflictResolution(
            newTask = task,
            allTasks = allTasks,
            existingBlocks = blocksForScheduling,
            availability = availability,
            busySlots = busySlots,
            startDate = today,
            endDate = today.plusDays(14),
            zoneId = zoneId,
            now = Instant.now(),
        )

        appPreferences.setLastSyncTimestamp(Instant.now())

        when (result) {
            is SchedulingResult.Scheduled -> {
                // Scheduling succeeded — now persist
                val savedId = if (isEditing) {
                    syncManager.deleteTaskEvents(savedTaskId)
                    blockRepository.deleteByTaskId(savedTaskId)
                    taskRepository.update(task.copy(id = savedTaskId))
                    savedTaskId
                } else {
                    val id = taskRepository.insert(task)
                    savedTaskId = id
                    id
                }
                val blocksWithTaskId = result.blocks.map { it.copy(taskId = savedId) }
                val insertedIds = blockRepository.insertAll(blocksWithTaskId)
                taskRepository.updateStatus(savedId, TaskStatus.SCHEDULED)
                blocksWithTaskId.zip(insertedIds).forEach { (block, id) ->
                    syncManager.pushNewBlock(block.copy(id = id))
                }
                _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
            }
            is SchedulingResult.NeedsReschedule -> {
                // Persist task and blocks
                val savedId = if (isEditing) {
                    syncManager.deleteTaskEvents(savedTaskId)
                    blockRepository.deleteByTaskId(savedTaskId)
                    taskRepository.update(task.copy(id = savedTaskId))
                    savedTaskId
                } else {
                    val id = taskRepository.insert(task)
                    savedTaskId = id
                    id
                }
                // Only remap newBlocks to saved ID; movedBlocks keep their original taskId
                val newBlocks = result.newBlocks.map { it.copy(taskId = savedId) }
                val movedBlocks = result.movedBlocks.map { it.second }
                blockRepository.insertAll(newBlocks + movedBlocks)
                _uiState.update {
                    it.copy(schedulingResult = result, isSaving = false)
                }
            }
            else -> {
                // Scheduling failed — do NOT persist
                _uiState.update {
                    it.copy(
                        schedulingResult = result,
                        savedSuccessfully = false,
                        isSaving = false,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `./gradlew assembleDebug -x lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "fix: atomic save — schedule before persist, fix NeedsReschedule block mapping"
```

---

## Task 3: Data Layer — Tasks with Schedule Info

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/TaskWithScheduleInfo.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/dao/TaskDao.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/repository/TaskRepository.kt`
- Modify: `app/src/main/java/com/tasktracker/data/repository/TaskRepositoryImpl.kt`

- [ ] **Step 1: Create the domain model**

Create `app/src/main/java/com/tasktracker/domain/model/TaskWithScheduleInfo.kt`:

```kotlin
package com.tasktracker.domain.model

import java.time.Instant

data class TaskWithScheduleInfo(
    val task: Task,
    val nextBlockStart: Instant? = null,
    val nextBlockEnd: Instant? = null,
    val blockCount: Int = 0,
)
```

- [ ] **Step 2: Add joined query to `TaskDao`**

Add to `app/src/main/java/com/tasktracker/data/local/dao/TaskDao.kt`:

```kotlin
@Query("""
    SELECT t.*,
           sb.startTime AS nextBlockStart,
           sb.endTime AS nextBlockEnd,
           (SELECT COUNT(*) FROM scheduled_blocks sb3
            WHERE sb3.taskId = t.id AND sb3.status = 'CONFIRMED') AS blockCount
    FROM tasks t
    LEFT JOIN scheduled_blocks sb
        ON sb.taskId = t.id AND sb.status = 'CONFIRMED'
        AND sb.startTime = (
            SELECT MIN(sb2.startTime) FROM scheduled_blocks sb2
            WHERE sb2.taskId = t.id AND sb2.status = 'CONFIRMED'
        )
    ORDER BY t.createdAt DESC
""")
fun observeAllWithNextBlock(): Flow<List<TaskWithNextBlockTuple>>
```

NOTE: The subquery ensures `nextBlockStart` and `nextBlockEnd` come from the same row (the earliest confirmed block). The `blockCount` correlated subquery enables "(+1 more)" display for splittable tasks.

Also add the data class at the top of the file (or in the same package):

```kotlin
data class TaskWithNextBlockTuple(
    val id: Long,
    val title: String,
    val description: String,
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant?,
    val dayPreference: DayPreference,
    val splittable: Boolean,
    val status: TaskStatus,
    val recurringPattern: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextBlockStart: Instant?,
    val nextBlockEnd: Instant?,
    val blockCount: Int,
)
```

NOTE: Room maps column names to constructor params by name. The task columns come from `t.*` and the block columns from the aliases. Since `TaskWithNextBlockTuple` is NOT an `@Entity`, it does not need `@Embedded` or `@Relation` — Room treats it as a plain POJO projection. Put this class in a new file `app/src/main/java/com/tasktracker/data/local/dao/TaskWithNextBlockTuple.kt`.

- [ ] **Step 3: Add repository interface method**

In `app/src/main/java/com/tasktracker/domain/repository/TaskRepository.kt`, add:

```kotlin
fun observeAllWithScheduleInfo(): Flow<List<TaskWithScheduleInfo>>
```

Add the import: `import com.tasktracker.domain.model.TaskWithScheduleInfo`

- [ ] **Step 4: Implement in `TaskRepositoryImpl`**

In `app/src/main/java/com/tasktracker/data/repository/TaskRepositoryImpl.kt`, add:

```kotlin
override fun observeAllWithScheduleInfo(): Flow<List<TaskWithScheduleInfo>> =
    taskDao.observeAllWithNextBlock().map { tuples ->
        tuples.map { t ->
            TaskWithScheduleInfo(
                task = Task(
                    id = t.id,
                    title = t.title,
                    description = t.description,
                    estimatedDurationMinutes = t.estimatedDurationMinutes,
                    quadrant = t.quadrant,
                    deadline = t.deadline,
                    dayPreference = t.dayPreference,
                    splittable = t.splittable,
                    status = t.status,
                    recurringPattern = t.recurringPattern,
                    createdAt = t.createdAt,
                    updatedAt = t.updatedAt,
                ),
                nextBlockStart = t.nextBlockStart,
                nextBlockEnd = t.nextBlockEnd,
                blockCount = t.blockCount,
            )
        }
    }
```

Add imports: `import com.tasktracker.domain.model.TaskWithScheduleInfo`

- [ ] **Step 5: Verify the build compiles**

Run: `./gradlew assembleDebug -x lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/TaskWithScheduleInfo.kt \
       app/src/main/java/com/tasktracker/data/local/dao/TaskDao.kt \
       app/src/main/java/com/tasktracker/data/local/dao/TaskWithNextBlockTuple.kt \
       app/src/main/java/com/tasktracker/domain/repository/TaskRepository.kt \
       app/src/main/java/com/tasktracker/data/repository/TaskRepositoryImpl.kt
git commit -m "feat: add TaskWithScheduleInfo and joined Room query for task+block data"
```

---

## Task 4: TaskListViewModel — Use Schedule Info, Add Due Today, Add Reschedule

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt`

- [ ] **Step 1: Update `TaskListUiState` and ViewModel**

Replace the full contents of `TaskListViewModel.kt`:

```kotlin
package com.tasktracker.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.CalendarRepository
import com.tasktracker.domain.repository.CalendarSelectionRepository
import com.tasktracker.domain.repository.ScheduledBlockRepository
import com.tasktracker.domain.repository.TaskRepository
import com.tasktracker.domain.repository.UserAvailabilityRepository
import com.tasktracker.domain.scheduler.TaskScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TaskListUiState(
    val tasksByQuadrant: Map<Quadrant, List<TaskWithScheduleInfo>> = emptyMap(),
    val completedTasks: List<TaskWithScheduleInfo> = emptyList(),
    val dueTodayTasks: List<TaskWithScheduleInfo> = emptyList(),
    val isLoading: Boolean = true,
    val rescheduleError: String? = null,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val syncManager: CalendarSyncManager,
    private val availabilityRepository: UserAvailabilityRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val calendarRepository: CalendarRepository,
    private val taskScheduler: TaskScheduler,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _rescheduleError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeAllWithScheduleInfo(),
        _rescheduleError,
    ) { tasks, rescheduleErr ->
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val active = tasks.filter { it.task.status != TaskStatus.COMPLETED }
        val completed = tasks.filter { it.task.status == TaskStatus.COMPLETED }

        val dueToday = active.filter { info ->
            info.task.deadline?.let { deadline ->
                deadline.atZone(zoneId).toLocalDate() == today
            } ?: false
        }

        TaskListUiState(
            tasksByQuadrant = active.groupBy { it.task.quadrant },
            completedTasks = completed,
            dueTodayTasks = dueToday,
            isLoading = false,
            rescheduleError = rescheduleErr,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaskListUiState(),
    )

    fun completeTask(task: Task) {
        viewModelScope.launch {
            taskRepository.updateStatus(task.id, TaskStatus.COMPLETED)
            val blocks = blockRepository.getByTaskId(task.id)
            for (block in blocks) {
                blockRepository.updateStatus(block.id, BlockStatus.COMPLETED)
            }
            syncManager.markTaskCompleted(task.id)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            syncManager.deleteTaskEvents(task.id)
            taskRepository.delete(task)
        }
    }

    fun rescheduleTask(taskId: Long) {
        viewModelScope.launch {
            _rescheduleError.value = null

            val task = taskRepository.getById(taskId) ?: return@launch
            val oldBlocks = blockRepository.getByTaskId(taskId)
                .filter { it.status == BlockStatus.CONFIRMED }

            if (oldBlocks.isEmpty()) return@launch

            // Old block times become additional busy slots to prevent same-slot assignment
            val blockedSlots = oldBlocks.map { TimeSlot(it.startTime, it.endTime) }

            val availability = availabilityRepository.getAll()
            val existingBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
            ).filter { it.taskId != taskId } // Exclude this task's blocks
            val allTasks = taskRepository.getByStatuses(
                listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
            )
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val busySlots = try {
                val now = Instant.now()
                calendarRepository.getFreeBusySlots(
                    calendarIds = enabledCalendars.map { it.googleCalendarId },
                    timeMin = now,
                    timeMax = now.plusSeconds(14 * 24 * 3600),
                )
            } catch (_: Exception) {
                emptyList()
            }

            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val taskForScheduling = task.copy(status = TaskStatus.PENDING)

            val result = taskScheduler.scheduleWithConflictResolution(
                newTask = taskForScheduling,
                allTasks = allTasks,
                existingBlocks = existingBlocks,
                availability = availability,
                busySlots = busySlots + blockedSlots,
                startDate = today,
                endDate = today.plusDays(14),
                zoneId = zoneId,
                now = Instant.now(),
            )

            appPreferences.setLastSyncTimestamp(Instant.now())

            when (result) {
                is SchedulingResult.Scheduled -> {
                    // Success: delete old, insert new
                    syncManager.deleteTaskEvents(taskId)
                    blockRepository.deleteByTaskId(taskId)
                    val newBlocks = result.blocks.map { it.copy(taskId = taskId) }
                    val insertedIds = blockRepository.insertAll(newBlocks)
                    taskRepository.updateStatus(taskId, TaskStatus.SCHEDULED)
                    newBlocks.zip(insertedIds).forEach { (block, id) ->
                        syncManager.pushNewBlock(block.copy(id = id))
                    }
                }
                is SchedulingResult.NeedsReschedule -> {
                    // Accept the reschedule
                    syncManager.deleteTaskEvents(taskId)
                    blockRepository.deleteByTaskId(taskId)
                    val newBlocks = result.newBlocks.map { it.copy(taskId = taskId) }
                    val movedBlocks = result.movedBlocks.map { it.second }
                    blockRepository.insertAll(newBlocks + movedBlocks)
                    taskRepository.updateStatus(taskId, TaskStatus.SCHEDULED)
                    newBlocks.forEach { syncManager.pushNewBlock(it) }
                }
                is SchedulingResult.DeadlineAtRisk -> {
                    _rescheduleError.value = result.message
                }
                is SchedulingResult.NoSlotsAvailable -> {
                    _rescheduleError.value = result.message
                }
            }
        }
    }

    fun clearRescheduleError() {
        _rescheduleError.value = null
    }
}
```

- [ ] **Step 2: Note: build will NOT compile at this point**

`TaskListScreen.kt` still references the old `TaskListUiState` types (`List<Task>` vs `List<TaskWithScheduleInfo>`) and old `TaskCard` signature. This is expected and will be fixed in Tasks 5 and 6. Skip build verification for this task.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt
git commit -m "feat: TaskListViewModel uses TaskWithScheduleInfo, adds due today + reschedule"
```

---

## Task 5: TaskCard — Show Scheduled Time and Deadline

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/TaskCard.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/theme/Color.kt`

- [ ] **Step 1: Add `deadlineWarning` color to `SortdColors`**

In `app/src/main/java/com/tasktracker/ui/theme/Color.kt`, add after line 8 (`accentLight`):

```kotlin
val deadlineWarning = Color(0xFFEF4444)
```

- [ ] **Step 2: Update `TaskCard` to accept and display schedule info**

Replace `app/src/main/java/com/tasktracker/ui/components/TaskCard.kt` with:

```kotlin
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.ui.theme.SortdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskCard(
    taskInfo: TaskWithScheduleInfo,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = taskInfo.task
    val isCompleted = task.status == TaskStatus.COMPLETED
    val (colorStart, colorEnd) = quadrantColors(task.quadrant)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.45f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gradient quadrant dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(12.dp))

        // Task info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Scheduled time
            if (taskInfo.nextBlockStart != null && taskInfo.nextBlockEnd != null) {
                val extra = if (taskInfo.blockCount > 1) " (+${taskInfo.blockCount - 1} more)" else ""
                Text(
                    text = formatScheduledTime(taskInfo.nextBlockStart, taskInfo.nextBlockEnd) + extra,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            } else if (!isCompleted) {
                Text(
                    text = "Not scheduled",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }

            // Deadline
            if (task.deadline != null) {
                val isUrgent = isDeadlineUrgent(task.deadline)
                Text(
                    text = formatDeadline(task.deadline),
                    fontSize = 11.sp,
                    color = if (isUrgent) SortdColors.deadlineWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // Duration badge
                Text(
                    text = formatDuration(task.estimatedDurationMinutes),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorStart,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorStart.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // Check button
        if (!isCompleted) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.12f))
                    .border(1.5.dp, colorStart.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onComplete),
                contentAlignment = Alignment.Center,
            ) {
                Text("○", fontSize = 14.sp, color = colorStart)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Completed",
                    modifier = Modifier.size(16.dp),
                    tint = colorStart,
                )
            }
        }
    }
}

fun quadrantColors(quadrant: Quadrant): Pair<Color, Color> = when (quadrant) {
    Quadrant.URGENT_IMPORTANT -> SortdColors.nowStart to SortdColors.nowEnd
    Quadrant.IMPORTANT -> SortdColors.nextStart to SortdColors.nextEnd
    Quadrant.URGENT -> SortdColors.soonStart to SortdColors.soonEnd
    Quadrant.NEITHER -> SortdColors.laterStart to SortdColors.laterEnd
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatScheduledTime(start: Instant, end: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val startZoned = start.atZone(zoneId)
    val endZoned = end.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val startDate = startZoned.toLocalDate()

    val datePrefix = when {
        startDate == today -> "Today"
        startDate == today.plusDays(1) -> "Tomorrow"
        else -> startDate.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    return "$datePrefix, ${startZoned.format(timeFormatter)} - ${endZoned.format(timeFormatter)}"
}

private fun formatDeadline(deadline: Instant): String {
    val zoneId = ZoneId.systemDefault()
    val deadlineZoned = deadline.atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val deadlineDate = deadlineZoned.toLocalDate()

    return when {
        deadlineDate.isBefore(today) -> "Overdue"
        deadlineDate == today -> "Due today, ${deadlineZoned.format(timeFormatter)}"
        deadlineDate == today.plusDays(1) -> "Due tomorrow"
        else -> "Due ${deadlineDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
}

private fun isDeadlineUrgent(deadline: Instant): Boolean {
    val zoneId = ZoneId.systemDefault()
    val deadlineDate = deadline.atZone(zoneId).toLocalDate()
    val today = LocalDate.now(zoneId)
    return !deadlineDate.isAfter(today)
}
```

- [ ] **Step 3: Verify the build compiles**

Run: `./gradlew assembleDebug -x lint`
Expected: Compilation errors in `TaskListScreen.kt` (expected — it still passes `Task` instead of `TaskWithScheduleInfo`). These are fixed in Task 6.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/TaskCard.kt \
       app/src/main/java/com/tasktracker/ui/theme/Color.kt
git commit -m "feat: TaskCard shows scheduled time and deadline, add deadlineWarning color"
```

---

## Task 6: TaskListScreen — Swipe Gestures, Delete Dialog, Due Today Section

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt`

- [ ] **Step 1: Rewrite `TaskListScreen` with swipe gestures, delete dialog, and Due Today section**

Replace the full contents of `TaskListScreen.kt`:

```kotlin
package com.tasktracker.ui.tasklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.model.TaskWithScheduleInfo
import com.tasktracker.ui.components.TaskCard
import com.tasktracker.ui.components.quadrantColors
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var taskToDelete by remember { mutableStateOf<TaskWithScheduleInfo?>(null) }

    // Snackbar for reschedule errors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.rescheduleError) {
        uiState.rescheduleError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRescheduleError()
        }
    }

    // Delete confirmation dialog
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete task?") },
            text = { Text("Delete \"${taskToDelete!!.task.title}\"? This will also remove the calendar event.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(taskToDelete!!.task)
                    taskToDelete = null
                }) {
                    Text("Delete", color = SortdColors.deadlineWarning)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("sortd", style = MaterialTheme.typography.headlineMedium)
                        Text(".", style = MaterialTheme.typography.headlineMedium, color = SortdColors.accent)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(Icons.Default.CalendarMonth, "Schedule", tint = SortdColors.accent)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                shape = RoundedCornerShape(16.dp),
                containerColor = SortdColors.accent,
                contentColor = Color.White,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Add, "Add task")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SortdColors.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Due Today section
                if (uiState.dueTodayTasks.isNotEmpty()) {
                    item { DueTodayHeader(uiState.dueTodayTasks.size) }
                    items(uiState.dueTodayTasks, key = { "due-${it.task.id}" }) { taskInfo ->
                        SwipeableTaskCard(
                            taskInfo = taskInfo,
                            onEdit = { onEditTask(taskInfo.task.id) },
                            onComplete = { viewModel.completeTask(taskInfo.task) },
                            onDelete = { taskToDelete = taskInfo },
                            onReschedule = { viewModel.rescheduleTask(taskInfo.task.id) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Quadrant groups
                val quadrantOrder = listOf(
                    Quadrant.URGENT_IMPORTANT,
                    Quadrant.IMPORTANT,
                    Quadrant.URGENT,
                    Quadrant.NEITHER,
                )
                for (quadrant in quadrantOrder) {
                    val tasks = uiState.tasksByQuadrant[quadrant] ?: continue
                    item { QuadrantHeader(quadrant, tasks.size) }
                    items(tasks, key = { it.task.id }) { taskInfo ->
                        SwipeableTaskCard(
                            taskInfo = taskInfo,
                            onEdit = { onEditTask(taskInfo.task.id) },
                            onComplete = { viewModel.completeTask(taskInfo.task) },
                            onDelete = { taskToDelete = taskInfo },
                            onReschedule = { viewModel.rescheduleTask(taskInfo.task.id) },
                        )
                    }
                }

                // Completed section
                if (uiState.completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "Completed · ${uiState.completedTasks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                letterSpacing = 1.sp,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(uiState.completedTasks, key = { "done-${it.task.id}" }) { taskInfo ->
                        SwipeableTaskCard(
                            taskInfo = taskInfo,
                            onEdit = { onEditTask(taskInfo.task.id) },
                            onComplete = { },
                            onDelete = { taskToDelete = taskInfo },
                            onReschedule = null, // No reschedule for completed
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    taskInfo: TaskWithScheduleInfo,
    onEdit: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: (() -> Unit)?,
) {
    val isScheduled = taskInfo.task.status == TaskStatus.SCHEDULED
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false // Don't dismiss — dialog handles it
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isScheduled && onReschedule != null) {
                        onReschedule()
                    }
                    false // Snap back after triggering
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> SortdColors.deadlineWarning.copy(alpha = 0.2f)
                    SwipeToDismissBoxValue.StartToEnd -> SortdColors.accent.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                label = "swipe-bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
            ) {
                // Left side — reschedule icon
                if (direction == SwipeToDismissBoxValue.StartToEnd && isScheduled) {
                    Icon(
                        Icons.Default.EventRepeat,
                        contentDescription = "Reschedule",
                        tint = SortdColors.accent,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                // Right side — delete icon
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = SortdColors.deadlineWarning,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        },
        enableDismissFromStartToEnd = isScheduled && onReschedule != null,
        enableDismissFromEndToStart = true,
    ) {
        TaskCard(
            taskInfo = taskInfo,
            onClick = onEdit,
            onComplete = onComplete,
        )
    }
}

@Composable
private fun DueTodayHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SortdColors.deadlineWarning.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = SortdColors.deadlineWarning,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Due Today",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SortdColors.deadlineWarning,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = SortdColors.deadlineWarning,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SortdColors.deadlineWarning.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun QuadrantHeader(quadrant: Quadrant, count: Int) {
    val (colorStart, colorEnd) = quadrantColors(quadrant)
    val (icon, label) = when (quadrant) {
        Quadrant.URGENT_IMPORTANT -> "⚡" to "Now"
        Quadrant.IMPORTANT -> "🎯" to "Next"
        Quadrant.URGENT -> "🔄" to "Soon"
        Quadrant.NEITHER -> "📦" to "Later"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$icon $label",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colorStart,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colorStart,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colorStart.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
```

- [ ] **Step 2: Verify the full build compiles**

Run: `./gradlew assembleDebug -x lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all existing tests**

Run: `./gradlew test -x lint`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
git commit -m "feat: swipe-to-delete with confirmation, swipe-to-reschedule, Due Today section"
```

---

## Task 7: Verify End-to-End & Final Commit

- [ ] **Step 1: Run full build**

Run: `./gradlew assembleDebug -x lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test -x lint`
Expected: All tests PASS

- [ ] **Step 3: Verify no untracked or unstaged changes remain**

Run: `git status`
Expected: clean working tree (or only the pre-existing modified files from git status)
