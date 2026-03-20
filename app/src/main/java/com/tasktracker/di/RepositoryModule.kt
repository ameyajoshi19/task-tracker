package com.tasktracker.di

import com.tasktracker.data.repository.*
import com.tasktracker.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindScheduledBlockRepository(impl: ScheduledBlockRepositoryImpl): ScheduledBlockRepository

    @Binds
    @Singleton
    abstract fun bindUserAvailabilityRepository(impl: UserAvailabilityRepositoryImpl): UserAvailabilityRepository

    @Binds
    @Singleton
    abstract fun bindCalendarSelectionRepository(impl: CalendarSelectionRepositoryImpl): CalendarSelectionRepository

    @Binds
    @Singleton
    abstract fun bindSyncOperationRepository(
        impl: SyncOperationRepositoryImpl
    ): SyncOperationRepository

    @Binds
    @Singleton
    abstract fun bindRecurringTaskRepository(impl: RecurringTaskRepositoryImpl): RecurringTaskRepository

    @Binds
    @Singleton
    abstract fun bindRecurringTaskExceptionRepository(impl: RecurringTaskExceptionRepositoryImpl): RecurringTaskExceptionRepository
}
