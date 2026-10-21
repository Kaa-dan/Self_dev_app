package com.wac.worktrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules the daily-recurring alarms for [DayBlock]s that trigger [ScheduleReceiver].
 *
 * AlarmManager exact alarms don't repeat on their own, so each block is scheduled for its
 * next occurrence and [ScheduleReceiver] re-arms it for the following day when it fires.
 * Request/notification ids are offset from the Reminders feature so the two never collide.
 */
object ScheduleAlarm {

    private const val REQ_BASE = 100_000
    const val NOTIF_BASE = 200_000

    /** Next epoch-millis at which [minuteOfDay] occurs, today if still ahead else tomorrow. */
    fun nextTrigger(minuteOfDay: Int, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context, block: DayBlock): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_ID, block.id)
            putExtra(ScheduleReceiver.EXTRA_TITLE, block.title)
            putExtra(ScheduleReceiver.EXTRA_MINUTE, block.minuteOfDay)
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_BASE + block.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, block: DayBlock) {
        val pi = pendingIntent(context, block)
        val am = context.getSystemService(AlarmManager::class.java)
        if (!block.notify) {   // nothing to fire; make sure any old alarm is gone
            am.cancel(pi)
            return
        }
        val at = nextTrigger(block.minuteOfDay)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, block: DayBlock) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, block))
    }

    /** Re-arms every stored block. Called on app open and after a device reboot. */
    fun rescheduleAll(context: Context) {
        ScheduleStore.load(context).forEach { schedule(context, it) }
    }
}
