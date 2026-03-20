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
