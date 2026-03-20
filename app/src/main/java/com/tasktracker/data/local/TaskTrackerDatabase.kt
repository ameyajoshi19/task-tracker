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
        RecurringTaskEntity::class,
        RecurringTaskExceptionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao
    abstract fun userAvailabilityDao(): UserAvailabilityDao
    abstract fun calendarSelectionDao(): CalendarSelectionDao
    abstract fun pendingSyncOperationDao(): PendingSyncOperationDao
    abstract fun recurringTaskDao(): RecurringTaskDao
    abstract fun recurringTaskExceptionDao(): RecurringTaskExceptionDao

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

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create recurring_tasks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recurring_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        estimatedDurationMinutes INTEGER NOT NULL,
                        quadrant TEXT NOT NULL,
                        dayPreference TEXT NOT NULL DEFAULT 'ANY',
                        splittable INTEGER NOT NULL DEFAULT 0,
                        intervalDays INTEGER NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        fixedTime TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create recurring_task_exceptions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS recurring_task_exceptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recurringTaskId INTEGER NOT NULL,
                        exceptionDate INTEGER NOT NULL,
                        FOREIGN KEY (recurringTaskId) REFERENCES recurring_tasks(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_task_exceptions_recurringTaskId ON recurring_task_exceptions (recurringTaskId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_task_exceptions_recurringTaskId_exceptionDate ON recurring_task_exceptions (recurringTaskId, exceptionDate)")

                // Add columns to tasks table
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurringTaskId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN instanceDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN fixedTime TEXT DEFAULT NULL")

                // Note: Cannot add foreign key to existing table in SQLite without recreating.
                // The foreign key is enforced at the Room/app level. The index still helps queries.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_recurringTaskId ON tasks (recurringTaskId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_recurringTaskId_instanceDate ON tasks (recurringTaskId, instanceDate)")
            }
        }
    }
}
