package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("taskflow_reminder_prefs", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        const val CHANNEL_ID = "taskflow_daily_reminders"
        const val CHANNEL_NAME = "Recordatorios Diarios"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DAILY_REMINDER = "com.aistudio.taskflow.ACTION_DAILY_REMINDER"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de tareas diarias y pendientes de TaskFlow"
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun isDailyReminderEnabled(): Boolean {
        return prefs.getBoolean("daily_reminder_enabled", true)
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("daily_reminder_enabled", enabled).apply()
        if (enabled) {
            scheduleDailyAlarm()
        } else {
            cancelDailyAlarm()
        }
    }

    fun getReminderHour(): Int = prefs.getInt("reminder_hour", 9)
    fun getReminderMinute(): Int = prefs.getInt("reminder_minute", 0)

    fun updateReminderTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()
        if (isDailyReminderEnabled()) {
            scheduleDailyAlarm()
        }
    }

    fun scheduleDailyAlarm() {
        val hour = getReminderHour()
        val minute = getReminderMinute()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    am.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // In case exact alarm permission is restricted
                am.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelDailyAlarm() {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
    }

    fun showDailyNotification(pendingCount: Int, taskTitles: List<String>) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (pendingCount > 0) {
            "🎯 Recordatorio Diario: $pendingCount tarea${if (pendingCount > 1) "s" else ""} pendiente${if (pendingCount > 1) "s" else ""}"
        } else {
            "🎉 ¡Todo al día en TaskFlow!"
        }

        val message = if (pendingCount > 0) {
            taskTitles.take(3).joinToString(separator = "\n") { "• $it" } +
                    if (taskTitles.size > 3) "\n...y ${taskTitles.size - 3} más" else ""
        } else {
            "No tienes tareas pendientes por ahora. ¡Excelente trabajo!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon_1792379234902)
            .setContentTitle(title)
            .setContentText(if (pendingCount > 0) "Toca para ver y completar tus tareas de hoy" else "Toca para planificar nuevas tareas")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun triggerTestReminder() {
        CoroutineScope(Dispatchers.IO).launch {
            val tasks = AppDatabase.getDatabase(context).taskDao().getPendingTasksForReminder()
            val titles = tasks.map { it.title }
            showDailyNotification(tasks.size, titles)
        }
    }
}
