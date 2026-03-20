package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.RecurringTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTaskEntity): Long

    @Update
    suspend fun update(entity: RecurringTaskEntity)

    @Delete
    suspend fun delete(entity: RecurringTaskEntity)

    @Query("SELECT * FROM recurring_tasks WHERE id = :id")
    suspend fun getById(id: Long): RecurringTaskEntity?

    @Query("SELECT * FROM recurring_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecurringTaskEntity>>

    @Query("SELECT * FROM recurring_tasks")
    suspend fun getAll(): List<RecurringTaskEntity>
}
