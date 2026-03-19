package com.tasktracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tasktracker.domain.model.SyncInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val TASK_CALENDAR_ID = stringPreferencesKey("task_calendar_id")
        private val SYNC_INTERVAL = stringPreferencesKey("sync_interval")
        private val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private const val STALE_THRESHOLD_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    }

    val taskCalendarId: Flow<String?> = context.dataStore.data
        .map { it[TASK_CALENDAR_ID] }

    suspend fun setTaskCalendarId(id: String) {
        context.dataStore.edit { it[TASK_CALENDAR_ID] = id }
    }

    val syncInterval: Flow<SyncInterval> = context.dataStore.data
        .map { prefs ->
            val name = prefs[SYNC_INTERVAL] ?: SyncInterval.FIFTEEN_MINUTES.name
            SyncInterval.valueOf(name)
        }

    suspend fun setSyncInterval(interval: SyncInterval) {
        context.dataStore.edit { it[SYNC_INTERVAL] = interval.name }
    }

    val lastSyncTimestamp: Flow<Instant?> = context.dataStore.data
        .map { prefs ->
            prefs[LAST_SYNC_TIMESTAMP]?.let { Instant.ofEpochMilli(it) }
        }

    suspend fun setLastSyncTimestamp(timestamp: Instant) {
        context.dataStore.edit { it[LAST_SYNC_TIMESTAMP] = timestamp.toEpochMilli() }
    }

    val isFreeBusyDataStale: Flow<Boolean> = context.dataStore.data
        .map { prefs ->
            val lastSync = prefs[LAST_SYNC_TIMESTAMP] ?: return@map true
            (System.currentTimeMillis() - lastSync) > STALE_THRESHOLD_MILLIS
        }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE] ?: "auto" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }
}
