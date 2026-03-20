package com.tasktracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.domain.model.BlockStatus
import com.tasktracker.domain.repository.ScheduledBlockRepository
import com.tasktracker.ui.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val blockRepository: ScheduledBlockRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = appPreferences.dailySummaryEnabled.first()
        if (!enabled) return Result.success()

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()

        val blocks = blockRepository.getByStatuses(
            listOf(BlockStatus.CONFIRMED)
        ).filter { it.startTime >= dayStart && it.startTime < dayEnd }

        if (blocks.isEmpty()) return Result.success()

        val taskCount = blocks.map { it.taskId }.distinct().size
        notificationHelper.showDailySummary(taskCount)

        return Result.success()
    }
}
