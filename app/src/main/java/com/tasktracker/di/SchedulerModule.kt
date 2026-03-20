package com.tasktracker.di

import com.tasktracker.domain.scheduler.RecurrenceExpander
import com.tasktracker.domain.scheduler.SlotFinder
import com.tasktracker.domain.scheduler.TaskPriorityComparator
import com.tasktracker.domain.scheduler.TaskScheduler
import com.tasktracker.domain.validation.RecurringTaskValidator
import com.tasktracker.domain.validation.TaskValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SchedulerModule {

    @Provides
    @Singleton
    fun provideTaskPriorityComparator(): TaskPriorityComparator = TaskPriorityComparator()

    @Provides
    @Singleton
    fun provideSlotFinder(): SlotFinder = SlotFinder()

    @Provides
    @Singleton
    fun provideTaskScheduler(
        comparator: TaskPriorityComparator,
        slotFinder: SlotFinder,
    ): TaskScheduler = TaskScheduler(comparator, slotFinder)

    @Provides
    @Singleton
    fun provideTaskValidator(): TaskValidator = TaskValidator()

    @Provides
    @Singleton
    fun provideRecurrenceExpander(): RecurrenceExpander = RecurrenceExpander()

    @Provides
    @Singleton
    fun provideRecurringTaskValidator(): RecurringTaskValidator = RecurringTaskValidator()
}
