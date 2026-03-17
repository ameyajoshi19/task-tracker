package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.CalendarSelectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSelectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selection: CalendarSelectionEntity): Long

    @Update
    suspend fun update(selection: CalendarSelectionEntity)

    @Delete
    suspend fun delete(selection: CalendarSelectionEntity)

    @Query("SELECT * FROM calendar_selections ORDER BY calendarName")
    fun observeAll(): Flow<List<CalendarSelectionEntity>>

    @Query("SELECT * FROM calendar_selections WHERE enabled = 1")
    suspend fun getEnabled(): List<CalendarSelectionEntity>
}
