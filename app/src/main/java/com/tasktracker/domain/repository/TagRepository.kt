package com.tasktracker.domain.repository

import com.tasktracker.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAll(): Flow<List<Tag>>
    suspend fun getAll(): List<Tag>
    suspend fun insert(tag: Tag): Long
    suspend fun delete(tag: Tag)
}
