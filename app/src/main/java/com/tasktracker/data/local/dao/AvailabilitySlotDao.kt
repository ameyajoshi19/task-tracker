package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.AvailabilitySlotEntity
import com.tasktracker.domain.model.AvailabilitySlotType
import kotlinx.coroutines.flow.Flow

@Dao
interface AvailabilitySlotDao {
    @Query("SELECT * FROM availability_slots ORDER BY slotType, dayOfWeek")
    fun observeAll(): Flow<List<AvailabilitySlotEntity>>

    @Query("SELECT * FROM availability_slots WHERE slotType = :slotType ORDER BY dayOfWeek")
    fun observeBySlotType(slotType: AvailabilitySlotType): Flow<List<AvailabilitySlotEntity>>

    @Query("SELECT * FROM availability_slots WHERE slotType = :slotType ORDER BY dayOfWeek")
    suspend fun getBySlotType(slotType: AvailabilitySlotType): List<AvailabilitySlotEntity>

    @Query("SELECT * FROM availability_slots WHERE enabled = 1 ORDER BY slotType, dayOfWeek")
    suspend fun getEnabled(): List<AvailabilitySlotEntity>

    @Update
    suspend fun update(slot: AvailabilitySlotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<AvailabilitySlotEntity>)
}
