package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.AvailabilitySlotDao
import com.tasktracker.data.local.entity.AvailabilitySlotEntity
import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.domain.repository.AvailabilitySlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AvailabilitySlotRepositoryImpl @Inject constructor(
    private val dao: AvailabilitySlotDao,
) : AvailabilitySlotRepository {

    override fun observeAll(): Flow<List<AvailabilitySlot>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabled(): List<AvailabilitySlot> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun getBySlotType(type: AvailabilitySlotType): List<AvailabilitySlot> =
        dao.getBySlotType(type).map { it.toDomain() }

    override suspend fun update(slot: AvailabilitySlot) =
        dao.update(AvailabilitySlotEntity.fromDomain(slot))
}
