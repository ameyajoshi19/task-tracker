package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.CalendarSelectionDao
import com.tasktracker.data.local.entity.CalendarSelectionEntity
import com.tasktracker.domain.model.CalendarSelection
import com.tasktracker.domain.repository.CalendarSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CalendarSelectionRepositoryImpl @Inject constructor(
    private val dao: CalendarSelectionDao,
) : CalendarSelectionRepository {

    override suspend fun insert(selection: CalendarSelection): Long =
        dao.upsert(CalendarSelectionEntity.fromDomain(selection))

    override suspend fun update(selection: CalendarSelection) =
        dao.update(CalendarSelectionEntity.fromDomain(selection))

    override suspend fun delete(selection: CalendarSelection) =
        dao.delete(CalendarSelectionEntity.fromDomain(selection))

    override fun observeAll(): Flow<List<CalendarSelection>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabled(): List<CalendarSelection> =
        dao.getEnabled().map { it.toDomain() }
}
