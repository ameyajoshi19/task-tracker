package com.tasktracker.data.repository

import com.tasktracker.data.local.dao.TagDao
import com.tasktracker.data.local.entity.TagEntity
import com.tasktracker.domain.model.Tag
import com.tasktracker.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAll(): List<Tag> =
        dao.getAll().map { it.toDomain() }

    override suspend fun insert(tag: Tag): Long =
        dao.insert(TagEntity.fromDomain(tag))

    override suspend fun delete(tag: Tag) =
        dao.delete(TagEntity.fromDomain(tag))
}
