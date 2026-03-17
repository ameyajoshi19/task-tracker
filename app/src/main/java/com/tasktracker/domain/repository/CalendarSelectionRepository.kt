package com.tasktracker.domain.repository

import com.tasktracker.domain.model.CalendarSelection
import kotlinx.coroutines.flow.Flow

interface CalendarSelectionRepository {
    suspend fun insert(selection: CalendarSelection): Long
    suspend fun update(selection: CalendarSelection)
    suspend fun delete(selection: CalendarSelection)
    fun observeAll(): Flow<List<CalendarSelection>>
    suspend fun getEnabled(): List<CalendarSelection>
}
