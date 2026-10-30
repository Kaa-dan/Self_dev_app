package com.wac.worktrack.block

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.wac.worktrack.data.AppBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Blocks {

    /** When [block] stops blocking, if it is blocking at [now]; null when it isn't. */
    fun endsAt(block: AppBlock, now: Long): Long? {
        if (!block.daily) return block.untilMillis.takeIf { now < it }
        val zone = ZoneId.systemDefault()
        val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val minute = at.hour * 60 + at.minute
        val start = block.startMinutes
        val end = block.endMinutes
        val endDay: LocalDate = when {
            start < end -> if (minute in start until end) at.toLocalDate() else return null
            // Overnight, e.g. 10 PM – 7 AM.
            start > end -> when {
                minute >= start -> at.toLocalDate().plusDays(1)
                minute < end -> at.toLocalDate()
                else -> return null
            }
            else -> return null
        }
        return endDay.atTime(LocalTime.of(end / 60, end % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    /** When [pkg] can be opened again, if any block covers it right now. */
    fun blockedUntil(blocks: List<AppBlock>, pkg: String, now: Long): Long? =
        blocks.filter { pkg in it.packageList }.mapNotNull { endsAt(it, now) }.maxOrNull()

    /** Whether the blocker is switched on in Accessibility settings. */
    fun serviceOn(context: Context): Boolean {
        val me = ComponentName(context, BlockService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
