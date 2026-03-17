package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.UserAvailabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAvailabilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(availability: UserAvailabilityEntity): Long

    @Update
    suspend fun update(availability: UserAvailabilityEntity)

    @Delete
    suspend fun delete(availability: UserAvailabilityEntity)

    @Query("SELECT * FROM user_availability ORDER BY dayOfWeek, startTime")
    fun observeAll(): Flow<List<UserAvailabilityEntity>>

    @Query("SELECT * FROM user_availability WHERE enabled = 1 ORDER BY dayOfWeek, startTime")
    suspend fun getEnabled(): List<UserAvailabilityEntity>

    @Query("SELECT * FROM user_availability ORDER BY dayOfWeek, startTime")
    suspend fun getAll(): List<UserAvailabilityEntity>
}
