package com.wac.worktrack.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.data.HabitTimer
import com.wac.worktrack.ui.AlarmActivity
import kotlinx.coroutines.launch

/**
 * Habit alarms and timers.
 *
 * A habit with a time rings like an alarm clock at that time (or posts a quiet notification when
 * [Habit.ringAlarm] is off). A habit with a duration has a start/pause timer: while it runs an
 * ongoing notification counts down, and when the time is up it rings and the habit is marked done.
 */
object HabitAlarms {
    private const val CH_ALARM = "habit_alarm"
    private const val CH_REMIND = "habits"
    private const val CH_TIMER = "habit_timer"
    private const val EXTRA_HABIT = "habit"

    const val RING = "com.wac.worktrack.RING"
    const val SNOOZED_RING = "com.wac.worktrack.SNOOZED_RING"
    const val DONE = "com.wac.worktrack.DONE"
    const val SNOOZE = "com.wac.worktrack.SNOOZE"
    const val DISMISS = "com.wac.worktrack.DISMISS"
    const val START = "com.wac.worktrack.START"
    const val PAUSE = "com.wac.worktrack.PAUSE"
    const val TIMER_UP = "com.wac.worktrack.TIMER_UP"

    const val SNOOZE_MINUTES = 10

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "Habit alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings at a habit's time and when a habit timer finishes"
                setSound(
                    alarmSound,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER, "Habit timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows a running habit timer"
                setShowBadge(false)
            },
        )
    }

    // ------------------------------------------------------------ Scheduling

    /** Sets the habit's next daily alarm, or cancels it when the habit has no time. */
    fun schedule(context: Context, habit: Habit) {
        val pending = broadcast(context, RING, habit.id)
        val minutes = habit.timeMinutes ?: return alarms(context).cancel(pending)
        val today = Day.today()
        var at = Day.millisAt(today, minutes)
        if (at <= System.currentTimeMillis()) at = Day.millisAt(today + 1, minutes)
        setExact(context, at, pending)
    }

    fun cancel(context: Context, habitId: Long) {
        listOf(RING, SNOOZED_RING, TIMER_UP).forEach { alarms(context).cancel(broadcast(context, it, habitId)) }
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ringId(habitId))
        nm.cancel(timerId(habitId))
    }

    /** After boot / update / clock change: every habit's next alarm, and any timer still running. */
    suspend fun rescheduleAll(context: Context) {
        val dao = context.db.habits()
        dao.habitsNow().forEach { schedule(context, it) }
        dao.runningTimers().forEach { t -> dao.habit(t.habitId)?.let { scheduleTimerUp(context, it, t); showTimer(context, it, t) } }
    }

    /** Exact and allowed in Doze: shown as the next alarm, like a clock app's alarm. */
    private fun setExact(context: Context, at: Long, pending: PendingIntent) {
        val am = alarms(context)
        if (canScheduleExact(context)) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(context)), pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun canScheduleExact(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context).canScheduleExactAlarms()

    /** Whether the alarm can take over the screen (Android 14+ lets users turn this off). */
    fun canFullScreen(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Settings screen that fixes whichever alarm permission is missing. */
    fun permissionSettings(context: Context): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when {
            !canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            !canFullScreen(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            else -> null
        }
    }

    // ------------------------------------------------------------ Actions

    /** Runs [action] for [habitId] on the app scope (from the UI, the alarm screen or a notification). */
    fun perform(context: Context, action: String, habitId: Long, onDone: () -> Unit = {}) {
        val app = context.applicationContext as WorkTrackApp
        app.scope.launch {
            try {
                handle(app, action, habitId)
            } finally {
                onDone()
            }
        }
    }

    suspend fun handle(context: Context, action: String, habitId: Long) {
        val dao = context.db.habits()
        val habit = dao.habit(habitId) ?: return cancel(context, habitId)
        val today = Day.today()
        when (action) {
            RING, SNOOZED_RING -> {
                if (action == RING) schedule(context, habit) // tomorrow's alarm
                val timerRunning = dao.timer(habitId, today)?.runningSince != null
                if (!dao.isLogged(habitId, today) && !timerRunning) ring(context, habit)
            }
            DONE -> {
                stopRinging(context, habitId)
                pause(context, habitId)
                dao.log(HabitLog(habitId, today))
            }
            SNOOZE -> {
                stopRinging(context, habitId)
                setExact(context, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, broadcast(context, SNOOZED_RING, habitId))
            }
            DISMISS -> stopRinging(context, habitId)
            START -> start(context, habit)
            PAUSE -> pause(context, habitId)
            TIMER_UP -> {
                pause(context, habitId)
                dao.log(HabitLog(habitId, today))
                ringFinished(context, habit)
            }
        }
    }

    /** Ticking a habit off in the app stops its alarm and timer. */
    suspend fun onMarkedDone(context: Context, habitId: Long) {
        stopRinging(context, habitId)
        pause(context, habitId)
    }

    // ------------------------------------------------------------ Timer

    private suspend fun start(context: Context, habit: Habit) {
        stopRinging(context, habit.id)
        val dao = context.db.habits()
        val today = Day.today()
        val now = System.currentTimeMillis()
        val timer = dao.timer(habit.id, today) ?: HabitTimer(habit.id, today)
        if (timer.runningSince != null) return
        val running = timer.copy(runningSince = now)
        dao.saveTimer(running)
        scheduleTimerUp(context, habit, running)
        showTimer(context, habit, running)
    }

    private suspend fun pause(context: Context, habitId: Long) {
        val dao = context.db.habits()
        val running = dao.runningTimers().filter { it.habitId == habitId }
        val now = System.currentTimeMillis()
        running.forEach { dao.saveTimer(it.copy(accumulatedMillis = it.elapsed(now), runningSince = null)) }
        alarms(context).cancel(broadcast(context, TIMER_UP, habitId))
        NotificationManagerCompat.from(context).cancel(timerId(habitId))
    }

    private fun scheduleTimerUp(context: Context, habit: Habit, timer: HabitTimer) {
        val target = (habit.durationMinutes ?: return) * 60_000L
        val start = timer.runningSince ?: return
        val left = target - timer.accumulatedMillis
        if (left > 0) setExact(context, start + left, broadcast(context, TIMER_UP, habit.id))
    }

    /** Ongoing notification that counts down (or up, without a duration) while the timer runs. */
    private fun showTimer(context: Context, habit: Habit, timer: HabitTimer) {
        if (!canNotify(context)) return
        val start = timer.runningSince ?: return
        val target = habit.durationMinutes?.let { it * 60_000L }
        val builder = NotificationCompat.Builder(context, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("${habit.emoji} ${habit.name}")
            .setContentText(habit.durationMinutes?.let { "Timer running · ${durationLabel(it)} goal" } ?: "Timer running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setUsesChronometer(true)
            .setContentIntent(openApp(context))
            .addAction(0, "⏸ Pause", broadcast(context, PAUSE, habit.id))
            .addAction(0, "✓ Done", broadcast(context, DONE, habit.id))
        if (target != null && target > timer.accumulatedMillis) {
            builder.setWhen(start + target - timer.accumulatedMillis).setChronometerCountDown(true)
        } else {
            builder.setWhen(start - timer.accumulatedMillis)
        }
        NotificationManagerCompat.from(context).notify(timerId(habit.id), builder.build())
    }

    // ------------------------------------------------------------ Ringing

    private fun ring(context: Context, habit: Habit) {
        if (!canNotify(context)) return
        val nm = NotificationManagerCompat.from(context)
        if (!habit.ringAlarm) {
            val n = NotificationCompat.Builder(context, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle("${habit.emoji} ${habit.name}")
                .setContentText(habit.durationMinutes?.let { "Time for it · ${durationLabel(it)}" } ?: "Time for it")
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .addAction(0, if (habit.durationMinutes != null) "▶ Start" else "✓ Done", broadcast(context, if (habit.durationMinutes != null) START else DONE, habit.id))
                .build()
            nm.notify(ringId(habit.id), n)
            return
        }
        val screen = PendingIntent.getActivity(
            context, habit.id.toInt(),
            Intent(context, AlarmActivity::class.java).putExtra(EXTRA_HABIT, habit.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val timed = habit.durationMinutes != null
        val n = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("${habit.emoji} ${habit.name}")
            .setContentText(
                listOfNotNull(habit.timeMinutes?.let(::timeLabel), habit.durationMinutes?.let(::durationLabel)).joinToString(" · ")
                    .ifBlank { "Time for your habit" },
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            // Stop ringing on its own after 5 minutes, like a clock alarm.
            .setTimeoutAfter(5 * 60_000L)
            .addAction(0, if (timed) "▶ Start" else "✓ Done", broadcast(context, if (timed) START else DONE, habit.id))
            .addAction(0, "😴 $SNOOZE_MINUTES min", broadcast(context, SNOOZE, habit.id))
            .addAction(0, "Dismiss", broadcast(context, DISMISS, habit.id))
            .build()
        // Insistent: the alarm sound repeats until the user acts on it.
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(ringId(habit.id), n)
    }

    /** Short alarm when a timer reaches its duration. */
    private fun ringFinished(context: Context, habit: Habit) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("🎉 ${habit.name} done!")
            .setContentText("${habit.durationMinutes?.let(::durationLabel) ?: "Time"} complete. Marked as done.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .build()
        NotificationManagerCompat.from(context).notify(ringId(habit.id), n)
    }

    fun stopRinging(context: Context, habitId: Long) {
        NotificationManagerCompat.from(context).cancel(ringId(habitId))
        alarms(context).cancel(broadcast(context, SNOOZED_RING, habitId))
    }

    // ------------------------------------------------------------ Plumbing

    fun habitId(intent: Intent) = intent.getLongExtra(EXTRA_HABIT, -1)

    private fun ringId(habitId: Long) = (habitId * 10 + 1).toInt()
    private fun timerId(habitId: Long) = (habitId * 10 + 2).toInt()

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One PendingIntent per action and habit (they differ by action, so they don't replace each other). */
    private fun broadcast(context: Context, action: String, habitId: Long) = PendingIntent.getBroadcast(
        context, habitId.toInt(),
        Intent(context, HabitReceiver::class.java).setAction(action).putExtra(EXTRA_HABIT, habitId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}

class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        HabitAlarms.perform(context, intent.action ?: return pending.finish(), HabitAlarms.habitId(intent)) { pending.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as WorkTrackApp).scope.launch {
            try {
                HabitAlarms.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
