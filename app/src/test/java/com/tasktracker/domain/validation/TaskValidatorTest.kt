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
        AvailabilitySlot(
            slotType = AvailabilitySlotType.DURING_WORK,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            enabled = true,
        ),
    )

    private fun task(
        duration: Int = 60,
        splittable: Boolean = false,
        availabilitySlot: AvailabilitySlotType? = null,
    ) = Task(
        title = "Test",
        estimatedDurationMinutes = duration,
        quadrant = Quadrant.IMPORTANT,
        splittable = splittable,
        availabilitySlot = availabilitySlot,
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
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
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
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
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
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                enabled = false,
            ),
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
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

    @Test
    fun `task with slot type only checks matching availability windows`() {
        val availability = listOf(
            AvailabilitySlot(
                slotType = AvailabilitySlotType.BEFORE_WORK,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(7, 0),
                endTime = LocalTime.of(8, 0),
                enabled = true,
            ),
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                enabled = true,
            ),
        )
        // Task wants BEFORE_WORK but needs 120 min; only 60 min available before work
        val result = validator.validate(
            task(duration = 120, splittable = false, availabilitySlot = AvailabilitySlotType.BEFORE_WORK),
            availability,
        )
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `task without slot type checks all availability windows`() {
        val availability = listOf(
            AvailabilitySlot(
                slotType = AvailabilitySlotType.BEFORE_WORK,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(7, 0),
                endTime = LocalTime.of(8, 0),
                enabled = true,
            ),
            AvailabilitySlot(
                slotType = AvailabilitySlotType.DURING_WORK,
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                enabled = true,
            ),
        )
        // Task has no slot preference, 120 min fits in DURING_WORK window
        val result = validator.validate(
            task(duration = 120, splittable = false, availabilitySlot = null),
            availability,
        )
        assertThat(result).isEqualTo(ValidationResult.Valid)
    }
}
