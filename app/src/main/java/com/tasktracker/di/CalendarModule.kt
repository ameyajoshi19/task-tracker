package com.tasktracker.di

import com.tasktracker.data.calendar.GoogleCalendarApiClient
import com.tasktracker.domain.repository.CalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: GoogleCalendarApiClient,
    ): CalendarRepository
}
