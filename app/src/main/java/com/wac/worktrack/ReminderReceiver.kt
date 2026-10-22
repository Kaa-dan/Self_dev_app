package com.wac.worktrack

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Posts the reminder notification when its alarm fires; re-arms daily reminders for the next day. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val repeatDaily = intent.getBooleanExtra(EXTRA_REPEAT, false)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        val notification = NotificationCompat.Builder(context, WorkTrackApp.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(if (repeatDaily) "Daily reminder" else "Reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)

        // A daily reminder re-arms itself for tomorrow (next occurrence of the same time).
        if (repeatDaily) {
            ReminderScheduler.schedule(
                context,
                Reminder(id, title, ScheduleAlarm.nextTrigger(minute), repeatDaily = true),
            )
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REPEAT = "repeat"
        const val EXTRA_MINUTE = "minute"
    }
}
