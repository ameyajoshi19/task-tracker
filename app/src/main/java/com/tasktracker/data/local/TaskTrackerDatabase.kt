package com.tasktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tasktracker.data.local.converter.Converters
import com.tasktracker.data.local.dao.*
import com.tasktracker.data.local.entity.*

@Database(
    entities = [
        TaskEntity::class,
        ScheduledBlockEntity::class,
        UserAvailabilityEntity::class,
        CalendarSelectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao
    abstract fun userAvailabilityDao(): UserAvailabilityDao
    abstract fun calendarSelectionDao(): CalendarSelectionDao
}
