package com.tasktracker.data.sync

import com.tasktracker.domain.model.CalendarEvent
import com.tasktracker.domain.model.ScheduledBlock
import java.time.Instant
import javax.inject.Inject

sealed class ExternalChange {
    data class Deleted(val block: ScheduledBlock) : ExternalChange()
    data class Moved(
        val block: ScheduledBlock,
        val newStart: Instant,
        val newEnd: Instant,
    ) : ExternalChange()
}

class ExternalChangeDetector @Inject constructor() {

    fun detectChanges(
        localBlocks: List<ScheduledBlock>,
        calendarEvents: List<CalendarEvent>,
    ): List<ExternalChange> {
        val eventById = calendarEvents.associateBy { it.id }
        val changes = mutableListOf<ExternalChange>()

        for (block in localBlocks) {
            val eventId = block.googleCalendarEventId ?: continue
            val event = eventById[eventId]

            if (event == null) {
                changes.add(ExternalChange.Deleted(block))
            } else if (event.startTime != block.startTime || event.endTime != block.endTime) {
                changes.add(
                    ExternalChange.Moved(
                        block = block,
                        newStart = event.startTime,
                        newEnd = event.endTime,
                    )
                )
            }
        }

        return changes
    }
}
