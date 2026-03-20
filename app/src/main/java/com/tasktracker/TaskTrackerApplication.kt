package com.tasktracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.DailySummaryScheduler
import com.tasktracker.ui.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application entry point. Handles one-time process-level setup:
 * - Creates notification channels (must happen before any notification is posted).
 * - Provides a custom [Configuration] that injects Hilt dependencies into WorkManager workers.
 * - Schedules or cancels the daily summary [androidx.work.Worker] based on the persisted user
 *   preference, using [runBlocking] so the preference is read synchronously before the first
 *   Activity launches.
 */
@HiltAndroidApp
class TaskTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dailySummaryScheduler: DailySummaryScheduler

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        runBlocking {
            val enabled = appPreferences.dailySummaryEnabled.first()
            if (enabled) {
                val timeStr = appPreferences.dailySummaryTime.first()
                val time = LocalTime.parse(timeStr)
                dailySummaryScheduler.schedule(time)
            } else {
                dailySummaryScheduler.cancel()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
