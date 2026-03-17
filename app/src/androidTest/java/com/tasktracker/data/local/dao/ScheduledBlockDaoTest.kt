package com.tasktracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.entity.ScheduledBlockEntity
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ScheduledBlockDaoTest {

    private lateinit var db: TaskTrackerDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var blockDao: ScheduledBlockDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
        blockDao = db.scheduledBlockDao()
    }

    @After
    fun teardown() { db.close() }

    private val now = Instant.now()

    @Test
    fun insertAndRetrieveByTaskId() = runTest {
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Test",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                createdAt = now,
                updatedAt = now,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
            )
        )
        val blocks = blockDao.getByTaskId(taskId)
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].taskId).isEqualTo(taskId)
    }

    @Test
    fun cascadeDeleteRemovesBlocks() = runTest {
        val task = TaskEntity(
            title = "Test",
            estimatedDurationMinutes = 60,
            quadrant = Quadrant.IMPORTANT,
            createdAt = now,
            updatedAt = now,
        )
        val taskId = taskDao.insert(task)
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
            )
        )
        taskDao.delete(task.copy(id = taskId))
        val blocks = blockDao.getByTaskId(taskId)
        assertThat(blocks).isEmpty()
    }

    @Test
    fun deleteByStatusRemovesCorrectBlocks() = runTest {
        val taskId = taskDao.insert(
            TaskEntity(
                title = "Test",
                estimatedDurationMinutes = 60,
                quadrant = Quadrant.IMPORTANT,
                createdAt = now,
                updatedAt = now,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now,
                endTime = now.plus(1, ChronoUnit.HOURS),
                status = BlockStatus.PROPOSED,
            )
        )
        blockDao.insert(
            ScheduledBlockEntity(
                taskId = taskId,
                startTime = now.plus(2, ChronoUnit.HOURS),
                endTime = now.plus(3, ChronoUnit.HOURS),
                status = BlockStatus.CONFIRMED,
            )
        )
        blockDao.deleteByStatus(BlockStatus.PROPOSED)
        val remaining = blockDao.getByTaskId(taskId)
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].status).isEqualTo(BlockStatus.CONFIRMED)
    }
}
