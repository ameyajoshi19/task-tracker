package com.tasktracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tasktracker.data.calendar.CalendarSyncManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import com.tasktracker.domain.scheduler.TaskScheduler
import com.tasktracker.ui.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarRepository: CalendarRepository,
    private val calendarSelectionRepository: CalendarSelectionRepository,
    private val blockRepository: ScheduledBlockRepository,
    private val taskRepository: TaskRepository,
    private val availabilityRepository: UserAvailabilityRepository,
    private val syncManager: CalendarSyncManager,
    private val externalChangeDetector: ExternalChangeDetector,
    private val taskScheduler: TaskScheduler,
    private val notificationHelper: NotificationHelper,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Process any pending offline operations
            syncManager.processPendingOperations()

            // 2. Get task calendar ID
            val taskCalendarId = appPreferences.taskCalendarId.first()
                ?: return Result.success() // Not set up yet

            // 3. Verify Task Tracker calendar still exists; recreate if deleted
            val resolvedCalendarId = try {
                calendarRepository.getOrCreateTaskCalendar()
            } catch (e: Exception) {
                return Result.retry()
            }
            if (resolvedCalendarId != taskCalendarId) {
                // Calendar was recreated — update stored ID and re-push all blocks
                appPreferences.setTaskCalendarId(resolvedCalendarId)
                val allConfirmed = blockRepository.getByStatuses(listOf(BlockStatus.CONFIRMED))
                for (block in allConfirmed) {
                    syncManager.pushNewBlock(block)
                }
            }

            // 4. Detect external changes to Task Tracker calendar events
            val confirmedBlocks = blockRepository.getByStatuses(
                listOf(BlockStatus.CONFIRMED)
            )
            val now = Instant.now()
            val twoWeeksLater = now.plusSeconds(14 * 24 * 3600)
            val taskCalendarEvents = calendarRepository.getEvents(
                resolvedCalendarId, now, twoWeeksLater
            )
            val externalChanges = externalChangeDetector.detectChanges(
                confirmedBlocks, taskCalendarEvents
            )

            var needsReschedule = false

            for (change in externalChanges) {
                when (change) {
                    is ExternalChange.Deleted -> {
                        blockRepository.updateStatus(change.block.id, BlockStatus.CANCELLED)
                        taskRepository.updateStatus(change.block.taskId, TaskStatus.PENDING)
                        needsReschedule = true
                    }
                    is ExternalChange.Moved -> {
                        blockRepository.update(
                            change.block.copy(
                                startTime = change.newStart,
                                endTime = change.newEnd,
                            )
                        )
                    }
                }
            }

            // 4. Fetch fresh free/busy data (excluding the task calendar to avoid
            // false conflicts where confirmed blocks "conflict" with their own events)
            val enabledCalendars = calendarSelectionRepository.getEnabled()
            val externalCalendarIds = enabledCalendars
                .map { it.googleCalendarId }
                .filter { it != resolvedCalendarId }
            val busySlots = if (externalCalendarIds.isNotEmpty()) {
                calendarRepository.getFreeBusySlots(
                    calendarIds = externalCalendarIds,
                    timeMin = now,
                    timeMax = twoWeeksLater,
                )
            } else {
                emptyList()
            }

            // 5. Check for conflicts between confirmed blocks and external busy slots
            val currentBlocks = blockRepository.getByStatuses(listOf(BlockStatus.CONFIRMED))
            for (block in currentBlocks) {
                val hasConflict = busySlots.any { busy ->
                    block.startTime < busy.endTime && block.endTime > busy.startTime
                }
                if (hasConflict) {
                    needsReschedule = true
                    break
                }
            }

            // 6. Re-run scheduler if needed
            if (needsReschedule) {
                val pendingTasks = taskRepository.getByStatuses(
                    listOf(TaskStatus.PENDING, TaskStatus.SCHEDULED)
                )
                val availability = availabilityRepository.getEnabled()
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val existingBlocks = blockRepository.getByStatuses(
                    listOf(BlockStatus.CONFIRMED, BlockStatus.COMPLETED)
                )

                val result = taskScheduler.schedule(
                    tasks = pendingTasks,
                    existingBlocks = existingBlocks,
                    availability = availability,
                    busySlots = busySlots,
                    startDate = today,
                    endDate = today.plusDays(14),
                    zoneId = zoneId,
                )

                when (result) {
                    is SchedulingResult.Scheduled -> {
                        // Delete old blocks and calendar events for tasks being rescheduled
                        val rescheduledTaskIds = result.blocks.map { it.taskId }.toSet()
                        for (taskId in rescheduledTaskIds) {
                            syncManager.deleteTaskEvents(taskId)
                            blockRepository.deleteByTaskId(taskId)
                        }
                        for (block in result.blocks) {
                            val id = blockRepository.insert(block)
                            taskRepository.updateStatus(block.taskId, TaskStatus.SCHEDULED)
                            syncManager.pushNewBlock(block.copy(id = id))
                        }
                    }
                    is SchedulingResult.NeedsReschedule -> {
                        blockRepository.insertAll(
                            (result.newBlocks + result.movedBlocks.map { it.second })
                                .map { it.copy(status = BlockStatus.PROPOSED) }
                        )
                        notificationHelper.showRescheduleProposal(
                            result.newBlocks.size + result.movedBlocks.size
                        )
                    }
                    is SchedulingResult.DeadlineAtRisk -> {
                        notificationHelper.showDeadlineAtRisk(result.task.title, result.task.id)
                    }
                    is SchedulingResult.NoSlotsAvailable -> {
                        // Silent — user will see status in app
                    }
                }
            }

            // 7. Update last sync timestamp
            appPreferences.setLastSyncTimestamp(Instant.now())

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
