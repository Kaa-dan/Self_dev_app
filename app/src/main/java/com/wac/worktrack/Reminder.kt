package com.wac.worktrack

/** A one-off reminder that fires a notification at [timeMillis]. */
data class Reminder(
    val id: Int,
    val title: String,
    val timeMillis: Long,
)
