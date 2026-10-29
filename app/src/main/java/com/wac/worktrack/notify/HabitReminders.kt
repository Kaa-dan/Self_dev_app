package com.wac.worktrack.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import kotlinx.coroutines.launch

/** One notification a day per habit that has a time. */
object HabitReminders {
    private const val CHANNEL = "habits"
    private const val EXTRA_HABIT = "habit"
    const val ACTION_REMIND = "com.wac.worktrack.REMIND"
    const val ACTION_DONE = "com.wac.worktrack.DONE"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Next reminder for [habit], or cancels it when the habit has no time. */
    fun schedule(context: Context, habit: Habit) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = remindIntent(context, habit.id)
        val minutes = habit.timeMinutes ?: return alarms.cancel(pending)
        val today = Day.today()
        var at = Day.millisAt(today, minutes)
        if (at <= System.currentTimeMillis()) at = Day.millisAt(today + 1, minutes)
        // Inexact (within 10 minutes) so no exact-alarm permission is needed.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pending)
    }

    fun cancel(context: Context, habitId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(remindIntent(context, habitId))
        NotificationManagerCompat.from(context).cancel(habitId.toInt())
    }

    suspend fun rescheduleAll(context: Context) {
        context.db.habits().habitsNow().forEach { schedule(context, it) }
    }

    fun show(context: Context, habit: Habit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, habit.id.toInt(),
            Intent(context, HabitReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_HABIT, habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(habit.name)
            .setContentText(habit.durationMinutes?.let { "Time for it · ${durationLabel(it)}" } ?: "Time for it")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Done", done)
            .build()
        NotificationManagerCompat.from(context).notify(habit.id.toInt(), notification)
    }

    private fun remindIntent(context: Context, habitId: Long) = PendingIntent.getBroadcast(
        context, habitId.toInt(),
        Intent(context, HabitReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_HABIT, habitId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun habitId(intent: Intent) = intent.getLongExtra(EXTRA_HABIT, -1)
}

class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = HabitReminders.habitId(intent)
        val pending = goAsync()
        (context.applicationContext as WorkTrackApp).scope.launch {
            try {
                val habit = context.db.habits().habit(id) ?: return@launch
                when (intent.action) {
                    HabitReminders.ACTION_REMIND -> {
                        if (!context.db.habits().isLogged(id, Day.today())) HabitReminders.show(context, habit)
                        HabitReminders.schedule(context, habit)
                    }
                    HabitReminders.ACTION_DONE -> {
                        context.db.habits().log(HabitLog(id, Day.today()))
                        NotificationManagerCompat.from(context).cancel(id.toInt())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as WorkTrackApp).scope.launch {
            try {
                HabitReminders.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
