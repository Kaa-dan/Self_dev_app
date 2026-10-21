package com.wac.worktrack

/**
 * One recurring block in the daily routine (e.g. "Wake up" at 06:30).
 * [minuteOfDay] is 0..1439 (minutes since midnight); the block repeats every day.
 * When [notify] is true, a notification fires at that time each day.
 */
data class DayBlock(
    val id: Int,
    val title: String,
    val minuteOfDay: Int,
    val notify: Boolean,
) {
    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60
}
