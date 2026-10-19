package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.TaskDao
import com.example.data.model.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taskflow_database"
                )
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialData(getDatabase(context).taskDao())
                }
            }

            private suspend fun populateInitialData(dao: TaskDao) {
                val now = System.currentTimeMillis()
                val oneDay = 24 * 60 * 60 * 1000L
                val initialTasks = listOf(
                    TaskEntity(
                        title = "Planificar prioridades del día",
                        description = "Definir las 3 tareas de mayor impacto para hoy",
                        category = "Trabajo",
                        priority = "ALTA",
                        isCompleted = false,
                        isStarred = true,
                        dueDate = now,
                        dueTime = "09:00",
                        isDailyReminder = true,
                        createdAt = now
                    ),
                    TaskEntity(
                        title = "Configurar recordatorios diarios en TaskFlow",
                        description = "Ajustar la hora preferida en el panel de notificaciones",
                        category = "Personal",
                        priority = "ALTA",
                        isCompleted = false,
                        isStarred = true,
                        dueDate = now,
                        dueTime = "10:30",
                        isDailyReminder = true,
                        createdAt = now
                    ),
                    TaskEntity(
                        title = "Beber 2 litros de agua y pausa activa",
                        description = "Mantener hidratación y 5 minutos de estiramientos",
                        category = "Salud",
                        priority = "MEDIA",
                        isCompleted = false,
                        isStarred = false,
                        dueDate = now,
                        dueTime = "14:00",
                        isDailyReminder = true,
                        createdAt = now
                    ),
                    TaskEntity(
                        title = "Revisar finanzas y presupuesto mensual",
                        description = "Categorizar gastos recientes y verificar metas de ahorro",
                        category = "Finanzas",
                        priority = "MEDIA",
                        isCompleted = false,
                        isStarred = false,
                        dueDate = now + oneDay,
                        dueTime = "18:00",
                        isDailyReminder = false,
                        createdAt = now
                    ),
                    TaskEntity(
                        title = "Lectura de 20 minutos antes de dormir",
                        description = "Continuar con libro de desarrollo profesional o ficción",
                        category = "Estudio",
                        priority = "BAJA",
                        isCompleted = true,
                        isStarred = false,
                        dueDate = now,
                        dueTime = "22:00",
                        isDailyReminder = true,
                        createdAt = now - (2 * 60 * 60 * 1000L),
                        completedAt = now - (1 * 60 * 60 * 1000L)
                    )
                )
                dao.insertAll(initialTasks)
            }
        }
    }
}
