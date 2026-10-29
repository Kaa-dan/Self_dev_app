package com.wac.worktrack.core

import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreaksTest {
    private val today = 20_000L
    private fun habit(id: Long, created: Long = today - 100) = Habit(id, "h$id", null, null, created)

    @Test
    fun habitStreakIgnoresUnfinishedToday() {
        assertEquals(3, Streaks.habitStreak(setOf(today - 1, today - 2, today - 3), today))
        assertEquals(4, Streaks.habitStreak(setOf(today, today - 1, today - 2, today - 3), today))
        assertEquals(0, Streaks.habitStreak(setOf(today - 2), today))
    }

    @Test
    fun dayStreakNeedsEveryHabit() {
        val habits = listOf(habit(1), habit(2))
        val logs = listOf(
            HabitLog(1, today - 1), HabitLog(2, today - 1),
            HabitLog(1, today - 2), HabitLog(2, today - 2),
            HabitLog(1, today - 3), // habit 2 missed: streak stops here
        )
        assertEquals(2, Streaks.dayStreak(habits, logs, today))
        assertEquals(3, Streaks.dayStreak(habits, logs + HabitLog(1, today) + HabitLog(2, today), today))
    }

    @Test
    fun newHabitDoesNotBreakPastDays() {
        val habits = listOf(habit(1), habit(2, created = today))
        val logs = listOf(HabitLog(1, today - 1), HabitLog(1, today - 2))
        assertEquals(2, Streaks.dayStreak(habits, logs, today))
        assertEquals(2, Streaks.bestDayStreak(habits, logs, today))
    }

    @Test
    fun badges() {
        assertNull(Streaks.badgeFor(2))
        assertEquals("One week", Streaks.badgeFor(9)?.name)
        assertEquals(14, Streaks.nextBadge(9)?.days)
    }
}
