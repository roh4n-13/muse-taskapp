package com.tasktracker.pro.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tasktracker.pro.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-schedules the daily reminder after a reboot, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = context.appContainer()
                if (container.settings.reminderEnabled.first()) {
                    val hour = container.settings.reminderHour.first()
                    val minute = container.settings.reminderMinute.first()
                    container.reminders.scheduleDaily(hour, minute)
                }
            } catch (e: Exception) {
                // Best effort on boot.
            } finally {
                pending.finish()
            }
        }
    }
}
