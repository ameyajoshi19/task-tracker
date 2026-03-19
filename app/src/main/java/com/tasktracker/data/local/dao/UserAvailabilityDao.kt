package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.UserAvailabilityEntity
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(availabilities: List<UserAvailabilityEntity>)

    @Query("SELECT * FROM user_availability WHERE dayOfWeek = :dayOfWeek AND enabled = 1 ORDER BY startTime")
    suspend fun getByDayOfWeek(dayOfWeek: DayOfWeek): List<UserAvailabilityEntity>

    @Query("DELETE FROM user_availability WHERE dayOfWeek != :dayOfWeek")
    suspend fun deleteAllExceptDay(dayOfWeek: DayOfWeek)

    @Transaction
    suspend fun replaceAllExcept(sourceDayOfWeek: DayOfWeek, copies: List<UserAvailabilityEntity>) {
        deleteAllExceptDay(sourceDayOfWeek)
        insertAll(copies)
    }
}
