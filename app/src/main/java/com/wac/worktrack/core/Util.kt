package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.data.AppDatabase
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

val Context.db: AppDatabase get() = AppDatabase.get(this)

/** Database write on the app scope, so it still finishes when the dialog that started it closes. */
fun Context.write(block: suspend AppDatabase.() -> Unit) {
    (applicationContext as WorkTrackApp).scope.launch { db.block() }
}

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun startMillis(day: Long): Long =
        date(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Wall-clock time on [day], so 9:00 stays 9:00 on days the clocks change. */
    fun millisAt(day: Long, minutes: Int): Long {
        // Minutes past midnight roll over into the next day ("now + 1 hour" at 11:30 PM).
        val m = Math.floorMod(minutes, 24 * 60)
        val d = day + Math.floorDiv(minutes, 24 * 60)
        return date(d).atTime(LocalTime.of(m / 60, m % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun ofMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun minutesOfMillis(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** Monday of the week containing [day]. */
    fun weekStart(day: Long): Long =
        date(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    fun monthRange(day: Long): LongRange {
        val d = date(day)
        return d.withDayOfMonth(1).toEpochDay()..d.withDayOfMonth(d.lengthOfMonth()).toEpochDay()
    }

    // Built once: these run for every row of most lists.
    private val LABEL = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val SHORT = DateTimeFormatter.ofPattern("d MMM")
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun label(day: Long, today: Long = today()): String = when (day - today) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> date(day).format(LABEL)
    }

    fun long(day: Long): String = date(day).format(LONG)

    fun short(day: Long): String = date(day).format(SHORT)

    /** "2026-10": which month a monthly payment or salary belongs to. */
    fun monthKey(day: Long): String = date(day).let { "%04d-%02d".format(it.year, it.monthValue) }

    fun monthLabel(day: Long): String = date(day).format(MONTH)

    /** [dayOfMonth] in the month of [day], clamped so "31st" means the last day of shorter months. */
    fun inMonth(day: Long, dayOfMonth: Int): Long {
        val d = date(day)
        return d.withDayOfMonth(dayOfMonth.coerceIn(1, d.lengthOfMonth())).toEpochDay()
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a")

fun timeLabel(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(TIME)

fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

object Money {
    /** "₹1,25,000" / "₹12.50" — Indian lakh grouping, done by hand so it doesn't depend on the locale data. */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = (abs / 100).toString()
        val grouped = if (rupees.length <= 3) rupees else {
            val head = rupees.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + rupees.takeLast(3)
        }
        val fraction = abs % 100
        return "$sign₹$grouped" + if (fraction == 0L) "" else ".%02d".format(fraction)
    }

    /** "1,250.5" → 125050 paise; null if not a positive number. */
    fun parse(text: String): Long? {
        val n = text.replace(",", "").replace("₹", "").trim().toBigDecimalOrNull() ?: return null
        if (n <= BigDecimal.ZERO) return null
        return n.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun input(paise: Long): String =
        BigDecimal.valueOf(paise).movePointLeft(2).stripTrailingZeros().toPlainString()
}

object Categories {
    val expense = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Family", "Other",
    )
    val payables = listOf("EMI", "Rent", "Credit card", "Insurance", "Subscription", "Utility", "Other")
}
