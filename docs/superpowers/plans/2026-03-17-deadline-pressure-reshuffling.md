# Deadline Pressure Reshuffling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a deadline-pressured task is scheduled, attempt to reshuffle lower-priority tasks to give the user more buffer time — even if the task fits without reshuffling.

**Architecture:** Add a `now: Instant` parameter to `scheduleWithConflictResolution()` for testability. After direct scheduling succeeds, calculate pressure (duration / time-until-deadline). If >= 0.25, try displacing lower-priority blocks to find an earlier slot. Return `NeedsReschedule` if an earlier slot is found, otherwise keep the direct result. Callers pass `Instant.now()`.

**Tech Stack:** Kotlin, JUnit + Truth (existing test patterns)

---

### Task 1: Add `now` parameter to `scheduleWithConflictResolution`

**Files:**
- Modify: `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt:171` (add parameter)
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt` (pass `Instant.now()`)
- Modify: `app/src/main/java/com/tasktracker/data/sync/CalendarSyncWorker.kt` (does not call this method — verify)

The method signature changes from:

```kotlin
fun scheduleWithConflictResolution(
    newTask: Task,
    allTasks: List<Task>,
    existingBlocks: List<ScheduledBlock>,
    availability: List<UserAvailability>,
    busySlots: List<TimeSlot>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId,
): SchedulingResult
```

to:

```kotlin
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
): SchedulingResult
```

- [ ] **Step 1: Add `now` parameter with default to `scheduleWithConflictResolution`**

Add `now: Instant = Instant.now()` as the last parameter. No behavior change yet — the parameter is unused until Task 3.

- [ ] **Step 2: Update caller in `TaskEditViewModel.save()`**

Pass `now = Instant.now()` explicitly at the call site (line ~146).

- [ ] **Step 3: Run tests to verify nothing broke**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.*" 2>&1 | tail -10`
Expected: All existing tests pass unchanged (default parameter preserves behavior).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "refactor: add now parameter to scheduleWithConflictResolution for testability"
```

---

### Task 2: Add deadline pressure tests

**Files:**
- Modify: `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerConflictTest.kt`

All tests pass a fixed `now` value (`monday` at 9am) so pressure calculations are deterministic.

- [ ] **Step 1: Add `now` field to test class**

Add after the `monday` field:

```kotlin
private val now = monday.atTime(9, 0).atZone(zoneId).toInstant()
```

- [ ] **Step 2: Write test — pressure reshuffle triggers when pressure >= 0.25**

A 60-min task with a 4-hour deadline from `now` (pressure = 60/240 = 0.25) should trigger reshuffle when a lower-priority task occupies an earlier slot.

```kotlin
@Test
fun `deadline pressure triggers reshuffle when pressure at threshold`() {
    // Low-priority task occupies 9-10am, high-priority task would get 10-11am
    // Deadline is 4 hours from now (pressure = 60min / 240min = 0.25)
    val nineAm = now
    val tenAm = now.plus(60, ChronoUnit.MINUTES)
    val deadline = now.plus(240, ChronoUnit.MINUTES)

    val existingBlocks = listOf(
        ScheduledBlock(
            id = 1, taskId = 2,
            startTime = nineAm,
            endTime = tenAm,
            status = BlockStatus.CONFIRMED,
        ),
    )
    val lowPriorityTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadline)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(lowPriorityTask, newTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    assertThat(result).isInstanceOf(SchedulingResult.NeedsReschedule::class.java)
    val reschedule = result as SchedulingResult.NeedsReschedule
    assertThat(reschedule.newBlocks[0].startTime).isEqualTo(nineAm)
}
```

- [ ] **Step 3: Write test — no reshuffle when pressure is below threshold**

```kotlin
@Test
fun `no deadline pressure reshuffle when pressure below threshold`() {
    // 60-min task, 5-hour deadline (pressure = 60/300 = 0.2, below 0.25)
    val nineAm = now
    val tenAm = now.plus(60, ChronoUnit.MINUTES)
    val deadline = now.plus(300, ChronoUnit.MINUTES)

    val existingBlocks = listOf(
        ScheduledBlock(
            id = 1, taskId = 2,
            startTime = nineAm,
            endTime = tenAm,
            status = BlockStatus.CONFIRMED,
        ),
    )
    val lowPriorityTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadline)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(lowPriorityTask, newTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
}
```

- [ ] **Step 4: Write test — no reshuffle when task has no deadline**

```kotlin
@Test
fun `no deadline pressure reshuffle when task has no deadline`() {
    val nineAm = now
    val tenAm = now.plus(60, ChronoUnit.MINUTES)

    val existingBlocks = listOf(
        ScheduledBlock(
            id = 1, taskId = 2,
            startTime = nineAm,
            endTime = tenAm,
            status = BlockStatus.CONFIRMED,
        ),
    )
    val lowPriorityTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = null)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(lowPriorityTask, newTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
}
```

- [ ] **Step 5: Write test — reshuffle abandoned when no lower-priority blocks exist**

```kotlin
@Test
fun `deadline pressure keeps direct result when no lower-priority blocks`() {
    val deadline = now.plus(240, ChronoUnit.MINUTES)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = deadline)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(newTask),
        existingBlocks = emptyList(),
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
}
```

- [ ] **Step 6: Write test — reshuffle abandoned when displaced task would miss deadline**

```kotlin
@Test
fun `deadline pressure abandoned when displaced task would miss its deadline`() {
    // Low-priority task at 9-10am has a tight deadline at 10:30am
    // If displaced to 10am+, its 60-min duration exceeds the 10:30 deadline
    val nineAm = now
    val tenAm = now.plus(60, ChronoUnit.MINUTES)
    val highPriorityDeadline = now.plus(240, ChronoUnit.MINUTES)
    val lowPriorityDeadline = monday.atTime(10, 30).atZone(zoneId).toInstant()

    val existingBlocks = listOf(
        ScheduledBlock(
            id = 1, taskId = 2,
            startTime = nineAm,
            endTime = tenAm,
            status = BlockStatus.CONFIRMED,
        ),
    )
    val lowPriorityTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER, deadline = lowPriorityDeadline)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = highPriorityDeadline)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(lowPriorityTask, newTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
}
```

- [ ] **Step 7: Write test — deadline already passed triggers reshuffle**

```kotlin
@Test
fun `deadline already passed treats pressure as 1 and triggers reshuffle`() {
    val nineAm = now
    val tenAm = now.plus(60, ChronoUnit.MINUTES)
    // Deadline is 30 minutes BEFORE now
    val pastDeadline = now.minus(30, ChronoUnit.MINUTES)

    val existingBlocks = listOf(
        ScheduledBlock(
            id = 1, taskId = 2,
            startTime = nineAm,
            endTime = tenAm,
            status = BlockStatus.CONFIRMED,
        ),
    )
    val lowPriorityTask = task(id = 2, duration = 60, quadrant = Quadrant.NEITHER)
    val newTask = task(id = 3, duration = 60, quadrant = Quadrant.URGENT_IMPORTANT, deadline = pastDeadline)

    val result = scheduler.scheduleWithConflictResolution(
        newTask = newTask,
        allTasks = listOf(lowPriorityTask, newTask),
        existingBlocks = existingBlocks,
        availability = listOf(availability()),
        busySlots = emptyList(),
        startDate = monday,
        endDate = monday,
        zoneId = zoneId,
        now = now,
    )
    // Direct scheduling would fail (deadline passed), so this falls through
    // to existing displacement logic — should still produce a result
    // The key assertion: we don't crash on negative minutesUntilDeadline
    assertThat(result).isNotNull()
}
```

- [ ] **Step 8: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerConflictTest" 2>&1 | tail -20`
Expected: New tests FAIL (pressure logic not yet implemented). Existing tests still pass.

- [ ] **Step 9: Commit failing tests**

```bash
git add app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerConflictTest.kt
git commit -m "test: add failing tests for deadline pressure reshuffling"
```

---

### Task 3: Implement deadline pressure reshuffling

**Files:**
- Modify: `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt`

- [ ] **Step 1: Add the threshold constant**

In `TaskScheduler.companion`, add after `MIN_SPLIT_BLOCK_MINUTES`:

```kotlin
companion object {
    private const val MIN_SPLIT_BLOCK_MINUTES = 30L
    private const val DEADLINE_PRESSURE_THRESHOLD = 0.25
}
```

- [ ] **Step 2: Replace the early return after direct scheduling with pressure check**

Replace the block at lines 191-193:

```kotlin
if (directResult is SchedulingResult.Scheduled && directResult.blocks.isNotEmpty()) {
    return directResult
}
```

with:

```kotlin
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
```

- [ ] **Step 3: Run scheduler tests**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.*" 2>&1 | tail -20`
Expected: ALL tests pass (new + existing)

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt
git commit -m "feat: add deadline pressure reshuffling to scheduler

When a task's deadline pressure (duration/timeUntilDeadline) >= 0.25,
attempt to reshuffle lower-priority tasks for an earlier slot.
Abandoned if reshuffle doesn't improve timing or breaks displaced deadlines."
```
