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
        PendingSyncOperationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao
    abstract fun userAvailabilityDao(): UserAvailabilityDao
    abstract fun calendarSelectionDao(): CalendarSelectionDao
    abstract fun pendingSyncOperationDao(): PendingSyncOperationDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_sync_operations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        blockId INTEGER NOT NULL,
                        taskId INTEGER NOT NULL,
                        calendarId TEXT NOT NULL,
                        eventId TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Remove duplicate calendar selections, keeping the one with the lowest id
                db.execSQL("""
                    DELETE FROM calendar_selections WHERE id NOT IN (
                        SELECT MIN(id) FROM calendar_selections GROUP BY googleCalendarId
                    )
                """.trimIndent())
                // Add unique index on googleCalendarId
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_calendar_selections_googleCalendarId
                    ON calendar_selections (googleCalendarId)
                """.trimIndent())
            }
        }
    }
}
