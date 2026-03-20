package com.tasktracker.data.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val WORK_NAME = "daily_summary"
    }

    fun schedule(time: LocalTime) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var target = now.toLocalDate().atTime(time).atZone(ZoneId.systemDefault())
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        val initialDelay = Duration.between(now, target)

        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            24, TimeUnit.HOURS,
        )
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
