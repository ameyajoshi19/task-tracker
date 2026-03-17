package com.tasktracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "task_tracker_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val TASK_CALENDAR_ID = stringPreferencesKey("task_calendar_id")
    }

    val taskCalendarId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TASK_CALENDAR_ID]
    }

    suspend fun setTaskCalendarId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[TASK_CALENDAR_ID] = id
        }
    }
}
