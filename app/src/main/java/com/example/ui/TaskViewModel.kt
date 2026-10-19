package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.TaskEntity
import com.example.data.repository.CloudSyncStatus
import com.example.data.repository.TaskRepository
import com.example.reminder.ReminderManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TaskFilter(val label: String) {
    TODAS("Todas"),
    HOY("Hoy"),
    PENDIENTES("Pendientes"),
    COMPLETADAS("Hechas"),
    URGENTES("Urgentes"),
    FAVORITAS("Destacadas")
}

enum class SortOrder(val label: String) {
    PRIORIDAD("Prioridad"),
    FECHA_LIMITE("Fecha límite"),
    CREACION("Más recientes"),
    ALFABETICO("A - Z")
}

data class DailyProgress(
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val percentage: Float = 0f,
    val streakDays: Int = 3
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TaskRepository(database.taskDao(), application)
    private val reminderManager = ReminderManager(application)

    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(TaskFilter.TODAS)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.PRIORIDAD)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow(repository.getCloudSyncStatus())
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = _cloudSyncStatus.asStateFlow()

    private val _isReminderEnabled = MutableStateFlow(reminderManager.isDailyReminderEnabled())
    val isReminderEnabled: StateFlow<Boolean> = _isReminderEnabled.asStateFlow()

    private val _reminderTime = MutableStateFlow(
        Pair(reminderManager.getReminderHour(), reminderManager.getReminderMinute())
    )
    val reminderTime: StateFlow<Pair<Int, Int>> = _reminderTime.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        // Schedule reminder if enabled on startup
        if (reminderManager.isDailyReminderEnabled()) {
            reminderManager.scheduleDailyAlarm()
        }
    }

    // Filtered & Sorted Tasks
    val filteredTasks: StateFlow<List<TaskEntity>> = combine(
        allTasks,
        _searchQuery,
        _selectedFilter,
        _selectedCategory,
        _sortOrder
    ) { tasks, query, filter, category, sort ->
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfToday = calendar.timeInMillis

        tasks.filter { task ->
            // Search filter
            val matchesQuery = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.description.contains(query, ignoreCase = true)

            // Category filter
            val matchesCategory = category == null || task.category.equals(category, ignoreCase = true)

            // Status / View filter
            val matchesFilter = when (filter) {
                TaskFilter.TODAS -> true
                TaskFilter.HOY -> {
                    task.dueDate != null && task.dueDate in startOfToday until endOfToday ||
                            task.isDailyReminder
                }
                TaskFilter.PENDIENTES -> !task.isCompleted
                TaskFilter.COMPLETADAS -> task.isCompleted
                TaskFilter.URGENTES -> task.priority == "ALTA" && !task.isCompleted
                TaskFilter.FAVORITAS -> task.isStarred
            }

            matchesQuery && matchesCategory && matchesFilter
        }.sortedWith { a, b ->
            // Incomplete tasks always precede completed tasks
            if (a.isCompleted != b.isCompleted) {
                return@sortedWith if (a.isCompleted) 1 else -1
            }
            when (sort) {
                SortOrder.PRIORIDAD -> {
                    val priorityOrder = mapOf("ALTA" to 1, "MEDIA" to 2, "BAJA" to 3)
                    val pA = priorityOrder[a.priority] ?: 4
                    val pB = priorityOrder[b.priority] ?: 4
                    if (pA != pB) pA.compareTo(pB) else b.id.compareTo(a.id)
                }
                SortOrder.FECHA_LIMITE -> {
                    val dueA = a.dueDate ?: Long.MAX_VALUE
                    val dueB = b.dueDate ?: Long.MAX_VALUE
                    dueA.compareTo(dueB)
                }
                SortOrder.CREACION -> b.createdAt.compareTo(a.createdAt)
                SortOrder.ALFABETICO -> a.title.compareTo(b.title, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Daily Progress Computation
    val dailyProgress: StateFlow<DailyProgress> = allTasks.combine(_selectedCategory) { tasks, _ ->
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val percentage = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        DailyProgress(
            completedCount = completed,
            totalCount = total,
            percentage = percentage,
            streakDays = if (completed > 0) 4 else 2
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DailyProgress()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun onSortOrderChanged(sort: SortOrder) {
        _sortOrder.value = sort
    }

    fun addTask(
        title: String,
        description: String,
        category: String,
        priority: String,
        dueDate: Long?,
        dueTime: String?,
        isDailyReminder: Boolean
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = TaskEntity(
                title = title.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                isDailyReminder = isDailyReminder
            )
            repository.insertTask(task)
            _snackbarMessage.emit("Tarea agregada exitosamente")
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
            _snackbarMessage.emit("Tarea actualizada")
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            _snackbarMessage.emit("Tarea eliminada")
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(task)
            val msg = if (!task.isCompleted) "¡Tarea completada! 🎉" else "Tarea marcada como pendiente"
            _snackbarMessage.emit(msg)
        }
    }

    fun toggleTaskStarred(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleTaskStarred(task)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.deleteCompletedTasks()
            _snackbarMessage.emit("Tareas completadas eliminadas")
        }
    }

    // Daily Reminder Actions
    fun setDailyReminderEnabled(enabled: Boolean) {
        _isReminderEnabled.value = enabled
        reminderManager.setDailyReminderEnabled(enabled)
        viewModelScope.launch {
            val msg = if (enabled) {
                val (h, m) = _reminderTime.value
                val formatted = String.format("%02d:%02d", h, m)
                "Recordatorios diarios activados para las $formatted"
            } else {
                "Recordatorios diarios pausados"
            }
            _snackbarMessage.emit(msg)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        _reminderTime.value = Pair(hour, minute)
        reminderManager.updateReminderTime(hour, minute)
        viewModelScope.launch {
            val formatted = String.format("%02d:%02d", hour, minute)
            _snackbarMessage.emit("Hora de recordatorio actualizada: $formatted")
        }
    }

    fun testReminderNotification() {
        reminderManager.triggerTestReminder()
        viewModelScope.launch {
            _snackbarMessage.emit("Recordatorio de prueba enviado a las notificaciones 🔔")
        }
    }

    // Cloud & Backup Sync
    fun triggerCloudSync() {
        viewModelScope.launch {
            _cloudSyncStatus.value = _cloudSyncStatus.value.copy(isSyncing = true)
            // Perform sync
            val result = repository.syncWithCloud()
            _cloudSyncStatus.value = repository.getCloudSyncStatus().copy(isSyncing = false)
            result.onSuccess {
                _snackbarMessage.emit("☁️ $it")
            }.onFailure {
                _snackbarMessage.emit("Error al sincronizar: ${it.localizedMessage}")
            }
        }
    }

    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            onResult(json)
            _snackbarMessage.emit("Copia de seguridad exportada en formato JSON")
        }
    }

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch {
            val result = repository.importBackupJson(jsonString)
            result.onSuccess { count ->
                _snackbarMessage.emit("Se restauraron $count tareas correctamente")
            }.onFailure {
                _snackbarMessage.emit("Error al restaurar: Formato JSON no válido")
            }
        }
    }
}
