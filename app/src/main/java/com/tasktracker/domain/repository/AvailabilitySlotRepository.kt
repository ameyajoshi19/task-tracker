package com.tasktracker.domain.repository

import com.tasktracker.domain.model.AvailabilitySlot
import com.tasktracker.domain.model.AvailabilitySlotType
import kotlinx.coroutines.flow.Flow

interface AvailabilitySlotRepository {
    fun observeAll(): Flow<List<AvailabilitySlot>>
    suspend fun getEnabled(): List<AvailabilitySlot>
    suspend fun getBySlotType(type: AvailabilitySlotType): List<AvailabilitySlot>
    suspend fun update(slot: AvailabilitySlot)
}
