package com.tasktracker.domain.repository

import com.tasktracker.domain.model.UserAvailability
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

interface UserAvailabilityRepository {
    suspend fun insert(availability: UserAvailability): Long
    suspend fun update(availability: UserAvailability)
    suspend fun delete(availability: UserAvailability)
    fun observeAll(): Flow<List<UserAvailability>>
    suspend fun getEnabled(): List<UserAvailability>
    suspend fun getAll(): List<UserAvailability>
    suspend fun copyToAllDays(sourceDayOfWeek: DayOfWeek)
}
