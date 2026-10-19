package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TaskFlow", appName)
    }

    @Test
    fun `insert and retrieve task from database`() = runBlocking {
        val dao = database.taskDao()
        val task = TaskEntity(
            title = "Test task",
            description = "Test description",
            category = "Trabajo",
            priority = "ALTA",
            isDailyReminder = true
        )
        val id = dao.insertTask(task)
        assertTrue(id > 0)

        val retrieved = dao.getTaskById(id)
        assertNotNull(retrieved)
        assertEquals("Test task", retrieved?.title)
        assertEquals("ALTA", retrieved?.priority)

        val all = dao.getAllTasks().first()
        assertEquals(1, all.size)
    }
}
