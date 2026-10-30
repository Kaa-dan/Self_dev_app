package com.wac.worktrack.core

import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog

/** Streak earned by the badge, shortest first. */
data class StreakBadge(val days: Int, val name: String)

object Streaks {
    val badges = listOf(
        StreakBadge(3, "Starter"),
        StreakBadge(7, "One week"),
        StreakBadge(14, "Two weeks"),
        StreakBadge(30, "One month"),
        StreakBadge(60, "Two months"),
        StreakBadge(100, "Centurion"),
        StreakBadge(365, "One year"),
    )

    fun badgeFor(streak: Int): StreakBadge? = badges.lastOrNull { streak >= it.days }

    fun nextBadge(streak: Int): StreakBadge? = badges.firstOrNull { streak < it.days }

    /**
     * Days in a row a habit was done, counting back from today. Today not being done yet doesn't
     * break the streak; it just isn't counted until it is.
     */
    fun habitStreak(doneDays: Set<Long>, today: Long): Int {
        var day = if (today in doneDays) today else today - 1
        var count = 0
        while (day in doneDays) {
            count++
            day--
        }
        return count
    }

    /** Whether every habit that existed on [day] was done that day. A day with no habits isn't. */
    fun allDone(habits: List<Habit>, doneByDay: Map<Long, Set<Long>>, day: Long): Boolean {
        val due = habits.filter { it.createdDay <= day }
        if (due.isEmpty()) return false
        val done = doneByDay[day].orEmpty()
        return due.all { it.id in done }
    }

    /** Days in a row with every habit done: the dashboard streak. */
    fun dayStreak(habits: List<Habit>, logs: List<HabitLog>, today: Long): Int {
        val doneByDay = byDay(logs)
        var day = if (allDone(habits, doneByDay, today)) today else today - 1
        var count = 0
        while (allDone(habits, doneByDay, day)) {
            count++
            day--
        }
        return count
    }

    /** Longest run of all-done days ever. */
    fun bestDayStreak(habits: List<Habit>, logs: List<HabitLog>, today: Long): Int {
        val first = habits.minOfOrNull { it.createdDay } ?: return 0
        val doneByDay = byDay(logs)
        var best = 0
        var run = 0
        for (day in first..today) {
            if (allDone(habits, doneByDay, day)) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    fun byDay(logs: List<HabitLog>): Map<Long, Set<Long>> =
        logs.groupBy({ it.date }, { it.habitId }).mapValues { it.value.toSet() }
}
