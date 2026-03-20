# Recurring Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add recurring task support — templates that generate concrete task instances on a configurable "every X days" interval, with fixed-time and flexible-time modes.

**Architecture:** New `RecurringTask` template entity generates `Task` instances via a `RecurrenceExpander`. Fixed-time instances are injected as busy slots before the existing best-fit algorithm. Flexible instances enter the algorithm as regular tasks. Deletion uses exceptions (single) or endDate (future). No changes to the core scheduling algorithm.

**Tech Stack:** Kotlin, Room (migration v3→v4), Hilt DI, Jetpack Compose (Material 3), Google Truth (tests)

**Spec:** `docs/superpowers/specs/2026-03-19-recurring-tasks-design.md`

---

## File Structure

### New Files

| File | Responsibility |
|---|---|
| `domain/model/RecurringTask.kt` | Domain model for recurring task template |
| `domain/model/RecurringTaskException.kt` | Domain model for cancelled instance dates |
| `domain/scheduler/RecurrenceExpander.kt` | Generates Task instances from templates within a window |
| `domain/validation/RecurringTaskValidator.kt` | Validates recurring task creation/edit inputs |
| `domain/repository/RecurringTaskRepository.kt` | Repository interface for RecurringTask CRUD |
| `domain/repository/RecurringTaskExceptionRepository.kt` | Repository interface for exceptions |
| `data/local/entity/RecurringTaskEntity.kt` | Room entity for recurring_tasks table |
| `data/local/entity/RecurringTaskExceptionEntity.kt` | Room entity for recurring_task_exceptions table |
| `data/local/dao/RecurringTaskDao.kt` | Room DAO for recurring_tasks |
| `data/local/dao/RecurringTaskExceptionDao.kt` | Room DAO for recurring_task_exceptions |
| `data/repository/RecurringTaskRepositoryImpl.kt` | Repository implementation |
| `data/repository/RecurringTaskExceptionRepositoryImpl.kt` | Repository implementation |
| `ui/components/RecurringTaskFields.kt` | Compose UI for recurring fields (toggle, interval, dates, fixed time) |
| `ui/components/RecurringDeleteDialog.kt` | Material 3 deletion dialog |
| `test/.../domain/scheduler/RecurrenceExpanderTest.kt` | Unit tests for expander |
| `test/.../domain/validation/RecurringTaskValidatorTest.kt` | Unit tests for validator |

### Modified Files

| File | Changes |
|---|---|
| `domain/model/Task.kt` | Add `recurringTaskId`, `instanceDate`, `fixedTime` fields |
| `domain/model/TaskWithScheduleInfo.kt` | Add `recurringTaskId`, `instanceDate` fields |
| `domain/repository/TaskRepository.kt` | Add `getByRecurringTaskId`, `getByRecurringTaskIdAndDateRange` |
| `data/local/entity/TaskEntity.kt` | Add columns (no FK annotation — enforced at app level), update `toDomain`/`fromDomain` |
| `ui/components/CalendarGrid.kt` | Extracted from DeadlinePicker for reuse |
| `ui/components/TimeWheel.kt` | Extracted from DeadlinePicker for reuse |
| `data/local/dao/TaskDao.kt` | Add queries, update `observeAllWithNextBlock` tuple |
| `data/local/converter/Converters.kt` | Add `LocalDate` converter |
| `data/repository/TaskRepositoryImpl.kt` | Implement new query methods, update mapping |
| `data/local/TaskTrackerDatabase.kt` | Add entities, DAOs, MIGRATION_3_4 |
| `di/RepositoryModule.kt` | Bind new repositories |
| `di/DatabaseModule.kt` | Provide new DAOs |
| `di/SchedulerModule.kt` | Provide RecurrenceExpander, RecurringTaskValidator |
| `ui/taskedit/TaskEditViewModel.kt` | Add recurring state, save logic |
| `ui/taskedit/TaskEditScreen.kt` | Add recurring UI section |
| `ui/tasklist/TaskListViewModel.kt` | Add recurring delete logic, expand on reschedule |
| `ui/tasklist/TaskListScreen.kt` | Show recurring indicator, deletion dialog |

All paths are relative to `app/src/main/java/com/tasktracker/` (source) or `app/src/test/java/com/tasktracker/` (test).

---

## Task 1: Domain Models

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/RecurringTask.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/RecurringTaskException.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/model/Task.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/model/TaskWithScheduleInfo.kt`

- [ ] **Step 1: Create RecurringTask domain model**

```kotlin
// app/src/main/java/com/tasktracker/domain/model/RecurringTask.kt
package com.tasktracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RecurringTask(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val intervalDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
```

- [ ] **Step 2: Create RecurringTaskException domain model**

```kotlin
// app/src/main/java/com/tasktracker/domain/model/RecurringTaskException.kt
package com.tasktracker.domain.model

import java.time.LocalDate

data class RecurringTaskException(
    val id: Long = 0,
    val recurringTaskId: Long,
    val exceptionDate: LocalDate,
)
```

- [ ] **Step 3: Add recurringTaskId and instanceDate to Task**

In `Task.kt`, add three fields after `recurringPattern`:

```kotlin
val recurringTaskId: Long? = null,
val instanceDate: LocalDate? = null,
val fixedTime: LocalTime? = null,
```

The `fixedTime` field carries the fixed time from the `RecurringTask` template so the scheduler knows which instances are fixed-time vs flexible without querying back to the template. For ad hoc tasks, this is always `null`.

The existing `recurringPattern: String? = null` stays (unused, kept for migration compat).

Add `import java.time.LocalDate` and `import java.time.LocalTime` to imports.

- [ ] **Step 4: Add recurringTaskId and instanceDate to TaskWithScheduleInfo**

In `TaskWithScheduleInfo.kt`, add after `blockCount`:

```kotlin
val recurringTaskId: Long? = null,
val instanceDate: LocalDate? = null,
```

The `instanceDate` is needed by the deletion dialog to display and pass the specific occurrence date.

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/RecurringTask.kt \
       app/src/main/java/com/tasktracker/domain/model/RecurringTaskException.kt \
       app/src/main/java/com/tasktracker/domain/model/Task.kt \
       app/src/main/java/com/tasktracker/domain/model/TaskWithScheduleInfo.kt
git commit -m "feat: add RecurringTask and RecurringTaskException domain models"
```

---

## Task 2: RecurrenceExpander — Tests

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/scheduler/RecurrenceExpanderTest.kt`

Write all tests first, before the implementation. Tests use Google Truth (`assertThat`) like the existing test suite.

- [ ] **Step 1: Write RecurrenceExpanderTest**

```kotlin
// app/src/test/java/com/tasktracker/domain/scheduler/RecurrenceExpanderTest.kt
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceExpanderTest {

    private val expander = RecurrenceExpander()

    private fun recurringTask(
        id: Long = 1,
        intervalDays: Int = 1,
        startDate: LocalDate = LocalDate.of(2026, 3, 19),
        endDate: LocalDate? = null,
        fixedTime: LocalTime? = null,
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
        dayPreference: DayPreference = DayPreference.ANY,
        splittable: Boolean = false,
    ) = RecurringTask(
        id = id,
        title = "Recurring $id",
        description = "Test recurring task",
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        dayPreference = dayPreference,
        splittable = splittable,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        fixedTime = fixedTime,
    )

    // --- Basic generation ---

    @Test
    fun `generates daily instances within window`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(3) // Mar 19, 20, 21 (windowEnd is exclusive)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `generates every-3-days instances`() {
        val rt = recurringTask(intervalDays = 3, startDate = LocalDate.of(2026, 3, 19))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 4, 2),
        )
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 22),
            LocalDate.of(2026, 3, 25),
            LocalDate.of(2026, 3, 28),
            LocalDate.of(2026, 3, 31),
        )
    }

    @Test
    fun `copies template fields to generated instances`() {
        val rt = recurringTask(
            id = 42,
            duration = 90,
            quadrant = Quadrant.URGENT_IMPORTANT,
            dayPreference = DayPreference.WEEKDAY,
            splittable = true,
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        val task = result[0]
        assertThat(task.title).isEqualTo("Recurring 42")
        assertThat(task.description).isEqualTo("Test recurring task")
        assertThat(task.estimatedDurationMinutes).isEqualTo(90)
        assertThat(task.quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(task.dayPreference).isEqualTo(DayPreference.WEEKDAY)
        assertThat(task.splittable).isTrue()
        assertThat(task.recurringTaskId).isEqualTo(42)
        assertThat(task.status).isEqualTo(TaskStatus.PENDING)
        assertThat(task.deadline).isNull()
    }

    // --- Boundary conditions ---

    @Test
    fun `respects endDate`() {
        val rt = recurringTask(
            intervalDays = 1,
            startDate = LocalDate.of(2026, 3, 19),
            endDate = LocalDate.of(2026, 3, 21),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        // endDate is inclusive — generates Mar 19, 20, 21
        assertThat(result).hasSize(3)
        assertThat(result.last().instanceDate).isEqualTo(LocalDate.of(2026, 3, 21))
    }

    @Test
    fun `skips dates before windowStart`() {
        val rt = recurringTask(
            intervalDays = 1,
            startDate = LocalDate.of(2026, 3, 15),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(3) // Mar 19, 20, 21
        assertThat(result.first().instanceDate).isEqualTo(LocalDate.of(2026, 3, 19))
    }

    @Test
    fun `returns empty list when startDate is after windowEnd`() {
        val rt = recurringTask(startDate = LocalDate.of(2026, 4, 1))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when endDate is before windowStart`() {
        val rt = recurringTask(
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 10),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        assertThat(result).isEmpty()
    }

    // --- Filtering ---

    @Test
    fun `skips exception dates`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val exceptions = listOf(
            RecurringTaskException(recurringTaskId = 1, exceptionDate = LocalDate.of(2026, 3, 20)),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = exceptions,
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(2) // Mar 19, 21 (skipped 20)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 19),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `skips dates with existing instances`() {
        val rt = recurringTask(intervalDays = 1, startDate = LocalDate.of(2026, 3, 19))
        val existing = listOf(
            Task(
                id = 100,
                title = "Existing",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                recurringTaskId = 1,
                instanceDate = LocalDate.of(2026, 3, 19),
            ),
        )
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = existing,
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 22),
        )
        assertThat(result).hasSize(2) // Mar 20, 21 (skipped 19)
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 3, 21),
        )
    }

    @Test
    fun `copies fixedTime to generated instances`() {
        val rt = recurringTask(fixedTime = LocalTime.of(7, 0))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].fixedTime).isEqualTo(LocalTime.of(7, 0))
    }

    @Test
    fun `flexible instances have null fixedTime`() {
        val rt = recurringTask(fixedTime = null)
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].fixedTime).isNull()
    }

    @Test
    fun `generated instances always have null deadline`() {
        val rt = recurringTask()
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 20),
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].deadline).isNull()
    }

    @Test
    fun `every-3-days with startDate before window aligns correctly`() {
        // Start on Mar 15, every 3 days: 15, 18, 21, 24...
        val rt = recurringTask(intervalDays = 3, startDate = LocalDate.of(2026, 3, 15))
        val result = expander.expand(
            recurringTask = rt,
            exceptions = emptyList(),
            existingInstances = emptyList(),
            windowStart = LocalDate.of(2026, 3, 19),
            windowEnd = LocalDate.of(2026, 3, 25),
        )
        // Only Mar 21 and Mar 24 fall in window
        assertThat(result.map { it.instanceDate }).containsExactly(
            LocalDate.of(2026, 3, 21),
            LocalDate.of(2026, 3, 24),
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.RecurrenceExpanderTest" 2>&1 | tail -5`
Expected: FAIL — `RecurrenceExpander` class doesn't exist yet

- [ ] **Step 3: Commit test file**

```bash
git add app/src/test/java/com/tasktracker/domain/scheduler/RecurrenceExpanderTest.kt
git commit -m "test: add RecurrenceExpander unit tests (red)"
```

---

## Task 3: RecurrenceExpander — Implementation

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/scheduler/RecurrenceExpander.kt`

- [ ] **Step 1: Implement RecurrenceExpander**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.RecurrenceExpanderTest" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/RecurrenceExpander.kt
git commit -m "feat: implement RecurrenceExpander for recurring task instance generation"
```

---

## Task 4: RecurringTaskValidator — Tests & Implementation

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/validation/RecurringTaskValidatorTest.kt`
- Create: `app/src/main/java/com/tasktracker/domain/validation/RecurringTaskValidator.kt`

- [ ] **Step 1: Write RecurringTaskValidatorTest**

```kotlin
// app/src/test/java/com/tasktracker/domain/validation/RecurringTaskValidatorTest.kt
package com.tasktracker.domain.validation

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RecurringTaskValidatorTest {

    private val validator = RecurringTaskValidator()

    private val defaultAvailability = listOf(
        UserAvailability(
            dayOfWeek = java.time.DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            enabled = true,
        ),
    )

    private fun recurringTask(
        intervalDays: Int = 1,
        startDate: LocalDate = LocalDate.of(2026, 3, 25),
        endDate: LocalDate? = null,
        duration: Int = 60,
        splittable: Boolean = false,
    ) = RecurringTask(
        title = "Test",
        estimatedDurationMinutes = duration,
        quadrant = Quadrant.IMPORTANT,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        splittable = splittable,
    )

    @Test
    fun `valid recurring task passes`() {
        val result = validator.validate(
            recurringTask(),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `intervalDays below 1 fails`() {
        val result = validator.validate(
            recurringTask(intervalDays = 0),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason).contains("interval")
    }

    @Test
    fun `startDate in past fails on creation`() {
        val result = validator.validate(
            recurringTask(startDate = LocalDate.of(2026, 3, 15)),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
            isCreation = true,
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason).contains("past")
    }

    @Test
    fun `startDate in past passes on edit`() {
        val result = validator.validate(
            recurringTask(startDate = LocalDate.of(2026, 3, 15)),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
            isCreation = false,
        )
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `endDate before startDate fails`() {
        val result = validator.validate(
            recurringTask(
                startDate = LocalDate.of(2026, 3, 25),
                endDate = LocalDate.of(2026, 3, 20),
            ),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason).contains("end date")
    }

    @Test
    fun `duration below 15 fails`() {
        val result = validator.validate(
            recurringTask(duration = 10),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `duration above 480 fails`() {
        val result = validator.validate(
            recurringTask(duration = 500),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `duration not in 5-minute increments fails`() {
        val result = validator.validate(
            recurringTask(duration = 67),
            defaultAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `non-splittable exceeding longest window fails`() {
        val shortAvailability = listOf(
            UserAvailability(
                dayOfWeek = java.time.DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(9, 30),
                enabled = true,
            ),
        )
        val result = validator.validate(
            recurringTask(duration = 60, splittable = false),
            shortAvailability,
            today = LocalDate.of(2026, 3, 19),
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.validation.RecurringTaskValidatorTest" 2>&1 | tail -5`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Implement RecurringTaskValidator**

```kotlin
// app/src/main/java/com/tasktracker/domain/validation/RecurringTaskValidator.kt
package com.tasktracker.domain.validation

import com.tasktracker.domain.model.RecurringTask
import com.tasktracker.domain.model.UserAvailability
import java.time.Duration
import java.time.LocalDate

class RecurringTaskValidator {

    fun validate(
        recurringTask: RecurringTask,
        availability: List<UserAvailability>,
        today: LocalDate = LocalDate.now(),
        isCreation: Boolean = true,
    ): ValidationResult {
        if (recurringTask.intervalDays < 1) {
            return ValidationResult.Invalid("Repeat interval must be at least 1 day.")
        }
        if (isCreation && recurringTask.startDate.isBefore(today)) {
            return ValidationResult.Invalid("Start date must not be in the past.")
        }
        if (recurringTask.endDate != null && recurringTask.endDate.isBefore(recurringTask.startDate)) {
            return ValidationResult.Invalid("The end date must be after the start date.")
        }
        if (recurringTask.estimatedDurationMinutes < 15) {
            return ValidationResult.Invalid("Duration must be at least 15 minutes.")
        }
        if (recurringTask.estimatedDurationMinutes > 480) {
            return ValidationResult.Invalid("Duration must not exceed 480 minutes (8 hours).")
        }
        if (recurringTask.estimatedDurationMinutes % 5 != 0) {
            return ValidationResult.Invalid("Duration must be in 5-minute increments.")
        }
        if (!recurringTask.splittable) {
            val longestWindowMinutes = availability
                .filter { it.enabled }
                .maxOfOrNull { Duration.between(it.startTime, it.endTime).toMinutes() } ?: 0L
            if (recurringTask.estimatedDurationMinutes > longestWindowMinutes) {
                return ValidationResult.Invalid(
                    "Non-splittable task duration exceeds the longest availability window " +
                        "($longestWindowMinutes min). Make the task splittable or extend " +
                        "an availability window."
                )
            }
        }
        return ValidationResult.Valid
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.validation.RecurringTaskValidatorTest" 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/tasktracker/domain/validation/RecurringTaskValidatorTest.kt \
       app/src/main/java/com/tasktracker/domain/validation/RecurringTaskValidator.kt
git commit -m "feat: add RecurringTaskValidator with TDD"
```

---

## Task 5: Data Layer — Entities, DAOs, Converters, Migration

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/local/entity/RecurringTaskEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/entity/RecurringTaskExceptionEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/RecurringTaskDao.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/RecurringTaskExceptionDao.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/entity/TaskEntity.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/dao/TaskDao.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/converter/Converters.kt`
- Modify: `app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt`

- [ ] **Step 1: Add LocalDate converter to Converters.kt**

Add to `Converters.kt` (after the existing `LocalTime` converters):

```kotlin
@TypeConverter
fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

@TypeConverter
fun toLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }
```

Also add `import java.time.LocalDate` to the imports.

- [ ] **Step 2: Create RecurringTaskEntity**

```kotlin
// app/src/main/java/com/tasktracker/data/local/entity/RecurringTaskEntity.kt
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "recurring_tasks")
data class RecurringTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val intervalDays: Int,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain() = RecurringTask(
        id = id,
        title = title,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        quadrant = quadrant,
        dayPreference = dayPreference,
        splittable = splittable,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        fixedTime = fixedTime,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(rt: RecurringTask) = RecurringTaskEntity(
            id = rt.id,
            title = rt.title,
            description = rt.description,
            estimatedDurationMinutes = rt.estimatedDurationMinutes,
            quadrant = rt.quadrant,
            dayPreference = rt.dayPreference,
            splittable = rt.splittable,
            intervalDays = rt.intervalDays,
            startDate = rt.startDate,
            endDate = rt.endDate,
            fixedTime = rt.fixedTime,
            createdAt = rt.createdAt,
            updatedAt = rt.updatedAt,
        )
    }
}
```

- [ ] **Step 3: Create RecurringTaskExceptionEntity**

```kotlin
// app/src/main/java/com/tasktracker/data/local/entity/RecurringTaskExceptionEntity.kt
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.RecurringTaskException
import java.time.LocalDate

@Entity(
    tableName = "recurring_task_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recurringTaskId"),
        Index(value = ["recurringTaskId", "exceptionDate"], unique = true),
    ],
)
data class RecurringTaskExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringTaskId: Long,
    val exceptionDate: LocalDate,
) {
    fun toDomain() = RecurringTaskException(
        id = id,
        recurringTaskId = recurringTaskId,
        exceptionDate = exceptionDate,
    )

    companion object {
        fun fromDomain(e: RecurringTaskException) = RecurringTaskExceptionEntity(
            id = e.id,
            recurringTaskId = e.recurringTaskId,
            exceptionDate = e.exceptionDate,
        )
    }
}
```

- [ ] **Step 4: Add recurringTaskId and instanceDate to TaskEntity**

In `TaskEntity.kt`, add the three new fields. **Do NOT add a `@ForeignKey` annotation** — SQLite does not support adding foreign keys to existing tables via `ALTER TABLE`, and Room validates FK annotations against the actual schema at runtime. The foreign key is enforced at the application level. Add only indices:

```kotlin
@Entity(
    tableName = "tasks",
    indices = [
        Index("recurringTaskId"),
        Index(value = ["recurringTaskId", "instanceDate"], unique = true),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // ... existing fields stay the same ...
    val recurringTaskId: Long? = null,
    val instanceDate: LocalDate? = null,
    val fixedTime: LocalTime? = null,
)
```

Update `toDomain()` to include the new fields:
```kotlin
recurringTaskId = recurringTaskId,
instanceDate = instanceDate,
fixedTime = fixedTime,
```

Update `fromDomain()` to include the new fields:
```kotlin
recurringTaskId = task.recurringTaskId,
instanceDate = task.instanceDate,
fixedTime = task.fixedTime,
```

Also add `import java.time.LocalDate` to imports.

- [ ] **Step 5: Create RecurringTaskDao**

```kotlin
// app/src/main/java/com/tasktracker/data/local/dao/RecurringTaskDao.kt
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.RecurringTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTaskEntity): Long

    @Update
    suspend fun update(entity: RecurringTaskEntity)

    @Delete
    suspend fun delete(entity: RecurringTaskEntity)

    @Query("SELECT * FROM recurring_tasks WHERE id = :id")
    suspend fun getById(id: Long): RecurringTaskEntity?

    @Query("SELECT * FROM recurring_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecurringTaskEntity>>

    @Query("SELECT * FROM recurring_tasks")
    suspend fun getAll(): List<RecurringTaskEntity>
}
```

- [ ] **Step 6: Create RecurringTaskExceptionDao**

```kotlin
// app/src/main/java/com/tasktracker/data/local/dao/RecurringTaskExceptionDao.kt
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.RecurringTaskExceptionEntity

@Dao
interface RecurringTaskExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTaskExceptionEntity): Long

    @Query("SELECT * FROM recurring_task_exceptions WHERE recurringTaskId = :recurringTaskId")
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskExceptionEntity>

    @Query("DELETE FROM recurring_task_exceptions WHERE recurringTaskId = :recurringTaskId")
    suspend fun deleteByRecurringTaskId(recurringTaskId: Long)
}
```

- [ ] **Step 7: Add new queries to TaskDao**

Add to `TaskDao.kt`:

```kotlin
@Query("SELECT * FROM tasks WHERE recurringTaskId = :recurringTaskId")
suspend fun getByRecurringTaskId(recurringTaskId: Long): List<TaskEntity>

@Query("SELECT * FROM tasks WHERE recurringTaskId = :recurringTaskId AND instanceDate >= :startDate AND instanceDate < :endDate")
suspend fun getByRecurringTaskIdAndDateRange(
    recurringTaskId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
): List<TaskEntity>

@Query("DELETE FROM tasks WHERE recurringTaskId = :recurringTaskId AND instanceDate >= :fromDate")
suspend fun deleteByRecurringTaskIdFromDate(recurringTaskId: Long, fromDate: LocalDate)
```

Also update the `TaskWithNextBlockTuple` (used by `observeAllWithNextBlock`) to include `recurringTaskId` and `instanceDate`:

```kotlin
data class TaskWithNextBlockTuple(
    // ... existing fields ...
    val recurringTaskId: Long?,
    val instanceDate: LocalDate?,
)
```

The `observeAllWithNextBlock` SQL query already does `SELECT t.*` which will pick up the new columns automatically since they are in the `tasks` table.

- [ ] **Step 8: Update TaskTrackerDatabase**

In `TaskTrackerDatabase.kt`:

1. Add new entities to the `@Database` annotation:
```kotlin
entities = [
    TaskEntity::class,
    ScheduledBlockEntity::class,
    UserAvailabilityEntity::class,
    CalendarSelectionEntity::class,
    PendingSyncOperationEntity::class,
    RecurringTaskEntity::class,
    RecurringTaskExceptionEntity::class,
],
version = 4,
```

2. Add abstract DAO methods:
```kotlin
abstract fun recurringTaskDao(): RecurringTaskDao
abstract fun recurringTaskExceptionDao(): RecurringTaskExceptionDao
```

3. Add MIGRATION_3_4 in the companion:
```kotlin
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Create recurring_tasks table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recurring_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                estimatedDurationMinutes INTEGER NOT NULL,
                quadrant TEXT NOT NULL,
                dayPreference TEXT NOT NULL DEFAULT 'ANY',
                splittable INTEGER NOT NULL DEFAULT 0,
                intervalDays INTEGER NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER,
                fixedTime TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Create recurring_task_exceptions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recurring_task_exceptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recurringTaskId INTEGER NOT NULL,
                exceptionDate INTEGER NOT NULL,
                FOREIGN KEY (recurringTaskId) REFERENCES recurring_tasks(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_task_exceptions_recurringTaskId ON recurring_task_exceptions (recurringTaskId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_task_exceptions_recurringTaskId_exceptionDate ON recurring_task_exceptions (recurringTaskId, exceptionDate)")

        // Add columns to tasks table
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurringTaskId INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE tasks ADD COLUMN instanceDate INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE tasks ADD COLUMN fixedTime TEXT DEFAULT NULL")

        // Note: Cannot add foreign key to existing table in SQLite without recreating.
        // The foreign key is enforced at the Room/app level. The index still helps queries.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_recurringTaskId ON tasks (recurringTaskId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_recurringTaskId_instanceDate ON tasks (recurringTaskId, instanceDate)")
    }
}
```

4. Add the new migration to `DatabaseModule.kt`'s `.addMigrations()` call.

- [ ] **Step 9: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/local/
git commit -m "feat: add Room entities, DAOs, and migration for recurring tasks"
```

---

## Task 6: Repository Layer

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/repository/RecurringTaskRepository.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/RecurringTaskExceptionRepository.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/RecurringTaskRepositoryImpl.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/RecurringTaskExceptionRepositoryImpl.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/repository/TaskRepository.kt`
- Modify: `app/src/main/java/com/tasktracker/data/repository/TaskRepositoryImpl.kt`

- [ ] **Step 1: Create RecurringTaskRepository interface**

```kotlin
// app/src/main/java/com/tasktracker/domain/repository/RecurringTaskRepository.kt
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.RecurringTask
import kotlinx.coroutines.flow.Flow

interface RecurringTaskRepository {
    suspend fun insert(recurringTask: RecurringTask): Long
    suspend fun update(recurringTask: RecurringTask)
    suspend fun delete(recurringTask: RecurringTask)
    suspend fun getById(id: Long): RecurringTask?
    fun observeAll(): Flow<List<RecurringTask>>
    suspend fun getAll(): List<RecurringTask>
}
```

- [ ] **Step 2: Create RecurringTaskExceptionRepository interface**

```kotlin
// app/src/main/java/com/tasktracker/domain/repository/RecurringTaskExceptionRepository.kt
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.RecurringTaskException

interface RecurringTaskExceptionRepository {
    suspend fun insert(exception: RecurringTaskException): Long
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskException>
    suspend fun deleteByRecurringTaskId(recurringTaskId: Long)
}
```

- [ ] **Step 3: Implement RecurringTaskRepositoryImpl**

```kotlin
// app/src/main/java/com/tasktracker/data/repository/RecurringTaskRepositoryImpl.kt
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.RecurringTaskDao
import com.tasktracker.data.local.entity.RecurringTaskEntity
import com.tasktracker.domain.model.RecurringTask
import com.tasktracker.domain.repository.RecurringTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RecurringTaskRepositoryImpl @Inject constructor(
    private val dao: RecurringTaskDao,
) : RecurringTaskRepository {
    override suspend fun insert(recurringTask: RecurringTask): Long =
        dao.insert(RecurringTaskEntity.fromDomain(recurringTask))

    override suspend fun update(recurringTask: RecurringTask) =
        dao.update(RecurringTaskEntity.fromDomain(recurringTask.copy(updatedAt = Instant.now())))

    override suspend fun delete(recurringTask: RecurringTask) =
        dao.delete(RecurringTaskEntity.fromDomain(recurringTask))

    override suspend fun getById(id: Long): RecurringTask? =
        dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<RecurringTask>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<RecurringTask> =
        dao.getAll().map { it.toDomain() }
}
```

- [ ] **Step 4: Implement RecurringTaskExceptionRepositoryImpl**

```kotlin
// app/src/main/java/com/tasktracker/data/repository/RecurringTaskExceptionRepositoryImpl.kt
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.RecurringTaskExceptionDao
import com.tasktracker.data.local.entity.RecurringTaskExceptionEntity
import com.tasktracker.domain.model.RecurringTaskException
import com.tasktracker.domain.repository.RecurringTaskExceptionRepository
import javax.inject.Inject

class RecurringTaskExceptionRepositoryImpl @Inject constructor(
    private val dao: RecurringTaskExceptionDao,
) : RecurringTaskExceptionRepository {
    override suspend fun insert(exception: RecurringTaskException): Long =
        dao.insert(RecurringTaskExceptionEntity.fromDomain(exception))

    override suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskException> =
        dao.getByRecurringTaskId(recurringTaskId).map { it.toDomain() }

    override suspend fun deleteByRecurringTaskId(recurringTaskId: Long) =
        dao.deleteByRecurringTaskId(recurringTaskId)
}
```

- [ ] **Step 5: Add new methods to TaskRepository interface**

In `TaskRepository.kt`, add:

```kotlin
suspend fun getByRecurringTaskId(recurringTaskId: Long): List<Task>
suspend fun getByRecurringTaskIdAndDateRange(
    recurringTaskId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Task>
suspend fun deleteByRecurringTaskIdFromDate(recurringTaskId: Long, fromDate: LocalDate)
```

Add `import java.time.LocalDate` to imports.

- [ ] **Step 6: Implement new methods in TaskRepositoryImpl**

In `TaskRepositoryImpl.kt`, add:

```kotlin
override suspend fun getByRecurringTaskId(recurringTaskId: Long): List<Task> =
    taskDao.getByRecurringTaskId(recurringTaskId).map { it.toDomain() }

override suspend fun getByRecurringTaskIdAndDateRange(
    recurringTaskId: Long,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Task> =
    taskDao.getByRecurringTaskIdAndDateRange(recurringTaskId, startDate, endDate).map { it.toDomain() }

override suspend fun deleteByRecurringTaskIdFromDate(recurringTaskId: Long, fromDate: LocalDate) =
    taskDao.deleteByRecurringTaskIdFromDate(recurringTaskId, fromDate)
```

Also update `observeAllWithScheduleInfo()` mapping to include the new fields:

In the `Task(...)` constructor inside the mapping, add:
```kotlin
recurringTaskId = t.recurringTaskId,
instanceDate = t.instanceDate,
fixedTime = t.fixedTime,
```

In the `TaskWithScheduleInfo(...)` constructor, add:
```kotlin
recurringTaskId = t.recurringTaskId,
instanceDate = t.instanceDate,
```

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/repository/ \
       app/src/main/java/com/tasktracker/data/repository/
git commit -m "feat: add repository layer for recurring tasks"
```

---

## Task 7: Dependency Injection

**Files:**
- Modify: `app/src/main/java/com/tasktracker/di/RepositoryModule.kt`
- Modify: `app/src/main/java/com/tasktracker/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/tasktracker/di/SchedulerModule.kt`

- [ ] **Step 1: Add bindings to RepositoryModule**

```kotlin
@Binds
@Singleton
abstract fun bindRecurringTaskRepository(impl: RecurringTaskRepositoryImpl): RecurringTaskRepository

@Binds
@Singleton
abstract fun bindRecurringTaskExceptionRepository(impl: RecurringTaskExceptionRepositoryImpl): RecurringTaskExceptionRepository
```

- [ ] **Step 2: Add DAO providers to DatabaseModule**

```kotlin
@Provides
fun provideRecurringTaskDao(db: TaskTrackerDatabase): RecurringTaskDao = db.recurringTaskDao()

@Provides
fun provideRecurringTaskExceptionDao(db: TaskTrackerDatabase): RecurringTaskExceptionDao = db.recurringTaskExceptionDao()
```

Also add `MIGRATION_3_4` to the `.addMigrations()` call:
```kotlin
.addMigrations(TaskTrackerDatabase.MIGRATION_1_2, TaskTrackerDatabase.MIGRATION_2_3, TaskTrackerDatabase.MIGRATION_3_4)
```

- [ ] **Step 3: Add providers to SchedulerModule**

```kotlin
@Provides
@Singleton
fun provideRecurrenceExpander(): RecurrenceExpander = RecurrenceExpander()

@Provides
@Singleton
fun provideRecurringTaskValidator(): RecurringTaskValidator = RecurringTaskValidator()
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/di/
git commit -m "feat: wire recurring task dependencies in Hilt modules"
```

---

## Task 8: UI — RecurringTaskFields Composable

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/components/RecurringTaskFields.kt`

This composable renders the recurring toggle and its indented child fields. It follows the same patterns as the existing Splittable toggle in `TaskEditScreen.kt` and the `DeadlinePicker` / `DayPreferenceSelector` components.

- [ ] **Step 1: Create RecurringTaskFields**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/RecurringTaskFields.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun RecurringTaskFields(
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    endDate: LocalDate?,
    onEndDateChange: (LocalDate?) -> Unit,
    isFixedTime: Boolean,
    onFixedTimeChange: (Boolean) -> Unit,
    fixedTime: LocalTime?,
    onFixedTimeValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Recurring task toggle (mirrors Splittable style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Recurring task",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Repeats on a schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = isRecurring,
                onCheckedChange = onRecurringChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SortdColors.accent,
                    checkedThumbColor = Color.White,
                ),
            )
        }

        // Indented recurring fields
        AnimatedVisibility(visible = isRecurring) {
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .drawStartBorder(SortdColors.accent)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // REPEAT EVERY
                IntervalInput(intervalDays = intervalDays, onIntervalChange = onIntervalChange)

                // START DATE
                var showStartDatePicker by remember { mutableStateOf(false) }
                DateField(
                    label = "START DATE",
                    date = startDate,
                    placeholder = "Set start date",
                    isActive = showStartDatePicker,
                    onClick = { showStartDatePicker = !showStartDatePicker },
                )
                AnimatedVisibility(visible = showStartDatePicker) {
                    // Reuse date picker pattern from DeadlinePicker
                    SimpleDatePicker(
                        selectedDate = startDate,
                        onDateSelected = { date ->
                            onStartDateChange(date)
                            showStartDatePicker = false
                        },
                    )
                }

                // END DATE
                var showEndDatePicker by remember { mutableStateOf(false) }
                DateField(
                    label = "END DATE",
                    date = endDate,
                    placeholder = "No end date",
                    isActive = showEndDatePicker,
                    onClick = { showEndDatePicker = !showEndDatePicker },
                )
                AnimatedVisibility(visible = showEndDatePicker) {
                    Column {
                        SimpleDatePicker(
                            selectedDate = endDate ?: startDate.plusDays(30),
                            onDateSelected = { date ->
                                onEndDateChange(date)
                                showEndDatePicker = false
                            },
                        )
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .clickable {
                                    onEndDateChange(null)
                                    showEndDatePicker = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Fixed time toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fixed time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Always at a specific time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = isFixedTime,
                        onCheckedChange = onFixedTimeChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = SortdColors.accent,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }

                // Time picker (nested indent when fixed time is ON)
                AnimatedVisibility(visible = isFixedTime) {
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 4.dp)
                            .drawStartBorder(SortdColors.accentLight)
                            .padding(start = 14.dp),
                    ) {
                        Text(
                            text = "TIME",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 11.sp, letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        var showTimePicker by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    if (showTimePicker) SortdColors.accent
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { showTimePicker = !showTimePicker }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("\uD83D\uDD50", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = fixedTime?.format(DateTimeFormatter.ofPattern("h:mm a"))
                                    ?: "Set time",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fixedTime != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                        AnimatedVisibility(visible = showTimePicker) {
                            // Reuse TimeWheel from DeadlinePicker — extract or call directly
                            // Implementation depends on whether TimeWheel is extracted as public
                            // For now, use a simplified time picker or extract TimeWheel
                        }
                    }
                }
            }
        }

        // Summary banner (when fixed time is ON and configured)
        if (isRecurring && isFixedTime && fixedTime != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SortdColors.accent.copy(alpha = 0.1f))
                    .border(1.dp, SortdColors.accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\uD83D\uDD01", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Every $intervalDays day${if (intervalDays > 1) "s" else ""} at ${
                        fixedTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                    }, starting ${startDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = SortdColors.accentLight,
                )
            }
        }
    }
}

@Composable
private fun IntervalInput(
    intervalDays: Int,
    onIntervalChange: (Int) -> Unit,
) {
    Column {
        Text(
            text = "REPEAT EVERY",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp, letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (intervalDays > 0) intervalDays.toString() else "",
                onValueChange = { text ->
                    val value = text.filter { it.isDigit() }.take(3).toIntOrNull()
                    if (value != null && value > 0) onIntervalChange(value)
                },
                modifier = Modifier.width(72.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SortdColors.accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    placeholder: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp, letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isActive) Modifier.border(1.dp, SortdColors.accent, RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\uD83D\uDCC5", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = date?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = if (date != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

// Helper Modifier to draw left border (used for indented sections)
private fun Modifier.drawStartBorder(color: androidx.compose.ui.graphics.Color) = this.then(
    Modifier.drawBehind {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(0f, size.height),
            strokeWidth = 2.dp.toPx(),
        )
    }
)

// SimpleDatePicker — minimal inline date picker for start/end dates
// Reuses the CalendarGrid pattern from DeadlinePicker
@Composable
private fun SimpleDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    // Delegate to a CalendarGrid similar to DeadlinePicker's implementation.
    // The implementation reuses the same visual patterns (month nav, weekday headers, day cells).
    // For brevity, this can initially call the CalendarGrid composable extracted from DeadlinePicker.
    // If CalendarGrid is not yet public, extract it as a shared composable in this task.
    var displayMonth by remember { mutableStateOf(java.time.YearMonth.from(selectedDate)) }
    val today = LocalDate.now()

    // Reuse CalendarGrid from DeadlinePicker (make it internal or extract)
    // This is a placeholder — the actual implementation follows the DeadlinePicker pattern.
    Text(
        "Calendar picker — extract CalendarGrid from DeadlinePicker",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

**Note for implementer:** The `CalendarGrid` composable in `DeadlinePicker.kt` is currently private. Extract it as an `internal` composable in a shared file (e.g., `CalendarGrid.kt`) or make it public so both `DeadlinePicker` and `RecurringTaskFields` can use it. Similarly, the `TimeWheel` composable should be extracted for reuse.

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/RecurringTaskFields.kt
git commit -m "feat: add RecurringTaskFields composable for task edit screen"
```

---

## Task 9: UI — RecurringDeleteDialog

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/components/RecurringDeleteDialog.kt`

- [ ] **Step 1: Create RecurringDeleteDialog**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/RecurringDeleteDialog.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RecurringDeleteChoice {
    THIS_INSTANCE,
    THIS_AND_FUTURE,
    ENTIRE_RECURRING_TASK,
}

@Composable
fun RecurringDeleteDialog(
    taskTitle: String,
    intervalDays: Int,
    instanceDate: LocalDate,
    onChoice: (RecurringDeleteChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\uD83D\uDD01", fontSize = 28.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Delete recurring task",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append(taskTitle)
                        append(" repeats every $intervalDays day${if (intervalDays > 1) "s" else ""}.")
                        append("\nWhat would you like to delete?")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(20.dp))

                val dateText = instanceDate.format(DateTimeFormatter.ofPattern("MMM d"))

                // Delete this instance
                DeleteOptionButton(
                    title = "Delete this instance",
                    subtitle = "Only $dateText",
                    backgroundAlpha = 0.15f,
                    borderAlpha = 0.3f,
                    onClick = { onChoice(RecurringDeleteChoice.THIS_INSTANCE) },
                )

                Spacer(Modifier.height(8.dp))

                // Delete this & future
                DeleteOptionButton(
                    title = "Delete this & future",
                    subtitle = "Stop from $dateText onward",
                    backgroundAlpha = 0.08f,
                    borderAlpha = 0.15f,
                    onClick = { onChoice(RecurringDeleteChoice.THIS_AND_FUTURE) },
                )

                Spacer(Modifier.height(8.dp))

                // Delete entire recurring task
                DeleteOptionButton(
                    title = "Delete all instances",
                    subtitle = "Remove this recurring task entirely",
                    backgroundAlpha = 0.05f,
                    borderAlpha = 0.1f,
                    onClick = { onChoice(RecurringDeleteChoice.ENTIRE_RECURRING_TASK) },
                )

                Spacer(Modifier.height(8.dp))

                // Cancel
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        },
    )
}

@Composable
private fun DeleteOptionButton(
    title: String,
    subtitle: String,
    backgroundAlpha: Float,
    borderAlpha: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = backgroundAlpha))
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = borderAlpha),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        )
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/RecurringDeleteDialog.kt
git commit -m "feat: add Material 3 recurring task deletion dialog"
```

---

## Task 10: ViewModel Integration — TaskEditViewModel

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt`

- [ ] **Step 1: Add recurring state to TaskEditUiState**

Add these fields to `TaskEditUiState`:

```kotlin
val isRecurring: Boolean = false,
val intervalDays: Int = 1,
val startDate: LocalDate = LocalDate.now(),
val endDate: LocalDate? = null,
val isFixedTime: Boolean = false,
val fixedTime: LocalTime? = null,
```

Add imports: `java.time.LocalDate`, `java.time.LocalTime`.

- [ ] **Step 2: Add update methods to TaskEditViewModel**

```kotlin
fun updateRecurring(isRecurring: Boolean) {
    _uiState.update { it.copy(isRecurring = isRecurring) }
}

fun updateIntervalDays(interval: Int) {
    _uiState.update { it.copy(intervalDays = interval) }
}

fun updateStartDate(date: LocalDate) {
    _uiState.update { it.copy(startDate = date) }
}

fun updateEndDate(date: LocalDate?) {
    _uiState.update { it.copy(endDate = date) }
}

fun updateFixedTime(isFixed: Boolean) {
    _uiState.update { it.copy(isFixedTime = isFixed, fixedTime = if (isFixed) it.fixedTime ?: LocalTime.of(9, 0) else null) }
}

fun updateFixedTimeValue(time: LocalTime) {
    _uiState.update { it.copy(fixedTime = time) }
}
```

- [ ] **Step 3: Inject recurring dependencies**

Add to the constructor:

```kotlin
private val recurringTaskRepository: RecurringTaskRepository,
private val recurringTaskExceptionRepository: RecurringTaskExceptionRepository,
private val recurrenceExpander: RecurrenceExpander,
private val recurringTaskValidator: RecurringTaskValidator,
```

- [ ] **Step 4: Update save() for recurring tasks**

In the `save()` method, after the existing title validation, add a branch:

```kotlin
if (state.isRecurring) {
    val recurringTask = RecurringTask(
        title = state.title.trim(),
        description = state.description.trim(),
        estimatedDurationMinutes = state.durationMinutes,
        quadrant = state.quadrant,
        dayPreference = state.dayPreference,
        splittable = state.splittable,
        intervalDays = state.intervalDays,
        startDate = state.startDate,
        endDate = state.endDate,
        fixedTime = if (state.isFixedTime) state.fixedTime else null,
    )

    val validationResult = recurringTaskValidator.validate(
        recurringTask, availabilityList, today = LocalDate.now(),
    )
    if (validationResult is ValidationResult.Invalid) {
        _uiState.update { it.copy(validationError = validationResult.reason, isSaving = false) }
        return@launch
    }

    // Save the template
    val templateId = recurringTaskRepository.insert(recurringTask)

    // Expand instances for the scheduling window
    val today = LocalDate.now()
    val windowEnd = today.plusDays(14)
    val instances = recurrenceExpander.expand(
        recurringTask = recurringTask.copy(id = templateId),
        exceptions = emptyList(),
        existingInstances = emptyList(),
        windowStart = today,
        windowEnd = windowEnd,
    )

    // Persist instances to get real IDs
    for (instance in instances) {
        val instanceId = taskRepository.insert(instance)
        // Schedule each instance through the existing flow
        // (Fixed-time instances are handled as busy slots by the scheduler)
    }

    _uiState.update { it.copy(savedSuccessfully = true, isSaving = false) }
    return@launch
}
```

The exact scheduling integration (partitioning fixed vs flexible, placing fixed-time as busy slots) is wired here. The details follow the spec's "Scheduler Integration" flow.

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "feat: integrate recurring task creation in TaskEditViewModel"
```

---

## Task 11: ViewModel Integration — TaskListViewModel (Deletion)

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt`

- [ ] **Step 1: Add recurring delete state to TaskListUiState**

```kotlin
val recurringDeleteTask: TaskWithScheduleInfo? = null,
```

- [ ] **Step 2: Add methods for recurring deletion**

```kotlin
fun showRecurringDeleteDialog(task: TaskWithScheduleInfo) {
    _uiState.update { it.copy(recurringDeleteTask = task) }
}

fun dismissRecurringDeleteDialog() {
    _uiState.update { it.copy(recurringDeleteTask = null) }
}

fun deleteRecurringInstance(task: Task) {
    viewModelScope.launch {
        val recurringTaskId = task.recurringTaskId ?: return@launch
        val instanceDate = task.instanceDate ?: return@launch

        // Create exception so expander doesn't regenerate
        recurringTaskExceptionRepository.insert(
            RecurringTaskException(recurringTaskId = recurringTaskId, exceptionDate = instanceDate)
        )

        // Delete calendar events, then delete the task instance
        syncManager.deleteTaskEvents(task.id)
        taskRepository.delete(task)

        _uiState.update { it.copy(recurringDeleteTask = null) }
    }
}

fun deleteRecurringInstanceAndFuture(task: Task) {
    viewModelScope.launch {
        val recurringTaskId = task.recurringTaskId ?: return@launch
        val instanceDate = task.instanceDate ?: return@launch

        // Set endDate on template to day before this instance
        val template = recurringTaskRepository.getById(recurringTaskId) ?: return@launch
        recurringTaskRepository.update(
            template.copy(endDate = instanceDate.minusDays(1))
        )

        // Collect all future instances and their blocks for calendar cleanup
        val futureInstances = taskRepository.getByRecurringTaskId(recurringTaskId)
            .filter { it.instanceDate != null && !it.instanceDate.isBefore(instanceDate) }

        for (instance in futureInstances) {
            syncManager.deleteTaskEvents(instance.id)
        }

        // Delete future task instances (cascade deletes blocks)
        taskRepository.deleteByRecurringTaskIdFromDate(recurringTaskId, instanceDate)

        _uiState.update { it.copy(recurringDeleteTask = null) }
    }
}
```

- [ ] **Step 2b: Add deleteEntireRecurringTask method**

```kotlin
fun deleteEntireRecurringTask(task: Task) {
    viewModelScope.launch {
        val recurringTaskId = task.recurringTaskId ?: return@launch

        // IMPORTANT: Collect all data BEFORE cascade delete
        val allInstances = taskRepository.getByRecurringTaskId(recurringTaskId)
        for (instance in allInstances) {
            syncManager.deleteTaskEvents(instance.id)
        }

        // Delete template — CASCADE deletes all instances, blocks, exceptions at SQLite level
        val template = recurringTaskRepository.getById(recurringTaskId) ?: return@launch
        recurringTaskRepository.delete(template)

        _uiState.update { it.copy(recurringDeleteTask = null) }
    }
}
```

- [ ] **Step 3: Inject recurring dependencies**

Add to constructor:

```kotlin
private val recurringTaskRepository: RecurringTaskRepository,
private val recurringTaskExceptionRepository: RecurringTaskExceptionRepository,
```

- [ ] **Step 4: Update deleteTask to check for recurring**

Modify the existing `deleteTask()` to route recurring tasks through the dialog:

```kotlin
fun deleteTask(taskInfo: TaskWithScheduleInfo) {
    if (taskInfo.recurringTaskId != null) {
        showRecurringDeleteDialog(taskInfo)
    } else {
        viewModelScope.launch {
            syncManager.deleteTaskEvents(taskInfo.task.id)
            taskRepository.delete(taskInfo.task)
        }
    }
}
```

Note: This changes the signature from `Task` to `TaskWithScheduleInfo`. Update call sites in the UI accordingly.

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListViewModel.kt
git commit -m "feat: add recurring task deletion logic to TaskListViewModel"
```

---

## Task 12: UI — Wire TaskEditScreen and TaskListScreen

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt` (or equivalent screen file)

- [ ] **Step 1: Add RecurringTaskFields to TaskEditScreen**

In `TaskEditScreen.kt`, after the Splittable toggle section (around line 192), add:

```kotlin
// Recurring task
RecurringTaskFields(
    isRecurring = uiState.isRecurring,
    onRecurringChange = viewModel::updateRecurring,
    intervalDays = uiState.intervalDays,
    onIntervalChange = viewModel::updateIntervalDays,
    startDate = uiState.startDate,
    onStartDateChange = viewModel::updateStartDate,
    endDate = uiState.endDate,
    onEndDateChange = viewModel::updateEndDate,
    isFixedTime = uiState.isFixedTime,
    onFixedTimeChange = viewModel::updateFixedTime,
    fixedTime = uiState.fixedTime,
    onFixedTimeValueChange = viewModel::updateFixedTimeValue,
)
```

- [ ] **Step 2: Add recurring indicator to task list items**

In the task list screen, when rendering task cards, check `taskInfo.recurringTaskId != null` and show a small recurrence indicator (e.g., a "\uD83D\uDD01" icon or text label) next to the task title.

- [ ] **Step 3: Wire RecurringDeleteDialog in task list screen**

```kotlin
val recurringDeleteTask = uiState.recurringDeleteTask
if (recurringDeleteTask != null) {
    val task = recurringDeleteTask.task
    val recurringTask = // fetch from repository or pass interval through TaskWithScheduleInfo
    RecurringDeleteDialog(
        taskTitle = task.title,
        intervalDays = recurringDeleteTask.intervalDays, // Add intervalDays to TaskWithScheduleInfo or fetch from ViewModel
        instanceDate = task.instanceDate ?: LocalDate.now(),
        onChoice = { choice ->
            when (choice) {
                RecurringDeleteChoice.THIS_INSTANCE -> viewModel.deleteRecurringInstance(task)
                RecurringDeleteChoice.THIS_AND_FUTURE -> viewModel.deleteRecurringInstanceAndFuture(task)
                RecurringDeleteChoice.ENTIRE_RECURRING_TASK -> viewModel.deleteEntireRecurringTask(task)
            }
        },
        onDismiss = viewModel::dismissRecurringDeleteDialog,
    )
}
```

**Note for implementer:** The `intervalDays` for the dialog needs to come from the `RecurringTask` template. Either extend `TaskWithScheduleInfo` to include `intervalDays`, or have the ViewModel fetch the template when showing the dialog and store it in UI state.

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/
git commit -m "feat: wire recurring task UI in edit and list screens"
```

---

## Task 13: Extract CalendarGrid and TimeWheel as Shared Components

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/components/CalendarGrid.kt`
- Create: `app/src/main/java/com/tasktracker/ui/components/TimeWheel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/components/RecurringTaskFields.kt`

The `CalendarGrid` and `TimeWheel` composables are currently `private` inside `DeadlinePicker.kt`. Extract them as `internal` composables in their own files so `RecurringTaskFields` can reuse them.

- [ ] **Step 1: Extract CalendarGrid**

Move the `CalendarGrid` composable from `DeadlinePicker.kt` into `CalendarGrid.kt`. Make it `internal`. Update `DeadlinePicker.kt` to import from the new file. The function signature stays the same.

- [ ] **Step 2: Extract TimeWheel**

Move the `TimeWheel` composable and its helper `emitTime()` from `DeadlinePicker.kt` into `TimeWheel.kt`. Make them `internal`. Update `DeadlinePicker.kt` to import from the new file.

- [ ] **Step 3: Update RecurringTaskFields to use shared components**

Replace the placeholder `SimpleDatePicker` in `RecurringTaskFields.kt` with the extracted `CalendarGrid`. Replace the empty time picker block with the extracted `TimeWheel`.

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/
git commit -m "refactor: extract CalendarGrid and TimeWheel as shared composables"
```

---

## Task 14: Scheduler Integration — Fixed-Time Placement

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt`

This task implements the spec's scheduler integration flow: partition fixed vs flexible instances, place fixed-time instances as busy slots, and handle displacement via NeedsReschedule.

- [ ] **Step 1: Implement fixed-time placement in save()**

After persisting recurring instances in the save() method (Task 10), add the partitioning and placement logic:

```kotlin
// Partition instances
val (fixedTimeInstances, flexibleInstances) = instances.map { instance ->
    val instanceId = taskRepository.insert(instance)
    instance.copy(id = instanceId)
}.partition { it.fixedTime != null }

// Place fixed-time instances as busy slots
val fixedTimeSlots = fixedTimeInstances.mapNotNull { instance ->
    val date = instance.instanceDate ?: return@mapNotNull null
    val time = instance.fixedTime ?: return@mapNotNull null
    val start = date.atTime(time).atZone(zoneId).toInstant()
    val end = start.plus(instance.estimatedDurationMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES)
    TimeSlot(startTime = start, endTime = end)
}

// Create ScheduledBlocks for fixed-time instances directly
for (instance in fixedTimeInstances) {
    val date = instance.instanceDate ?: continue
    val time = instance.fixedTime ?: continue
    val start = date.atTime(time).atZone(zoneId).toInstant()
    val end = start.plus(instance.estimatedDurationMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES)
    val block = ScheduledBlock(taskId = instance.id, startTime = start, endTime = end)
    val blockId = blockRepository.insert(block)
    taskRepository.updateStatus(instance.id, TaskStatus.SCHEDULED)
    syncManager.pushNewBlock(block.copy(id = blockId), instance.title)
}

// Add fixed-time slots to busySlots for the scheduler
val allBusySlots = busySlots + fixedTimeSlots

// Schedule flexible instances through the existing algorithm
if (flexibleInstances.isNotEmpty()) {
    val flexResult = taskScheduler.scheduleWithConflictResolution(
        tasks = flexibleInstances + otherPendingTasks,
        existingBlocks = existingBlocks,
        availability = availabilityList,
        busySlots = allBusySlots,
        startDate = today,
        endDate = today.plusDays(14),
        zoneId = zoneId,
    )
    // Handle flexResult same as existing save() flow
    // (Scheduled, NeedsReschedule, DeadlineAtRisk, NoSlotsAvailable)
}
```

**Note for implementer:** This is the core integration logic. The existing `save()` method's scheduling flow (handling `SchedulingResult` variants) can be reused for the flexible instances. The key additions are:
1. Partitioning by `fixedTime != null`
2. Creating blocks directly for fixed-time instances
3. Adding fixed-time slots to `busySlots` so the algorithm avoids them

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "feat: integrate fixed-time recurring instance placement in scheduler"
```

---

## Task 15: Run All Tests and Verify

**Files:** None (verification only)

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew test 2>&1 | tail -30`
Expected: All tests PASS (including new RecurrenceExpanderTest and RecurringTaskValidatorTest)

- [ ] **Step 2: Run build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Fix any issues found**

If tests fail or build breaks, fix issues and re-run.

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git commit -am "fix: resolve issues from integration testing"
```

---

## Implementation Notes

### Key Design Decisions

1. **`fixedTime` on `Task` model:** The `fixedTime: LocalTime?` field is added to `Task` (not just `RecurringTask`) so the scheduler can distinguish fixed-time from flexible instances without querying back to the template. For ad hoc tasks, this is always `null`.

2. **No foreign key annotation on `TaskEntity`:** SQLite cannot add foreign keys to existing tables via `ALTER TABLE`. The `ForeignKey` annotation is omitted from `TaskEntity` to avoid Room schema validation failures at runtime. Foreign key integrity is enforced at the application level.

3. **`LocalDate` stored as `Long` (epoch days):** The `Converters.kt` stores `LocalDate` as `Long` via `toEpochDay()/ofEpochDay()`. This differs from `LocalTime` which is stored as `String`. The `Long` representation is more compact and supports range queries in SQL.

4. **`CalendarGrid`/`TimeWheel` extraction:** These composables are extracted from `DeadlinePicker.kt` into shared files so `RecurringTaskFields` can reuse them. The extraction preserves the exact same visual behavior.
