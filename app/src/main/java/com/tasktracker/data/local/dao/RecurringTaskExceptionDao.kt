package com.tasktracker.data.local.dao

import androidx.room.*
import com.tasktracker.data.local.entity.RecurringTaskExceptionEntity

@Dao
interface RecurringTaskExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecurringTaskExceptionEntity): Long

    @Query("SELECT * FROM recurring_task_exceptions WHERE recurringTaskId = :recurringTaskId")
    suspend fun getByRecurringTaskId(recurringTaskId: Long): List<RecurringTaskExceptionEntity>

    @Query("DELETE FROM recurring_task_exceptions WHERE recurringTaskId = :recurringTaskId")
    suspend fun deleteByRecurringTaskId(recurringTaskId: Long)
}
