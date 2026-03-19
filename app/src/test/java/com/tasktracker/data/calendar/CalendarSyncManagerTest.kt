package com.tasktracker.data.calendar

import com.google.common.truth.Truth.assertThat
import com.tasktracker.domain.model.*
import com.tasktracker.domain.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CalendarSyncManagerTest {

    private lateinit var syncManager: CalendarSyncManager
    private lateinit var fakeCalendarRepo: FakeCalendarRepository
    private lateinit var fakeBlockRepo: FakeScheduledBlockRepository
    private lateinit var fakeTaskRepo: FakeTaskRepository
    private lateinit var fakeSyncOpRepo: FakeSyncOperationRepository

    private val now = Instant.parse("2026-03-16T14:00:00Z")
    private val taskCalendarId = "task-tracker-calendar-id"

    @Before
    fun setup() {
        fakeCalendarRepo = FakeCalendarRepository(taskCalendarId)
        fakeBlockRepo = FakeScheduledBlockRepository()
        fakeTaskRepo = FakeTaskRepository()
        fakeSyncOpRepo = FakeSyncOperationRepository()
        syncManager = CalendarSyncManager(
            calendarRepository = fakeCalendarRepo,
            blockRepository = fakeBlockRepo,
            taskRepository = fakeTaskRepo,
            syncOperationRepository = fakeSyncOpRepo,
        )
    }

    @Test
    fun `pushNewBlock creates event and updates block with event ID`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        syncManager.pushNewBlock(block)
        assertThat(fakeCalendarRepo.createdEvents).hasSize(1)
        assertThat(fakeBlockRepo.updatedBlocks).hasSize(1)
        assertThat(fakeBlockRepo.updatedBlocks[0].googleCalendarEventId).isNotNull()
    }

    @Test
    fun `markTaskCompleted updates event title with Completed prefix`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
            googleCalendarEventId = "event123",
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        fakeBlockRepo.blocks[1] = block
        syncManager.markTaskCompleted(taskId = 10)
        assertThat(fakeCalendarRepo.updatedEvents["event123"]?.completed).isTrue()
    }

    @Test
    fun `pushNewBlock queues operation when offline`() = runTest {
        fakeCalendarRepo.simulateOffline = true
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
        )
        fakeTaskRepo.tasks[10] = Task(
            id = 10, title = "Test task",
            estimatedDurationMinutes = 60, quadrant = Quadrant.IMPORTANT,
        )
        syncManager.pushNewBlock(block)
        assertThat(fakeSyncOpRepo.operations).hasSize(1)
        assertThat(fakeSyncOpRepo.operations[0].type).isEqualTo(SyncOperationType.CREATE_EVENT)
    }

    @Test
    fun `deleteTaskEvents deletes calendar events and cancels blocks`() = runTest {
        val block = ScheduledBlock(
            id = 1, taskId = 10,
            startTime = now, endTime = now.plus(60, ChronoUnit.MINUTES),
            status = BlockStatus.CONFIRMED,
            googleCalendarEventId = "event123",
        )
        fakeBlockRepo.blocks[1] = block
        syncManager.deleteTaskEvents(taskId = 10)
        assertThat(fakeCalendarRepo.deletedEventIds).contains("event123")
    }

    // Fake implementations for testing
    class FakeCalendarRepository(private val taskCalendarId: String) : CalendarRepository {
        var simulateOffline = false
        val createdEvents = mutableListOf<Pair<ScheduledBlock, String>>()
        val updatedEvents = mutableMapOf<String, UpdatedEvent>()
        val deletedEventIds = mutableListOf<String>()
        private var eventCounter = 0

        data class UpdatedEvent(val block: ScheduledBlock, val title: String, val completed: Boolean)

        override suspend fun listCalendars() = emptyList<CalendarInfo>()
        override suspend fun getOrCreateTaskCalendar() = taskCalendarId
        override suspend fun renameCalendar(calendarId: String, newName: String) {}
        override suspend fun getFreeBusySlots(calendarIds: List<String>, timeMin: Instant, timeMax: Instant) = emptyList<TimeSlot>()
        override suspend fun getEvents(calendarId: String, timeMin: Instant, timeMax: Instant) = emptyList<CalendarEvent>()
        override suspend fun createEvent(calendarId: String, block: ScheduledBlock, taskTitle: String): String {
            if (simulateOffline) throw java.io.IOException("No network")
            val id = "generated-event-${eventCounter++}"
            createdEvents.add(block to taskTitle)
            return id
        }
        override suspend fun updateEvent(calendarId: String, eventId: String, block: ScheduledBlock, taskTitle: String, completed: Boolean) {
            if (simulateOffline) throw java.io.IOException("No network")
            updatedEvents[eventId] = UpdatedEvent(block, taskTitle, completed)
        }
        override suspend fun deleteEvent(calendarId: String, eventId: String) {
            if (simulateOffline) throw java.io.IOException("No network")
            deletedEventIds.add(eventId)
        }
    }

    class FakeScheduledBlockRepository : ScheduledBlockRepository {
        val blocks = mutableMapOf<Long, ScheduledBlock>()
        val updatedBlocks = mutableListOf<ScheduledBlock>()
        override suspend fun insert(block: ScheduledBlock) = block.id
        override suspend fun insertAll(blocks: List<ScheduledBlock>) = blocks.map { it.id }
        override suspend fun update(block: ScheduledBlock) { updatedBlocks.add(block); blocks[block.id] = block }
        override suspend fun delete(block: ScheduledBlock) { blocks.remove(block.id) }
        override suspend fun getByTaskId(taskId: Long) = blocks.values.filter { it.taskId == taskId }
        override fun observeByTaskId(taskId: Long) = kotlinx.coroutines.flow.flowOf(blocks.values.filter { it.taskId == taskId })
        override suspend fun getByStatuses(statuses: List<BlockStatus>) = blocks.values.filter { it.status in statuses }
        override fun observeInRange(start: Instant, end: Instant) = kotlinx.coroutines.flow.flowOf(emptyList<ScheduledBlock>())
        override suspend fun updateStatus(id: Long, status: BlockStatus) { blocks[id] = blocks[id]!!.copy(status = status) }
        override suspend fun deleteByTaskId(taskId: Long) { blocks.entries.removeAll { it.value.taskId == taskId } }
        override suspend fun deleteProposed() { blocks.entries.removeAll { it.value.status == BlockStatus.PROPOSED } }
    }

    class FakeTaskRepository : TaskRepository {
        val tasks = mutableMapOf<Long, Task>()
        override suspend fun insert(task: Task) = task.id
        override suspend fun update(task: Task) { tasks[task.id] = task }
        override suspend fun delete(task: Task) { tasks.remove(task.id) }
        override suspend fun getById(id: Long) = tasks[id]
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(tasks.values.toList())
        override fun observeAllWithScheduleInfo() = kotlinx.coroutines.flow.flowOf(
            tasks.values.map { com.tasktracker.domain.model.TaskWithScheduleInfo(task = it) }
        )
        override suspend fun getByStatus(status: TaskStatus) = tasks.values.filter { it.status == status }
        override suspend fun getByStatuses(statuses: List<TaskStatus>) = tasks.values.filter { it.status in statuses }
        override suspend fun updateStatus(id: Long, status: TaskStatus) { tasks[id] = tasks[id]!!.copy(status = status) }
    }

    class FakeSyncOperationRepository : SyncOperationRepository {
        val operations = mutableListOf<SyncOperation>()
        override suspend fun enqueue(operation: SyncOperation): Long { operations.add(operation); return operation.id }
        override suspend fun dequeue(operation: SyncOperation) { operations.removeAll { it.id == operation.id } }
        override suspend fun getAll() = operations.toList()
        override suspend fun clear() { operations.clear() }
        override suspend fun hasPending() = operations.isNotEmpty()
    }
}
