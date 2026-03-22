package com.tasktracker.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies MIGRATION_4_5: creates availability_slots (21 pre-populated rows) and tags tables,
 * adds availabilitySlot and tagId columns to tasks, and drops user_availability.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setup() {
        // Create a v4-like database in memory with the tables MIGRATION_4_5 depends on
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                ApplicationProvider.getApplicationContext()
            )
                .name(null) // in-memory
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Minimal v4 schema: tasks table and user_availability
                        db.execSQL("""
                            CREATE TABLE tasks (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                description TEXT NOT NULL DEFAULT '',
                                estimatedDurationMinutes INTEGER NOT NULL,
                                quadrant TEXT NOT NULL,
                                deadline INTEGER,
                                dayPreference TEXT NOT NULL DEFAULT 'ANY',
                                splittable INTEGER NOT NULL DEFAULT 0,
                                status TEXT NOT NULL DEFAULT 'PENDING',
                                recurringTaskId INTEGER,
                                instanceDate INTEGER,
                                fixedTime TEXT,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                        """.trimIndent())

                        db.execSQL("""
                            CREATE TABLE user_availability (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                dayOfWeek INTEGER NOT NULL,
                                startTime TEXT NOT NULL,
                                endTime TEXT NOT NULL,
                                enabled INTEGER NOT NULL DEFAULT 1
                            )
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun migration_creates_availability_slots_with_21_rows() {
        // Insert a task before migration to verify data survives
        db.execSQL(
            "INSERT INTO tasks (id, title, estimatedDurationMinutes, quadrant, createdAt, updatedAt) VALUES (1, 'Test Task', 60, 'IMPORTANT', 1000, 1000)"
        )

        // Insert data into user_availability to verify it gets dropped
        db.execSQL(
            "INSERT INTO user_availability (dayOfWeek, startTime, endTime, enabled) VALUES (1, '09:00', '17:00', 1)"
        )

        // Run the migration
        TaskTrackerDatabase.MIGRATION_4_5.migrate(db)

        // Verify availability_slots table exists with 21 rows (3 types x 7 days)
        db.query("SELECT COUNT(*) FROM availability_slots").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(21)
        }

        // Verify all 3 slot types are present
        db.query("SELECT DISTINCT slotType FROM availability_slots ORDER BY slotType").use { cursor ->
            val types = mutableListOf<String>()
            while (cursor.moveToNext()) {
                types.add(cursor.getString(0))
            }
            assertThat(types).containsExactly("AFTER_WORK", "BEFORE_WORK", "DURING_WORK")
        }

        // Verify each type has 7 day entries
        for (slotType in listOf("BEFORE_WORK", "DURING_WORK", "AFTER_WORK")) {
            db.query("SELECT COUNT(*) FROM availability_slots WHERE slotType = ?", arrayOf(slotType)).use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(7)
            }
        }

        // Verify all slots are disabled by default
        db.query("SELECT COUNT(*) FROM availability_slots WHERE enabled = 1").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migration_creates_tags_table() {
        TaskTrackerDatabase.MIGRATION_4_5.migrate(db)

        // Verify tags table exists by inserting and querying
        db.execSQL("INSERT INTO tags (name, color, createdAt) VALUES ('Work', 4278190335, 1000)")
        db.query("SELECT name, color FROM tags").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("Work")
            assertThat(cursor.getLong(1)).isEqualTo(4278190335)
        }
    }

    @Test
    fun migration_adds_columns_to_tasks_and_preserves_data() {
        // Insert a task before migration
        db.execSQL(
            "INSERT INTO tasks (id, title, estimatedDurationMinutes, quadrant, createdAt, updatedAt) VALUES (1, 'Preserved Task', 90, 'URGENT', 2000, 2000)"
        )

        TaskTrackerDatabase.MIGRATION_4_5.migrate(db)

        // Verify task data preserved and new columns exist with NULL defaults
        db.query("SELECT id, title, estimatedDurationMinutes, quadrant, availabilitySlot, tagId FROM tasks WHERE id = 1").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(1)).isEqualTo("Preserved Task")
            assertThat(cursor.getInt(2)).isEqualTo(90)
            assertThat(cursor.getString(3)).isEqualTo("URGENT")
            assertThat(cursor.isNull(4)).isTrue() // availabilitySlot
            assertThat(cursor.isNull(5)).isTrue() // tagId
        }
    }

    @Test
    fun migration_drops_user_availability_table() {
        db.execSQL(
            "INSERT INTO user_availability (dayOfWeek, startTime, endTime, enabled) VALUES (1, '09:00', '17:00', 1)"
        )

        TaskTrackerDatabase.MIGRATION_4_5.migrate(db)

        // Verify user_availability table no longer exists
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='user_availability'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }
}
