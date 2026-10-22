package com.wac.worktrack

import java.util.Calendar

/**
 * A reminder shown on the calendar.
 * - One-off ([repeatDaily] = false): fires once at [timeMillis].
 * - Daily ([repeatDaily] = true): fires every day at the time-of-day of [timeMillis].
 */
data class Reminder(
    val id: Int,
    val title: String,
    val timeMillis: Long,
    val repeatDaily: Boolean = false,
) {
    /** Minutes since midnight of [timeMillis], used for sorting and daily re-arming. */
    val minuteOfDay: Int
        get() = Calendar.getInstance().apply { timeInMillis = timeMillis }
            .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
}
