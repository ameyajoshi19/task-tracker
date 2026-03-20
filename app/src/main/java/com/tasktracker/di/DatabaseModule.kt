package com.tasktracker.di

import android.content.Context
import androidx.room.Room
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TaskTrackerDatabase =
        Room.databaseBuilder(
            context,
            TaskTrackerDatabase::class.java,
            "task_tracker.db",
        )
        .addMigrations(TaskTrackerDatabase.MIGRATION_1_2, TaskTrackerDatabase.MIGRATION_2_3, TaskTrackerDatabase.MIGRATION_3_4)
        .build()

    @Provides
    fun provideTaskDao(db: TaskTrackerDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideScheduledBlockDao(db: TaskTrackerDatabase): ScheduledBlockDao = db.scheduledBlockDao()

    @Provides
    fun provideUserAvailabilityDao(db: TaskTrackerDatabase): UserAvailabilityDao = db.userAvailabilityDao()

    @Provides
    fun provideCalendarSelectionDao(db: TaskTrackerDatabase): CalendarSelectionDao = db.calendarSelectionDao()

    @Provides
    fun providePendingSyncOperationDao(db: TaskTrackerDatabase): PendingSyncOperationDao =
        db.pendingSyncOperationDao()

    @Provides
    fun provideRecurringTaskDao(db: TaskTrackerDatabase): RecurringTaskDao = db.recurringTaskDao()

    @Provides
    fun provideRecurringTaskExceptionDao(db: TaskTrackerDatabase): RecurringTaskExceptionDao =
        db.recurringTaskExceptionDao()
}
