package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.UserAvailabilityDao
import com.tasktracker.data.local.entity.UserAvailabilityEntity
import com.tasktracker.domain.model.UserAvailability
import com.tasktracker.domain.repository.UserAvailabilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserAvailabilityRepositoryImpl @Inject constructor(
    private val dao: UserAvailabilityDao,
) : UserAvailabilityRepository {

    override suspend fun insert(availability: UserAvailability): Long =
        dao.insert(UserAvailabilityEntity.fromDomain(availability))

    override suspend fun update(availability: UserAvailability) =
        dao.update(UserAvailabilityEntity.fromDomain(availability))

    override suspend fun delete(availability: UserAvailability) =
        dao.delete(UserAvailabilityEntity.fromDomain(availability))

    override fun observeAll(): Flow<List<UserAvailability>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getEnabled(): List<UserAvailability> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun getAll(): List<UserAvailability> =
        dao.getAll().map { it.toDomain() }
}
