package com.tasktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tasktracker.data.local.converter.Converters
import com.tasktracker.data.local.dao.*
import com.tasktracker.data.local.entity.*

/**
 * Room database for the app. Current schema version: **4**.
 *
 * Migration history:
 * - 1→2: Added `pending_sync_operations` table for offline queue support.
 * - 2→3: Deduplicated `calendar_selections` rows and added a unique index on `googleCalendarId`.
 * - 3→4: Added `recurring_tasks` and `recurring_task_exceptions` tables; extended `tasks` with
 *   `recurringTaskId`, `instanceDate`, and `fixedTime` columns to link instances to their
 *   template. SQLite's lack of ALTER TABLE … ADD FOREIGN KEY means the FK is enforced at the
 *   app level only; an index is added to keep instance-by-template queries fast.
 * - 4→5: Replaced `user_availability` with `availability_slots` (3 slot types × 7 days).
 *   Added `tags` table. Extended `tasks` with `availability_slot` and `tag_id` columns.
 */
@Database(
    entities = [
        TaskEntity::class,
        ScheduledBlockEntity::class,
        AvailabilitySlotEntity::class,
        TagEntity::class,
        CalendarSelectionEntity::class,
        PendingSyncOperationEntity::class,
        RecurringTaskEntity::class,
        RecurringTaskExceptionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TaskTrackerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduledBlockDao(): ScheduledBlockDao
    abstract fun availabilitySlotDao(): AvailabilitySlotDao
    abstract fun tagDao(): TagDao
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

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Drop the old user_availability table, replaced by availability_slots
                db.execSQL("DROP TABLE IF EXISTS user_availability")

                // Create availability_slots table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS availability_slots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        slotType TEXT NOT NULL,
                        dayOfWeek INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_availability_slots_slotType_dayOfWeek ON availability_slots (slotType, dayOfWeek)")

                // Pre-populate 21 rows (3 types x 7 days), all disabled
                val slotDefaults = mapOf(
                    "BEFORE_WORK" to ("06:00" to "09:00"),
                    "DURING_WORK" to ("09:00" to "17:00"),
                    "AFTER_WORK" to ("17:00" to "21:00"),
                )
                for ((slotType, times) in slotDefaults) {
                    for (day in 1..7) { // DayOfWeek 1=Monday .. 7=Sunday
                        db.execSQL(
                            "INSERT INTO availability_slots (slotType, dayOfWeek, startTime, endTime, enabled) VALUES (?, ?, ?, ?, 0)",
                            arrayOf(slotType, day, times.first, times.second),
                        )
                    }
                }

                // Create tags table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags (name)")

                // Add new columns to tasks table
                db.execSQL("ALTER TABLE tasks ADD COLUMN availabilitySlot TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tasks ADD COLUMN tagId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add tagId column to recurring_tasks to propagate tags to instances
                db.execSQL("ALTER TABLE recurring_tasks ADD COLUMN tagId INTEGER DEFAULT NULL")
            }
        }
    }
}
