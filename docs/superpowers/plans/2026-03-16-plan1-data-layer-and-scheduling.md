# Plan 1: Data Layer + Scheduling Algorithm

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Room database, domain models, repositories, and on-device scheduling engine — the testable foundation of the Smart Task Scheduler app.

**Architecture:** Clean Architecture with three layers. This plan covers the Domain layer (models, scheduling logic, repository interfaces) and Data layer (Room entities, DAOs, type converters, repository implementations). All scheduling logic is pure Kotlin with no Android framework dependencies, enabling fast unit tests.

**Tech Stack:** Kotlin, Room, Hilt, JUnit 5, Kotlinx Coroutines Test, Turbine (for Flow testing)

**Spec:** `docs/superpowers/specs/2026-03-16-smart-task-scheduler-design.md`

---

## File Structure

```
app/
├── build.gradle.kts                          # Module-level Gradle config
├── src/
│   ├── main/java/com/tasktracker/
│   │   ├── TaskTrackerApplication.kt         # Hilt application class
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Quadrant.kt               # Eisenhower quadrant enum
│   │   │   │   ├── DayPreference.kt          # WEEKDAY, WEEKEND, ANY enum
│   │   │   │   ├── TaskStatus.kt             # PENDING, SCHEDULED, IN_PROGRESS, COMPLETED
│   │   │   │   ├── BlockStatus.kt            # PROPOSED, CONFIRMED, COMPLETED, CANCELLED
│   │   │   │   ├── Task.kt                   # Domain model
│   │   │   │   ├── ScheduledBlock.kt         # Domain model
│   │   │   │   ├── UserAvailability.kt       # Domain model
│   │   │   │   ├── CalendarSelection.kt      # Domain model
│   │   │   │   ├── TimeSlot.kt               # Available time window for scheduling
│   │   │   │   └── SchedulingResult.kt       # Result of scheduling attempt
│   │   │   ├── repository/
│   │   │   │   ├── TaskRepository.kt         # Interface
│   │   │   │   ├── ScheduledBlockRepository.kt
│   │   │   │   ├── UserAvailabilityRepository.kt
│   │   │   │   └── CalendarSelectionRepository.kt
│   │   │   ├── scheduler/
│   │   │   │   ├── TaskPriorityComparator.kt # Priority sort logic
│   │   │   │   ├── SlotFinder.kt             # Availability ∩ free/busy → open slots
│   │   │   │   └── TaskScheduler.kt          # Orchestrates scheduling
│   │   │   └── validation/
│   │   │       └── TaskValidator.kt          # Duration/deadline validation + ValidationResult sealed class
│   │   └── data/
│   │       ├── local/
│   │       │   ├── entity/
│   │       │   │   ├── TaskEntity.kt         # Room entity
│   │       │   │   ├── ScheduledBlockEntity.kt
│   │       │   │   ├── UserAvailabilityEntity.kt
│   │       │   │   └── CalendarSelectionEntity.kt
│   │       │   ├── dao/
│   │       │   │   ├── TaskDao.kt
│   │       │   │   ├── ScheduledBlockDao.kt
│   │       │   │   ├── UserAvailabilityDao.kt
│   │       │   │   └── CalendarSelectionDao.kt
│   │       │   ├── converter/
│   │       │   │   └── Converters.kt         # Room type converters for Instant, enums
│   │       │   └── TaskTrackerDatabase.kt    # Room database
│   │       └── repository/
│   │           ├── TaskRepositoryImpl.kt
│   │           ├── ScheduledBlockRepositoryImpl.kt
│   │           ├── UserAvailabilityRepositoryImpl.kt
│   │           └── CalendarSelectionRepositoryImpl.kt
│   │   └── di/
│   │       ├── DatabaseModule.kt             # Hilt module for Room DB and DAOs
│   │       └── RepositoryModule.kt           # Hilt module binding repo interfaces to impls
│   ├── test/java/com/tasktracker/           # Unit tests (JVM)
│   │   └── domain/
│   │       ├── scheduler/
│   │       │   ├── TaskPriorityComparatorTest.kt
│   │       │   ├── SlotFinderTest.kt
│   │       │   └── TaskSchedulerTest.kt
│   │       └── validation/
│   │           └── TaskValidatorTest.kt
│   └── androidTest/java/com/tasktracker/   # Instrumented tests
│       └── data/
│           └── local/
│               └── dao/
│                   ├── TaskDaoTest.kt
│                   └── ScheduledBlockDaoTest.kt
build.gradle.kts                              # Project-level Gradle config
settings.gradle.kts                           # Settings
gradle.properties                             # Gradle properties
```

---

## Task 1: Android Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (project-level)
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/tasktracker/TaskTrackerApplication.kt`

- [ ] **Step 1: Create project-level Gradle files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TaskTracker"
include(":app")
```

`build.gradle.kts` (project-level):
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.54" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 2: Create app-level build.gradle.kts**

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.tasktracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tasktracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.google.truth:truth:1.4.4")

    // Android instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("com.google.truth:truth:1.4.4")
}
```

- [ ] **Step 3: Create AndroidManifest.xml and Application class**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:name=".TaskTrackerApplication"
        android:allowBackup="true"
        android:label="Task Tracker"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DynamicColors.DayNight">
    </application>
</manifest>
```

`app/src/main/java/com/tasktracker/TaskTrackerApplication.kt`:
```kotlin
package com.tasktracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TaskTrackerApplication : Application()
```

- [ ] **Step 4: Add Gradle wrapper**

Run:
```bash
gradle wrapper --gradle-version 8.11.1
```

- [ ] **Step 5: Verify project compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold Android project with Room, Hilt, Compose dependencies"
```

---

## Task 2: Domain Model Enums

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/Quadrant.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/DayPreference.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/TaskStatus.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/BlockStatus.kt`

- [ ] **Step 1: Create all enum files**

`Quadrant.kt`:
```kotlin
package com.tasktracker.domain.model

enum class Quadrant(val priority: Int) {
    URGENT_IMPORTANT(0),
    IMPORTANT(1),
    URGENT(2),
    NEITHER(3);
}
```

`DayPreference.kt`:
```kotlin
package com.tasktracker.domain.model

enum class DayPreference {
    WEEKDAY,
    WEEKEND,
    ANY;
}
```

`TaskStatus.kt`:
```kotlin
package com.tasktracker.domain.model

enum class TaskStatus {
    PENDING,
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED;
}
```

`BlockStatus.kt`:
```kotlin
package com.tasktracker.domain.model

enum class BlockStatus {
    PROPOSED,
    CONFIRMED,
    COMPLETED,
    CANCELLED;
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/
git commit -m "feat: add domain model enums for task scheduling"
```

---

## Task 3: Domain Models

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/model/Task.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/ScheduledBlock.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/UserAvailability.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/CalendarSelection.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/TimeSlot.kt`
- Create: `app/src/main/java/com/tasktracker/domain/model/SchedulingResult.kt`

- [ ] **Step 1: Create Task domain model**

```kotlin
package com.tasktracker.domain.model

import java.time.Instant

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val recurringPattern: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
```

- [ ] **Step 2: Create ScheduledBlock domain model**

```kotlin
package com.tasktracker.domain.model

import java.time.Instant

data class ScheduledBlock(
    val id: Long = 0,
    val taskId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val googleCalendarEventId: String? = null,
    val status: BlockStatus = BlockStatus.CONFIRMED,
)
```

- [ ] **Step 3: Create UserAvailability domain model**

```kotlin
package com.tasktracker.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class UserAvailability(
    val id: Long = 0,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
)
```

- [ ] **Step 4: Create CalendarSelection domain model**

```kotlin
package com.tasktracker.domain.model

data class CalendarSelection(
    val id: Long = 0,
    val googleCalendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val enabled: Boolean = true,
)
```

- [ ] **Step 5: Create TimeSlot and SchedulingResult**

`TimeSlot.kt` — represents an available window the scheduler can fill:
```kotlin
package com.tasktracker.domain.model

import java.time.Instant
import java.time.Duration

data class TimeSlot(
    val startTime: Instant,
    val endTime: Instant,
) {
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}
```

`SchedulingResult.kt` — returned by the scheduler:
```kotlin
package com.tasktracker.domain.model

sealed class SchedulingResult {
    data class Scheduled(
        val blocks: List<ScheduledBlock>,
    ) : SchedulingResult()

    data class NeedsReschedule(
        val newBlocks: List<ScheduledBlock>,
        val movedBlocks: List<Pair<ScheduledBlock, ScheduledBlock>>,
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
```

- [ ] **Step 6: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/model/
git commit -m "feat: add domain models for tasks, blocks, availability, and scheduling"
```

---

## Task 4: Task Validator

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/validation/TaskValidatorTest.kt`
- Create: `app/src/main/java/com/tasktracker/domain/validation/TaskValidator.kt`

- [ ] **Step 1: Write failing tests for TaskValidator**

```kotlin
package com.tasktracker.domain.validation

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.DayOfWeek

class TaskValidatorTest {

    private val validator = TaskValidator()

    private val defaultAvailability = listOf(
        UserAvailability(
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            enabled = true,
        ),
    )

    private fun task(
        duration: Int = 60,
        splittable: Boolean = false,
    ) = Task(
        title = "Test",
        estimatedDurationMinutes = duration,
        quadrant = Quadrant.IMPORTANT,
        splittable = splittable,
    )

    @Test
    fun `valid task passes validation`() {
        val result = validator.validate(task(duration = 60), defaultAvailability)
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `duration below 15 minutes fails`() {
        val result = validator.validate(task(duration = 10), defaultAvailability)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason)
            .contains("15")
    }

    @Test
    fun `duration above 480 minutes fails`() {
        val result = validator.validate(task(duration = 500), defaultAvailability)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason)
            .contains("480")
    }

    @Test
    fun `duration not in 5-minute increments fails`() {
        val result = validator.validate(task(duration = 17), defaultAvailability)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((result as ValidationResult.Invalid).reason)
            .contains("5-minute")
    }

    @Test
    fun `non-splittable task exceeding longest window fails`() {
        val shortAvailability = listOf(
            UserAvailability(
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                enabled = true,
            ),
        )
        val result = validator.validate(
            task(duration = 120, splittable = false),
            shortAvailability,
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `splittable task exceeding longest window passes`() {
        val shortAvailability = listOf(
            UserAvailability(
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                enabled = true,
            ),
        )
        val result = validator.validate(
            task(duration = 120, splittable = true),
            shortAvailability,
        )
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }

    @Test
    fun `disabled availability windows are ignored`() {
        val availability = listOf(
            UserAvailability(
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                enabled = false,
            ),
            UserAvailability(
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 0),
                enabled = true,
            ),
        )
        val result = validator.validate(
            task(duration = 120, splittable = false),
            availability,
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.validation.TaskValidatorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement TaskValidator**

```kotlin
package com.tasktracker.domain.validation

import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.UserAvailability
import java.time.Duration

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

class TaskValidator {

    fun validate(task: Task, availability: List<UserAvailability>): ValidationResult {
        if (task.estimatedDurationMinutes < 15) {
            return ValidationResult.Invalid(
                "Duration must be at least 15 minutes."
            )
        }
        if (task.estimatedDurationMinutes > 480) {
            return ValidationResult.Invalid(
                "Duration must not exceed 480 minutes (8 hours)."
            )
        }
        if (task.estimatedDurationMinutes % 5 != 0) {
            return ValidationResult.Invalid(
                "Duration must be in 5-minute increments."
            )
        }
        if (!task.splittable) {
            val longestWindowMinutes = availability
                .filter { it.enabled }
                .maxOfOrNull {
                    Duration.between(it.startTime, it.endTime).toMinutes()
                } ?: 0L
            if (task.estimatedDurationMinutes > longestWindowMinutes) {
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

Run: `./gradlew test --tests "com.tasktracker.domain.validation.TaskValidatorTest"`
Expected: All 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/validation/ app/src/test/java/com/tasktracker/domain/validation/
git commit -m "feat: add task duration and constraint validation with tests"
```

---

## Task 5: TaskPriorityComparator

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/scheduler/TaskPriorityComparatorTest.kt`
- Create: `app/src/main/java/com/tasktracker/domain/scheduler/TaskPriorityComparator.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TaskPriorityComparatorTest {

    private val comparator = TaskPriorityComparator()
    private val now = Instant.parse("2026-03-16T10:00:00Z")

    private fun task(
        quadrant: Quadrant = Quadrant.IMPORTANT,
        deadline: Instant? = null,
        createdAt: Instant = now,
    ) = Task(
        title = "Test",
        estimatedDurationMinutes = 60,
        quadrant = quadrant,
        deadline = deadline,
        createdAt = createdAt,
    )

    @Test
    fun `URGENT_IMPORTANT sorts before IMPORTANT`() {
        val tasks = listOf(
            task(quadrant = Quadrant.IMPORTANT),
            task(quadrant = Quadrant.URGENT_IMPORTANT),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
        assertThat(sorted[1].quadrant).isEqualTo(Quadrant.IMPORTANT)
    }

    @Test
    fun `full quadrant ordering is correct`() {
        val tasks = listOf(
            task(quadrant = Quadrant.NEITHER),
            task(quadrant = Quadrant.URGENT),
            task(quadrant = Quadrant.URGENT_IMPORTANT),
            task(quadrant = Quadrant.IMPORTANT),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted.map { it.quadrant }).containsExactly(
            Quadrant.URGENT_IMPORTANT,
            Quadrant.IMPORTANT,
            Quadrant.URGENT,
            Quadrant.NEITHER,
        ).inOrder()
    }

    @Test
    fun `within same quadrant, nearer deadline sorts first`() {
        val tasks = listOf(
            task(deadline = now.plus(3, ChronoUnit.DAYS)),
            task(deadline = now.plus(1, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].deadline).isEqualTo(now.plus(1, ChronoUnit.DAYS))
    }

    @Test
    fun `tasks without deadlines sort after tasks with deadlines in same quadrant`() {
        val tasks = listOf(
            task(deadline = null),
            task(deadline = now.plus(5, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].deadline).isNotNull()
        assertThat(sorted[1].deadline).isNull()
    }

    @Test
    fun `tie-breaker is earlier createdAt`() {
        val early = now.minus(1, ChronoUnit.HOURS)
        val late = now
        val tasks = listOf(
            task(createdAt = late),
            task(createdAt = early),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].createdAt).isEqualTo(early)
        assertThat(sorted[1].createdAt).isEqualTo(late)
    }

    @Test
    fun `higher quadrant wins even with nearer deadline in lower quadrant`() {
        val tasks = listOf(
            task(quadrant = Quadrant.NEITHER, deadline = now.plus(1, ChronoUnit.HOURS)),
            task(quadrant = Quadrant.URGENT_IMPORTANT, deadline = now.plus(30, ChronoUnit.DAYS)),
        )
        val sorted = tasks.sortedWith(comparator)
        assertThat(sorted[0].quadrant).isEqualTo(Quadrant.URGENT_IMPORTANT)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskPriorityComparatorTest"`
Expected: FAIL

- [ ] **Step 3: Implement TaskPriorityComparator**

```kotlin
package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.Task

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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskPriorityComparatorTest"`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskPriorityComparator.kt app/src/test/java/com/tasktracker/domain/scheduler/
git commit -m "feat: add priority comparator for Eisenhower-based task sorting"
```

---

## Task 6: SlotFinder

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/scheduler/SlotFinderTest.kt`
- Create: `app/src/main/java/com/tasktracker/domain/scheduler/SlotFinder.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.*
import java.time.temporal.ChronoUnit

class SlotFinderTest {

    private val finder = SlotFinder()
    private val zoneId = ZoneId.of("America/New_York")

    // Monday 2026-03-16
    private val monday = LocalDate.of(2026, 3, 16)

    private fun availability(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(17, 0),
    ) = UserAvailability(
        dayOfWeek = day,
        startTime = start,
        endTime = end,
        enabled = true,
    )

    private fun busySlot(
        date: LocalDate = monday,
        startHour: Int,
        endHour: Int,
    ) = TimeSlot(
        startTime = date.atTime(startHour, 0).atZone(zoneId).toInstant(),
        endTime = date.atTime(endHour, 0).atZone(zoneId).toInstant(),
    )

    @Test
    fun `returns full window when no busy slots`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(1)
        assertThat(slots[0].durationMinutes).isEqualTo(480) // 9am-5pm
    }

    @Test
    fun `subtracts busy slot from middle of window`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(busySlot(startHour = 12, endHour = 13)),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(2)
        assertThat(slots[0].durationMinutes).isEqualTo(180) // 9-12
        assertThat(slots[1].durationMinutes).isEqualTo(240) // 1-5
    }

    @Test
    fun `busy slot at start of window trims correctly`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(busySlot(startHour = 9, endHour = 11)),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(1)
        assertThat(slots[0].durationMinutes).isEqualTo(360) // 11am-5pm
    }

    @Test
    fun `WEEKDAY preference excludes weekends`() {
        val saturday = monday.plusDays(5) // 2026-03-21
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SATURDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = saturday,
            dayPreference = DayPreference.WEEKDAY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(1) // Only Monday
    }

    @Test
    fun `WEEKEND preference excludes weekdays`() {
        val sunday = monday.plusDays(6) // 2026-03-22
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SUNDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = sunday,
            dayPreference = DayPreference.WEEKEND,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(1) // Only Sunday
    }

    @Test
    fun `disabled availability windows are excluded`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability().copy(enabled = false),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).isEmpty()
    }

    @Test
    fun `multiple busy slots carve out multiple gaps`() {
        val slots = finder.findAvailableSlots(
            availability = listOf(availability()),
            busySlots = listOf(
                busySlot(startHour = 10, endHour = 11),
                busySlot(startHour = 14, endHour = 15),
            ),
            startDate = monday,
            endDate = monday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(3)
        assertThat(slots[0].durationMinutes).isEqualTo(60)  // 9-10
        assertThat(slots[1].durationMinutes).isEqualTo(180) // 11-14
        assertThat(slots[2].durationMinutes).isEqualTo(120) // 15-17
    }

    @Test
    fun `slots are sorted chronologically`() {
        val tuesday = monday.plusDays(1)
        val slots = finder.findAvailableSlots(
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.TUESDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = tuesday,
            dayPreference = DayPreference.ANY,
            zoneId = zoneId,
        )
        assertThat(slots).hasSize(2)
        assertThat(slots[0].startTime).isLessThan(slots[1].startTime)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.SlotFinderTest"`
Expected: FAIL

- [ ] **Step 3: Implement SlotFinder**

```kotlin
package com.tasktracker.domain.scheduler

import com.tasktracker.domain.model.DayPreference
import com.tasktracker.domain.model.TimeSlot
import com.tasktracker.domain.model.UserAvailability
import java.time.*

class SlotFinder {

    fun findAvailableSlots(
        availability: List<UserAvailability>,
        busySlots: List<TimeSlot>,
        startDate: LocalDate,
        endDate: LocalDate,
        dayPreference: DayPreference,
        zoneId: ZoneId,
    ): List<TimeSlot> {
        val result = mutableListOf<TimeSlot>()
        val enabledAvailability = availability.filter { it.enabled }

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek
            if (!matchesDayPreference(dayOfWeek, dayPreference)) {
                currentDate = currentDate.plusDays(1)
                continue
            }

            val windowsForDay = enabledAvailability.filter { it.dayOfWeek == dayOfWeek }
            for (window in windowsForDay) {
                val windowStart = currentDate.atTime(window.startTime)
                    .atZone(zoneId).toInstant()
                val windowEnd = currentDate.atTime(window.endTime)
                    .atZone(zoneId).toInstant()

                val freeSlots = subtractBusySlots(
                    windowStart, windowEnd, busySlots
                )
                result.addAll(freeSlots)
            }
            currentDate = currentDate.plusDays(1)
        }

        return result.sortedBy { it.startTime }
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

    private fun subtractBusySlots(
        windowStart: Instant,
        windowEnd: Instant,
        busySlots: List<TimeSlot>,
    ): List<TimeSlot> {
        val relevant = busySlots
            .filter { it.startTime < windowEnd && it.endTime > windowStart }
            .sortedBy { it.startTime }

        if (relevant.isEmpty()) {
            return listOf(TimeSlot(windowStart, windowEnd))
        }

        val freeSlots = mutableListOf<TimeSlot>()
        var cursor = windowStart

        for (busy in relevant) {
            val busyStart = maxOf(busy.startTime, windowStart)
            if (cursor < busyStart) {
                freeSlots.add(TimeSlot(cursor, busyStart))
            }
            cursor = maxOf(cursor, minOf(busy.endTime, windowEnd))
        }

        if (cursor < windowEnd) {
            freeSlots.add(TimeSlot(cursor, windowEnd))
        }

        return freeSlots
    }

    private fun maxOf(a: Instant, b: Instant): Instant = if (a > b) a else b
    private fun minOf(a: Instant, b: Instant): Instant = if (a < b) a else b
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.SlotFinderTest"`
Expected: All 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/SlotFinder.kt app/src/test/java/com/tasktracker/domain/scheduler/SlotFinderTest.kt
git commit -m "feat: add slot finder that computes available time windows"
```

---

## Task 7: TaskScheduler — Core Scheduling Engine

**Files:**
- Create: `app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt`
- Create: `app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt`

- [ ] **Step 1: Write failing tests for basic scheduling**

```kotlin
package com.tasktracker.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import org.junit.Test
import java.time.*
import java.time.temporal.ChronoUnit

class TaskSchedulerTest {

    private val scheduler = TaskScheduler(
        priorityComparator = TaskPriorityComparator(),
        slotFinder = SlotFinder(),
    )
    private val zoneId = ZoneId.of("America/New_York")
    private val monday = LocalDate.of(2026, 3, 16)

    private fun availability(
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: LocalTime = LocalTime.of(9, 0),
        end: LocalTime = LocalTime.of(17, 0),
    ) = UserAvailability(dayOfWeek = day, startTime = start, endTime = end)

    private fun task(
        id: Long = 1,
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
        deadline: Instant? = null,
        splittable: Boolean = false,
        dayPreference: DayPreference = DayPreference.ANY,
        createdAt: Instant = Instant.parse("2026-03-16T00:00:00Z"),
    ) = Task(
        id = id,
        title = "Task $id",
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        deadline = deadline,
        splittable = splittable,
        dayPreference = dayPreference,
        createdAt = createdAt,
    )

    @Test
    fun `schedules single task into first available slot`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].taskId).isEqualTo(1)
        // Should start at 9am
        val expectedStart = monday.atTime(9, 0).atZone(zoneId).toInstant()
        assertThat(blocks[0].startTime).isEqualTo(expectedStart)
        assertThat(blocks[0].endTime).isEqualTo(expectedStart.plus(60, ChronoUnit.MINUTES))
    }

    @Test
    fun `best-fit - smaller task fills small slot instead of leaving it empty`() {
        // 1-hour slot (9-10), then busy (10-12), then open (12-5)
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(12, 0).atZone(zoneId).toInstant(),
            ),
        )
        val bigTask = task(id = 1, duration = 120, quadrant = Quadrant.URGENT_IMPORTANT)
        val smallTask = task(id = 2, duration = 60, quadrant = Quadrant.IMPORTANT)

        val result = scheduler.schedule(
            tasks = listOf(bigTask, smallTask),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks

        // Small task should be in 9-10 slot, big task in 12-2 slot
        val smallBlock = blocks.find { it.taskId == 2L }!!
        val bigBlock = blocks.find { it.taskId == 1L }!!

        val nineAm = monday.atTime(9, 0).atZone(zoneId).toInstant()
        val noon = monday.atTime(12, 0).atZone(zoneId).toInstant()

        assertThat(smallBlock.startTime).isEqualTo(nineAm)
        assertThat(bigBlock.startTime).isEqualTo(noon)
    }

    @Test
    fun `splittable task splits across multiple slots`() {
        // Two 1-hour slots with a busy gap between
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(14, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 120, splittable = true)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(2)
        assertThat(blocks.sumOf { it.endTime.epochSecond - it.startTime.epochSecond })
            .isEqualTo(7200) // 120 minutes total
    }

    @Test
    fun `splittable task does not create blocks smaller than 30 minutes`() {
        // 20-min slot then big slot
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 20).atZone(zoneId).toInstant(),
                monday.atTime(12, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, splittable = true)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        // 9:00-9:20 is only 20 min — too small for 30-min minimum
        // Should schedule full 60 min at 12:00
        assertThat(blocks).hasSize(1)
        val noon = monday.atTime(12, 0).atZone(zoneId).toInstant()
        assertThat(blocks[0].startTime).isEqualTo(noon)
    }

    @Test
    fun `respects deadline - only uses slots before deadline`() {
        val tuesday = monday.plusDays(1)
        val deadlineInstant = monday.atTime(12, 0).atZone(zoneId).toInstant()

        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, deadline = deadlineInstant)),
            existingBlocks = emptyList(),
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.TUESDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = tuesday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].endTime).isAtMost(deadlineInstant)
    }

    @Test
    fun `returns NoSlotsAvailable when task cannot fit`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = emptyList(),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.NoSlotsAvailable::class.java)
    }

    @Test
    fun `returns DeadlineAtRisk when task cannot fit before deadline`() {
        val deadlineInstant = monday.atTime(10, 0).atZone(zoneId).toInstant()
        // Busy from 9-10, deadline at 10 → no room
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 0).atZone(zoneId).toInstant(),
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, deadline = deadlineInstant)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.DeadlineAtRisk::class.java)
    }

    @Test
    fun `blocks are created with CONFIRMED status`() {
        val result = scheduler.schedule(
            tasks = listOf(task(duration = 60)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = emptyList(),
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks.all { it.status == BlockStatus.CONFIRMED }).isTrue()
    }

    @Test
    fun `schedules multiple tasks across slots by best-fit`() {
        // 3 tasks of varying sizes, 2 slots: 1-hour (9-10) and 3-hour (11-14)
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(10, 0).atZone(zoneId).toInstant(),
                monday.atTime(11, 0).atZone(zoneId).toInstant(),
            ),
        )
        val tasks = listOf(
            task(id = 1, duration = 120, quadrant = Quadrant.URGENT_IMPORTANT),
            task(id = 2, duration = 60, quadrant = Quadrant.IMPORTANT),
            task(id = 3, duration = 30, quadrant = Quadrant.URGENT),
        )
        val result = scheduler.schedule(
            tasks = tasks,
            existingBlocks = emptyList(),
            availability = listOf(availability(end = LocalTime.of(14, 0))),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(3)
        // All 3 tasks should be scheduled
        assertThat(blocks.map { it.taskId }.toSet()).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `respects WEEKDAY day preference in scheduler`() {
        val saturday = monday.plusDays(5) // 2026-03-21
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, dayPreference = DayPreference.WEEKDAY)),
            existingBlocks = emptyList(),
            availability = listOf(
                availability(day = DayOfWeek.MONDAY),
                availability(day = DayOfWeek.SATURDAY),
            ),
            busySlots = emptyList(),
            startDate = monday,
            endDate = saturday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.Scheduled::class.java)
        val blocks = (result as SchedulingResult.Scheduled).blocks
        assertThat(blocks).hasSize(1)
        val scheduledDay = blocks[0].startTime.atZone(zoneId).dayOfWeek
        assertThat(scheduledDay).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `non-splittable task exceeding all slots returns NoSlotsAvailable with suggestion`() {
        // Only a 30-min slot available, task needs 60 min
        val busySlots = listOf(
            TimeSlot(
                monday.atTime(9, 30).atZone(zoneId).toInstant(),
                monday.atTime(17, 0).atZone(zoneId).toInstant(),
            ),
        )
        val result = scheduler.schedule(
            tasks = listOf(task(id = 1, duration = 60, splittable = false)),
            existingBlocks = emptyList(),
            availability = listOf(availability()),
            busySlots = busySlots,
            startDate = monday,
            endDate = monday,
            zoneId = zoneId,
        )
        assertThat(result).isInstanceOf(SchedulingResult.NoSlotsAvailable::class.java)
        assertThat((result as SchedulingResult.NoSlotsAvailable).message)
            .contains("splittable")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest"`
Expected: FAIL

- [ ] **Step 3: Implement TaskScheduler**

```kotlin
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
            val slot = slotsToProcess.removeFirst()
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
            // If we scheduled some tasks but not all, still return what we have
            // along with the failure. For v1, we return NoSlotsAvailable for the
            // first unscheduled task. NeedsReschedule (conflict resolution with
            // displacement of lower-priority tasks) will be implemented in Plan 2
            // alongside Google Calendar sync, where the full reschedule-and-confirm
            // flow requires calendar event manipulation.
            if (resultBlocks.isEmpty()) {
                return SchedulingResult.NoSlotsAvailable(
                    task = unscheduled,
                    message = message,
                )
            }
        }

        return SchedulingResult.Scheduled(resultBlocks)
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest"`
Expected: All 11 tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/scheduler/TaskScheduler.kt app/src/test/java/com/tasktracker/domain/scheduler/TaskSchedulerTest.kt
git commit -m "feat: add core task scheduler with slot-centric best-fit algorithm"
```

---

## Task 8: Room Entities and Type Converters

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/local/entity/TaskEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/entity/ScheduledBlockEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/entity/UserAvailabilityEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/entity/CalendarSelectionEntity.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/converter/Converters.kt`

- [ ] **Step 1: Create type converters**

```kotlin
package com.tasktracker.data.local.converter

import androidx.room.TypeConverter
import com.tasktracker.domain.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun toDayOfWeek(value: Int?): DayOfWeek? = value?.let { DayOfWeek.of(it) }

    @TypeConverter
    fun fromQuadrant(value: Quadrant?): String? = value?.name

    @TypeConverter
    fun toQuadrant(value: String?): Quadrant? = value?.let { Quadrant.valueOf(it) }

    @TypeConverter
    fun fromDayPreference(value: DayPreference?): String? = value?.name

    @TypeConverter
    fun toDayPreference(value: String?): DayPreference? = value?.let { DayPreference.valueOf(it) }

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus?): String? = value?.name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus? = value?.let { TaskStatus.valueOf(it) }

    @TypeConverter
    fun fromBlockStatus(value: BlockStatus?): String? = value?.name

    @TypeConverter
    fun toBlockStatus(value: String?): BlockStatus? = value?.let { BlockStatus.valueOf(it) }
}
```

- [ ] **Step 2: Create TaskEntity**

```kotlin
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.*
import java.time.Instant

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val estimatedDurationMinutes: Int,
    val quadrant: Quadrant,
    val deadline: Instant? = null,
    val dayPreference: DayPreference = DayPreference.ANY,
    val splittable: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val recurringPattern: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toDomain() = Task(
        id = id,
        title = title,
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        quadrant = quadrant,
        deadline = deadline,
        dayPreference = dayPreference,
        splittable = splittable,
        status = status,
        recurringPattern = recurringPattern,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: Task) = TaskEntity(
            id = task.id,
            title = task.title,
            description = task.description,
            estimatedDurationMinutes = task.estimatedDurationMinutes,
            quadrant = task.quadrant,
            deadline = task.deadline,
            dayPreference = task.dayPreference,
            splittable = task.splittable,
            status = task.status,
            recurringPattern = task.recurringPattern,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
```

- [ ] **Step 3: Create ScheduledBlockEntity**

```kotlin
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import java.time.Instant

@Entity(
    tableName = "scheduled_blocks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class ScheduledBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val googleCalendarEventId: String? = null,
    val status: BlockStatus = BlockStatus.CONFIRMED,
) {
    fun toDomain() = ScheduledBlock(
        id = id,
        taskId = taskId,
        startTime = startTime,
        endTime = endTime,
        googleCalendarEventId = googleCalendarEventId,
        status = status,
    )

    companion object {
        fun fromDomain(block: ScheduledBlock) = ScheduledBlockEntity(
            id = block.id,
            taskId = block.taskId,
            startTime = block.startTime,
            endTime = block.endTime,
            googleCalendarEventId = block.googleCalendarEventId,
            status = block.status,
        )
    }
}
```

- [ ] **Step 4: Create UserAvailabilityEntity**

```kotlin
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.UserAvailability
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "user_availability")
data class UserAvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
) {
    fun toDomain() = UserAvailability(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(ua: UserAvailability) = UserAvailabilityEntity(
            id = ua.id,
            dayOfWeek = ua.dayOfWeek,
            startTime = ua.startTime,
            endTime = ua.endTime,
            enabled = ua.enabled,
        )
    }
}
```

- [ ] **Step 5: Create CalendarSelectionEntity**

```kotlin
package com.tasktracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tasktracker.domain.model.CalendarSelection

@Entity(tableName = "calendar_selections")
data class CalendarSelectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val googleCalendarId: String,
    val calendarName: String,
    val calendarColor: String,
    val enabled: Boolean = true,
) {
    fun toDomain() = CalendarSelection(
        id = id,
        googleCalendarId = googleCalendarId,
        calendarName = calendarName,
        calendarColor = calendarColor,
        enabled = enabled,
    )

    companion object {
        fun fromDomain(cs: CalendarSelection) = CalendarSelectionEntity(
            id = cs.id,
            googleCalendarId = cs.googleCalendarId,
            calendarName = cs.calendarName,
            calendarColor = cs.calendarColor,
            enabled = cs.enabled,
        )
    }
}
```

- [ ] **Step 6: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/local/
git commit -m "feat: add Room entities and type converters"
```

---

## Task 9: DAOs

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/local/dao/TaskDao.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/ScheduledBlockDao.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/UserAvailabilityDao.kt`
- Create: `app/src/main/java/com/tasktracker/data/local/dao/CalendarSelectionDao.kt`

- [ ] **Step 1: Create TaskDao**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status")
    suspend fun getByStatus(status: TaskStatus): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<TaskStatus>): List<TaskEntity>

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, updatedAt: Long)
}
```

- [ ] **Step 2: Create ScheduledBlockDao**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.domain.model.BlockStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledBlockDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: ScheduledBlockEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<ScheduledBlockEntity>): List<Long>

    @Update
    suspend fun update(block: ScheduledBlockEntity)

    @Delete
    suspend fun delete(block: ScheduledBlockEntity)

    @Query("SELECT * FROM scheduled_blocks WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: Long): List<ScheduledBlockEntity>

    @Query("SELECT * FROM scheduled_blocks WHERE taskId = :taskId")
    fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlockEntity>>

    @Query("SELECT * FROM scheduled_blocks WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlockEntity>

    @Query("SELECT * FROM scheduled_blocks WHERE startTime >= :start AND endTime <= :end")
    fun observeInRange(start: Long, end: Long): Flow<List<ScheduledBlockEntity>>

    @Query("UPDATE scheduled_blocks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: BlockStatus)

    @Query("DELETE FROM scheduled_blocks WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("DELETE FROM scheduled_blocks WHERE status = :status")
    suspend fun deleteByStatus(status: BlockStatus)
}
```

- [ ] **Step 3: Create UserAvailabilityDao**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.UserAvailabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAvailabilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(availability: UserAvailabilityEntity): Long

    @Update
    suspend fun update(availability: UserAvailabilityEntity)

    @Delete
    suspend fun delete(availability: UserAvailabilityEntity)

    @Query("SELECT * FROM user_availability ORDER BY dayOfWeek, startTime")
    fun observeAll(): Flow<List<UserAvailabilityEntity>>

    @Query("SELECT * FROM user_availability WHERE enabled = 1 ORDER BY dayOfWeek, startTime")
    suspend fun getEnabled(): List<UserAvailabilityEntity>

    @Query("SELECT * FROM user_availability ORDER BY dayOfWeek, startTime")
    suspend fun getAll(): List<UserAvailabilityEntity>
}
```

- [ ] **Step 4: Create CalendarSelectionDao**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.CalendarSelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSelectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selection: CalendarSelectionEntity): Long

    @Update
    suspend fun update(selection: CalendarSelectionEntity)

    @Delete
    suspend fun delete(selection: CalendarSelectionEntity)

    @Query("SELECT * FROM calendar_selections ORDER BY calendarName")
    fun observeAll(): Flow<List<CalendarSelectionEntity>>

    @Query("SELECT * FROM calendar_selections WHERE enabled = 1")
    suspend fun getEnabled(): List<CalendarSelectionEntity>
}
```

- [ ] **Step 5: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/local/dao/
git commit -m "feat: add Room DAOs for all entities"
```

---

## Task 10: Room Database

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt`

- [ ] **Step 1: Create the database class**

```kotlin
package com.tasktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tasktracker.data.local.converter.Converters
import com.tasktracker.data.local.dao.*
import com.tasktracker.data.local.entity.*

@Database(
    entities = [
        TaskEntity::class,
        ScheduledBlockEntity::class,
        UserAvailabilityEntity::class,
        CalendarSelectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao
    abstract fun userAvailabilityDao(): UserAvailabilityDao
    abstract fun calendarSelectionDao(): CalendarSelectionDao
}
```

- [ ] **Step 2: Verify project compiles (Room annotation processing)**

Run: `./gradlew compileDebugKotlin kspDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/local/TaskTrackerDatabase.kt
git commit -m "feat: add Room database with all entities and DAOs"
```

---

## Task 11: Repository Interfaces

**Files:**
- Create: `app/src/main/java/com/tasktracker/domain/repository/TaskRepository.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/ScheduledBlockRepository.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/UserAvailabilityRepository.kt`
- Create: `app/src/main/java/com/tasktracker/domain/repository/CalendarSelectionRepository.kt`

- [ ] **Step 1: Create all repository interfaces**

`TaskRepository.kt`:
```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun getById(id: Long): Task?
    fun observeAll(): Flow<List<Task>>
    suspend fun getByStatus(status: TaskStatus): List<Task>
    suspend fun getByStatuses(statuses: List<TaskStatus>): List<Task>
    suspend fun updateStatus(id: Long, status: TaskStatus)
}
```

`ScheduledBlockRepository.kt`:
```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ScheduledBlockRepository {
    suspend fun insert(block: ScheduledBlock): Long
    suspend fun insertAll(blocks: List<ScheduledBlock>): List<Long>
    suspend fun update(block: ScheduledBlock)
    suspend fun delete(block: ScheduledBlock)
    suspend fun getByTaskId(taskId: Long): List<ScheduledBlock>
    fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlock>>
    suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlock>
    fun observeInRange(start: Instant, end: Instant): Flow<List<ScheduledBlock>>
    suspend fun updateStatus(id: Long, status: BlockStatus)
    suspend fun deleteByTaskId(taskId: Long)
    suspend fun deleteProposed()
}
```

`UserAvailabilityRepository.kt`:
```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.UserAvailability
import kotlinx.coroutines.flow.Flow

interface UserAvailabilityRepository {
    suspend fun insert(availability: UserAvailability): Long
    suspend fun update(availability: UserAvailability)
    suspend fun delete(availability: UserAvailability)
    fun observeAll(): Flow<List<UserAvailability>>
    suspend fun getEnabled(): List<UserAvailability>
    suspend fun getAll(): List<UserAvailability>
}
```

`CalendarSelectionRepository.kt`:
```kotlin
package com.tasktracker.domain.repository

import com.tasktracker.domain.model.CalendarSelection
import kotlinx.coroutines.flow.Flow

interface CalendarSelectionRepository {
    suspend fun insert(selection: CalendarSelection): Long
    suspend fun update(selection: CalendarSelection)
    suspend fun delete(selection: CalendarSelection)
    fun observeAll(): Flow<List<CalendarSelection>>
    suspend fun getEnabled(): List<CalendarSelection>
}
```

- [ ] **Step 2: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/domain/repository/
git commit -m "feat: add repository interfaces for domain layer"
```

---

## Task 12: Repository Implementations

**Files:**
- Create: `app/src/main/java/com/tasktracker/data/repository/TaskRepositoryImpl.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/ScheduledBlockRepositoryImpl.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/UserAvailabilityRepositoryImpl.kt`
- Create: `app/src/main/java/com/tasktracker/data/repository/CalendarSelectionRepositoryImpl.kt`

- [ ] **Step 1: Create TaskRepositoryImpl**

```kotlin
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.TaskDao
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
) : TaskRepository {

    override suspend fun insert(task: Task): Long =
        taskDao.insert(TaskEntity.fromDomain(task))

    override suspend fun update(task: Task) =
        taskDao.update(TaskEntity.fromDomain(task.copy(updatedAt = Instant.now())))

    override suspend fun delete(task: Task) =
        taskDao.delete(TaskEntity.fromDomain(task))

    override suspend fun getById(id: Long): Task? =
        taskDao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<Task>> =
        taskDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByStatus(status: TaskStatus): List<Task> =
        taskDao.getByStatus(status).map { it.toDomain() }

    override suspend fun getByStatuses(statuses: List<TaskStatus>): List<Task> =
        taskDao.getByStatuses(statuses).map { it.toDomain() }

    override suspend fun updateStatus(id: Long, status: TaskStatus) =
        taskDao.updateStatus(id, status, Instant.now().toEpochMilli())
}
```

- [ ] **Step 2: Create ScheduledBlockRepositoryImpl**

```kotlin
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.ScheduledBlockDao
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.model.ScheduledBlock
import com.tasktracker.domain.repository.ScheduledBlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class ScheduledBlockRepositoryImpl @Inject constructor(
    private val dao: ScheduledBlockDao,
) : ScheduledBlockRepository {

    override suspend fun insert(block: ScheduledBlock): Long =
        dao.insert(ScheduledBlockEntity.fromDomain(block))

    override suspend fun insertAll(blocks: List<ScheduledBlock>): List<Long> =
        dao.insertAll(blocks.map { ScheduledBlockEntity.fromDomain(it) })

    override suspend fun update(block: ScheduledBlock) =
        dao.update(ScheduledBlockEntity.fromDomain(block))

    override suspend fun delete(block: ScheduledBlock) =
        dao.delete(ScheduledBlockEntity.fromDomain(block))

    override suspend fun getByTaskId(taskId: Long): List<ScheduledBlock> =
        dao.getByTaskId(taskId).map { it.toDomain() }

    override fun observeByTaskId(taskId: Long): Flow<List<ScheduledBlock>> =
        dao.observeByTaskId(taskId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getByStatuses(statuses: List<BlockStatus>): List<ScheduledBlock> =
        dao.getByStatuses(statuses).map { it.toDomain() }

    override fun observeInRange(start: Instant, end: Instant): Flow<List<ScheduledBlock>> =
        dao.observeInRange(start.toEpochMilli(), end.toEpochMilli())
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun updateStatus(id: Long, status: BlockStatus) =
        dao.updateStatus(id, status)

    override suspend fun deleteByTaskId(taskId: Long) =
        dao.deleteByTaskId(taskId)

    override suspend fun deleteProposed() =
        dao.deleteByStatus(BlockStatus.PROPOSED)
}
```

- [ ] **Step 3: Create UserAvailabilityRepositoryImpl**

```kotlin
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.UserAvailabilityDao
import com.tasktracker.data.local.entity.UserAvailabilityEntity
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.UserAvailabilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserAvailabilityRepositoryImpl @Inject constructor(
    private val dao: UserAvailabilityDao,
) : UserAvailabilityRepository {

    override suspend fun insert(availability: UserAvailability): Long =
        dao.insert(UserAvailabilityEntity.fromDomain(availability))

    override suspend fun update(availability: UserAvailability) =
        dao.update(UserAvailabilityEntity.fromDomain(availability))

    override suspend fun delete(availability: UserAvailability) =
        dao.delete(UserAvailabilityEntity.fromDomain(availability))

    override fun observeAll(): Flow<List<UserAvailability>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabled(): List<UserAvailability> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun getAll(): List<UserAvailability> =
        dao.getAll().map { it.toDomain() }
}
```

- [ ] **Step 4: Create CalendarSelectionRepositoryImpl**

```kotlin
package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.CalendarSelectionDao
import com.tasktracker.data.local.entity.CalendarSelectionEntity
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.repository.CalendarSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CalendarSelectionRepositoryImpl @Inject constructor(
    private val dao: CalendarSelectionDao,
) : CalendarSelectionRepository {

    override suspend fun insert(selection: CalendarSelection): Long =
        dao.insert(CalendarSelectionEntity.fromDomain(selection))

    override suspend fun update(selection: CalendarSelection) =
        dao.update(CalendarSelectionEntity.fromDomain(selection))

    override suspend fun delete(selection: CalendarSelection) =
        dao.delete(CalendarSelectionEntity.fromDomain(selection))

    override fun observeAll(): Flow<List<CalendarSelection>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabled(): List<CalendarSelection> =
        dao.getEnabled().map { it.toDomain() }
}
```

- [ ] **Step 5: Verify project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/repository/
git commit -m "feat: add repository implementations backed by Room DAOs"
```

---

## Task 13: Hilt Dependency Injection Module

**Files:**
- Create: `app/src/main/java/com/tasktracker/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/tasktracker/di/RepositoryModule.kt`

- [ ] **Step 1: Create DatabaseModule**

```kotlin
package com.tasktracker.di

import android.content.Context
import androidx.room.Room
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskTrackerDatabase =
        Room.databaseBuilder(
            context,
            TaskTrackerDatabase::class.java,
            "task_tracker.db",
        ).build()

    @Provides
    fun provideTaskDao(db: TaskTrackerDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideScheduledBlockDao(db: TaskTrackerDatabase): ScheduledBlockDao = db.scheduledBlockDao()

    @Provides
    fun provideUserAvailabilityDao(db: TaskTrackerDatabase): UserAvailabilityDao = db.userAvailabilityDao()

    @Provides
    fun provideCalendarSelectionDao(db: TaskTrackerDatabase): CalendarSelectionDao = db.calendarSelectionDao()
}
```

- [ ] **Step 2: Create RepositoryModule**

```kotlin
package com.tasktracker.di

import com.tasktracker.data.repository.*
import com.tasktracker.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindScheduledBlockRepository(impl: ScheduledBlockRepositoryImpl): ScheduledBlockRepository

    @Binds
    @Singleton
    abstract fun bindUserAvailabilityRepository(impl: UserAvailabilityRepositoryImpl): UserAvailabilityRepository

    @Binds
    @Singleton
    abstract fun bindCalendarSelectionRepository(impl: CalendarSelectionRepositoryImpl): CalendarSelectionRepository
}
```

- [ ] **Step 3: Verify project compiles (full Hilt + Room annotation processing)**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/di/
git commit -m "feat: add Hilt DI modules for database and repositories"
```

---

## Task 14: DAO Instrumented Tests

**Files:**
- Create: `app/src/androidTest/java/com/tasktracker/data/local/dao/TaskDaoTest.kt`
- Create: `app/src/androidTest/java/com/tasktracker/data/local/dao/ScheduledBlockDaoTest.kt`

These run on a device/emulator to verify Room integration.

- [ ] **Step 1: Write TaskDaoTest**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: TaskTrackerDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(
        title: String = "Test",
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
    ) = TaskEntity(
        title = title,
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun insertAndRetrieve() = runTest {
        val id = dao.insert(entity(title = "My Task"))
        val result = dao.getById(id)
        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("My Task")
    }

    @Test
    fun observeAllEmitsUpdates() = runTest {
        dao.insert(entity(title = "A"))
        dao.insert(entity(title = "B"))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun getByStatusFiltersCorrectly() = runTest {
        dao.insert(entity().copy(status = TaskStatus.PENDING))
        dao.insert(entity().copy(status = TaskStatus.COMPLETED))
        val pending = dao.getByStatus(TaskStatus.PENDING)
        assertThat(pending).hasSize(1)
    }

    @Test
    fun updateStatusChangesStatus() = runTest {
        val id = dao.insert(entity())
        dao.updateStatus(id, TaskStatus.SCHEDULED, Instant.now().toEpochMilli())
        val result = dao.getById(id)
        assertThat(result!!.status).isEqualTo(TaskStatus.SCHEDULED)
    }

    @Test
    fun deleteRemovesTask() = runTest {
        val e = entity()
        val id = dao.insert(e)
        dao.delete(e.copy(id = id))
        assertThat(dao.getById(id)).isNull()
    }
}
```

- [ ] **Step 2: Write ScheduledBlockDaoTest**

```kotlin
package com.tasktracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ScheduledBlockDaoTest {

    private lateinit var db: TaskTrackerDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var blockDao: ScheduledBlockDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
        blockDao = db.scheduledBlockDao()
    }

    @After
    fun teardown() { db.close() }

    private val now = Instant.now()

    @Test
    fun insertAndRetrieveByTaskId() = runTest {
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Test",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                createdAt = now,
                updatedAt = now,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
            )
        )
        val blocks = blockDao.getByTaskId(taskId)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].taskId).isEqualTo(taskId)
    }

    @Test
    fun cascadeDeleteRemovesBlocks() = runTest {
        val task = TaskEntity(
            title = "Test",
            estimatedDurationMinutes = 60,
            quadrant = Quadrant.IMPORTANT,
            createdAt = now,
            updatedAt = now,
        )
        val taskId = taskDao.insert(task)
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
            )
        )
        taskDao.delete(task.copy(id = taskId))
        val blocks = blockDao.getByTaskId(taskId)
        assertThat(blocks).isEmpty()
    }

    @Test
    fun deleteByStatusRemovesCorrectBlocks() = runTest {
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Test",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                createdAt = now,
                updatedAt = now,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
                status = BlockStatus.PROPOSED,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now.plus(2, ChronoUnit.HOURS),
                endTime = now.plus(3, ChronoUnit.HOURS),
                status = BlockStatus.CONFIRMED,
            )
        )
        blockDao.deleteByStatus(BlockStatus.PROPOSED)
        val remaining = blockDao.getByTaskId(taskId)
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].status).isEqualTo(BlockStatus.CONFIRMED)
    }
}
```

- [ ] **Step 3: Run instrumented tests**

Run: `./gradlew connectedDebugAndroidTest --tests "com.tasktracker.data.local.dao.*"`
Expected: All tests PASS (requires emulator or connected device)

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/
git commit -m "test: add instrumented tests for Room DAOs"
```

---

## Summary

After completing all 14 tasks, you will have:

- A compilable Android project with Room, Hilt, and Compose dependencies
- Complete domain models with enums, validation, and scheduling result types
- A priority comparator implementing Eisenhower matrix sorting with deadline and tie-breaker rules
- A slot finder that computes available time windows from availability and busy slots
- A task scheduler implementing the slot-centric best-fit algorithm with splitting support
- Room entities, DAOs, and type converters for all 4 data types
- Repository interfaces (domain) and implementations (data) with Hilt DI
- Unit tests for validator, comparator, slot finder, and scheduler
- Instrumented tests for DAO operations

**Next plan:** Plan 2 — Google Calendar Integration (auth, API client, sync layer)
