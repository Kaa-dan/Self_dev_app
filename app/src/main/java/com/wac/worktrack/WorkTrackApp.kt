package com.wac.worktrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WorkTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Scheduled reminder alerts" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCHEDULE, "Daily schedule", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts for your daily routine blocks" },
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_SCHEDULE = "schedule"
    }
}
