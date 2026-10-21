package com.wac.worktrack

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Posts a daily-schedule notification, then re-arms the same block for the next day. */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Scheduled"
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        val notification = NotificationCompat.Builder(context, WorkTrackApp.CHANNEL_SCHEDULE)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText("Time for: $title")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(ScheduleAlarm.NOTIF_BASE + id, notification)

        // Re-arm for tomorrow (the block repeats every day).
        ScheduleAlarm.schedule(context, DayBlock(id, title, minute, notify = true))
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MINUTE = "minute"
    }
}
