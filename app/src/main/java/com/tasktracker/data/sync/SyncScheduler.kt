package com.tasktracker.data.sync

import android.content.Context
import androidx.work.*
import com.tasktracker.domain.model.SyncInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val SYNC_WORK_NAME = "calendar_sync"
        private const val SYNC_NOW_WORK_NAME = "calendar_sync_now"
    }

    fun schedule(interval: SyncInterval) {
        val workManager = WorkManager.getInstance(context)

        if (interval == SyncInterval.MANUAL) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            interval.minutes, TimeUnit.MINUTES,
            // Flex interval: allow WorkManager to schedule within the last third of the period
            interval.minutes / 3, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun observeSyncNowStatus(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SYNC_NOW_WORK_NAME)

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
}
