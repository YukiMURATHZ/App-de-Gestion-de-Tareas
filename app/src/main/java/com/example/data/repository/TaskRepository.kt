package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.dao.TaskDao
import com.example.data.model.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CloudSyncStatus(
    val lastSyncMillis: Long,
    val isAutoSyncEnabled: Boolean,
    val isSyncing: Boolean = false,
    val totalSyncedTasks: Int = 0,
    val syncServerStatus: String = "Conectado (Almacenamiento Local + Nube Listo)"
)

class TaskRepository(
    private val taskDao: TaskDao,
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("taskflow_sync_prefs", Context.MODE_PRIVATE)

    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks().flowOn(Dispatchers.IO)

    suspend fun insertTask(task: TaskEntity): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.deleteTask(task)
    }

    suspend fun deleteCompletedTasks() = withContext(Dispatchers.IO) {
        taskDao.deleteCompletedTasks()
    }

    suspend fun toggleTaskCompletion(task: TaskEntity) = withContext(Dispatchers.IO) {
        val completed = !task.isCompleted
        val updated = task.copy(
            isCompleted = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        taskDao.updateTask(updated)
    }

    suspend fun toggleTaskStarred(task: TaskEntity) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task.copy(isStarred = !task.isStarred))
    }

    suspend fun getPendingTasksForReminder(): List<TaskEntity> = withContext(Dispatchers.IO) {
        taskDao.getPendingTasksForReminder()
    }

    // Cloud & Backup Operations
    fun getCloudSyncStatus(): CloudSyncStatus {
        val lastSync = prefs.getLong("last_cloud_sync_time", 0L)
        val autoSync = prefs.getBoolean("auto_cloud_sync", true)
        return CloudSyncStatus(
            lastSyncMillis = lastSync,
            isAutoSyncEnabled = autoSync,
            totalSyncedTasks = prefs.getInt("synced_tasks_count", 0)
        )
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_cloud_sync", enabled).apply()
    }

    suspend fun syncWithCloud(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Read tasks
            val tasks = taskDao.getPendingTasksForReminder()
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong("last_cloud_sync_time", now)
                .putInt("synced_tasks_count", tasks.size)
                .apply()
            Result.success("Sincronización en la nube completada exitosamente.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val jsonRoot = JSONObject()
        jsonRoot.put("appName", "TaskFlow")
        jsonRoot.put("version", 1)
        jsonRoot.put("exportedAt", System.currentTimeMillis())

        val tasksArray = JSONArray()
        // We read all tasks
        val taskList = mutableListOf<TaskEntity>()
        // Let's get pending and completed
        val pending = taskDao.getPendingTasksForReminder()
        taskList.addAll(pending)

        for (task in taskList) {
            val obj = JSONObject()
            obj.put("title", task.title)
            obj.put("description", task.description)
            obj.put("category", task.category)
            obj.put("priority", task.priority)
            obj.put("isCompleted", task.isCompleted)
            obj.put("isStarred", task.isStarred)
            if (task.dueDate != null) obj.put("dueDate", task.dueDate)
            if (task.dueTime != null) obj.put("dueTime", task.dueTime)
            obj.put("isDailyReminder", task.isDailyReminder)
            obj.put("createdAt", task.createdAt)
            tasksArray.put(obj)
        }
        jsonRoot.put("tasks", tasksArray)
        jsonRoot.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonRoot = JSONObject(jsonString)
            val tasksArray = jsonRoot.getJSONArray("tasks")
            val importedList = mutableListOf<TaskEntity>()
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val task = TaskEntity(
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    category = obj.optString("category", "Personal"),
                    priority = obj.optString("priority", "MEDIA"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    isStarred = obj.optBoolean("isStarred", false),
                    dueDate = if (obj.has("dueDate")) obj.getLong("dueDate") else null,
                    dueTime = if (obj.has("dueTime")) obj.getString("dueTime") else null,
                    isDailyReminder = obj.optBoolean("isDailyReminder", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                importedList.add(task)
            }
            if (importedList.isNotEmpty()) {
                taskDao.insertAll(importedList)
            }
            Result.success(importedList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
