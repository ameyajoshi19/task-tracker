package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.CalendarSelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSelectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(selection: CalendarSelectionEntity): Long

    @Query("""
        UPDATE calendar_selections
        SET calendarName = :calendarName, calendarColor = :calendarColor, enabled = :enabled
        WHERE googleCalendarId = :googleCalendarId
    """)
    suspend fun updateByGoogleId(googleCalendarId: String, calendarName: String, calendarColor: String, enabled: Boolean)

    @Transaction
    suspend fun upsert(selection: CalendarSelectionEntity): Long {
        val id = insert(selection)
        if (id == -1L) {
            updateByGoogleId(selection.googleCalendarId, selection.calendarName, selection.calendarColor, selection.enabled)
        }
        return id
    }

    @Update
    suspend fun update(selection: CalendarSelectionEntity)

    @Delete
    suspend fun delete(selection: CalendarSelectionEntity)

    @Query("SELECT * FROM calendar_selections ORDER BY calendarName")
    fun observeAll(): Flow<List<CalendarSelectionEntity>>

    @Query("SELECT * FROM calendar_selections WHERE enabled = 1")
    suspend fun getEnabled(): List<CalendarSelectionEntity>
}
