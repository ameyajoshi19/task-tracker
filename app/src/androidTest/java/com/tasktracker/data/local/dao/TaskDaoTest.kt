package com.tasktracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tasktracker.data.local.TaskTrackerDatabase
import com.tasktracker.data.local.entity.TaskEntity
import com.tasktracker.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: TaskTrackerDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(
        title: String = "Test",
        duration: Int = 60,
        quadrant: Quadrant = Quadrant.IMPORTANT,
    ) = TaskEntity(
        title = title,
        estimatedDurationMinutes = duration,
        quadrant = quadrant,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun insertAndRetrieve() = runTest {
        val id = dao.insert(entity(title = "My Task"))
        val result = dao.getById(id)
        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("My Task")
    }

    @Test
    fun observeAllEmitsUpdates() = runTest {
        dao.insert(entity(title = "A"))
        dao.insert(entity(title = "B"))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun getByStatusFiltersCorrectly() = runTest {
        dao.insert(entity().copy(status = TaskStatus.PENDING))
        dao.insert(entity().copy(status = TaskStatus.COMPLETED))
        val pending = dao.getByStatus(TaskStatus.PENDING)
        assertThat(pending).hasSize(1)
    }

    @Test
    fun updateStatusChangesStatus() = runTest {
        val id = dao.insert(entity())
        dao.updateStatus(id, TaskStatus.SCHEDULED, Instant.now().toEpochMilli())
        val result = dao.getById(id)
        assertThat(result!!.status).isEqualTo(TaskStatus.SCHEDULED)
    }

    @Test
    fun deleteRemovesTask() = runTest {
        val e = entity()
        val id = dao.insert(e)
        dao.delete(e.copy(id = id))
        assertThat(dao.getById(id)).isNull()
    }
}
