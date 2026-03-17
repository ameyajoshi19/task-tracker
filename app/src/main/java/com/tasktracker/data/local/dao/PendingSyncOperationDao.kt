package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.PendingSyncOperationEntity

@Dao
interface PendingSyncOperationDao {
    @Insert
    suspend fun insert(operation: PendingSyncOperationEntity): Long

    @Delete
    suspend fun delete(operation: PendingSyncOperationEntity)

    @Query("SELECT * FROM pending_sync_operations ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSyncOperationEntity>

    @Query("DELETE FROM pending_sync_operations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_sync_operations")
    suspend fun count(): Int
}
