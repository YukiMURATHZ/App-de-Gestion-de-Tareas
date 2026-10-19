package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderManager = ReminderManager(context)

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule after reboot
            if (reminderManager.isDailyReminderEnabled()) {
                reminderManager.scheduleDailyAlarm()
            }
            return
        }

        // Daily alarm triggered
        if (reminderManager.isDailyReminderEnabled()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val tasks = db.taskDao().getPendingTasksForReminder()
                    val titles = tasks.map { it.title }
                    reminderManager.showDailyNotification(tasks.size, titles)
                    // Schedule next day's alarm
                    reminderManager.scheduleDailyAlarm()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
