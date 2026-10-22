package com.wac.worktrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Schedules/cancels exact alarms that trigger [ReminderReceiver]. */
object ReminderScheduler {

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderReceiver.EXTRA_REPEAT, reminder.repeatDaily)
            putExtra(ReminderReceiver.EXTRA_MINUTE, reminder.minuteOfDay)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, reminder)
        val at = if (reminder.repeatDaily) {
            ScheduleAlarm.nextTrigger(reminder.minuteOfDay)
        } else {
            reminder.timeMillis
        }
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Falls back to an inexact alarm if the user hasn't granted exact-alarm access.
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context, reminder))
    }

    /** Re-arms future one-off reminders and all daily reminders. Called on app open and after reboot. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        ReminderStore.load(context).forEach { r ->
            if (r.repeatDaily || r.timeMillis > now) schedule(context, r)
        }
    }
}
