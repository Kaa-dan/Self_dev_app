package com.wac.worktrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Finance
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.Streaks
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.data.HabitTimer
import com.wac.worktrack.data.Settings
import com.wac.worktrack.ui.AnimatedAmount
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.CircleIconButton
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.Glyphs
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.IconBadge
import com.wac.worktrack.ui.ListRow
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.RowDivider
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SectionHeader
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.Visuals
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.muted
import com.wac.worktrack.ui.rememberFlow
import com.wac.worktrack.ui.tabular
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    modifier: Modifier,
    openMoney: (MoneyTab) -> Unit,
    openHabits: () -> Unit,
) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    val weekStart = Day.weekStart(today)
    val month = Day.monthRange(today)
    val from = minOf(weekStart, month.first)

    val habits = rememberFlow<List<Habit>?>(null) { db.habits().habits() }.orEmpty()
    val logs = rememberFlow<List<HabitLog>?>(null) { db.habits().logs() }.orEmpty()
    val settings = rememberFlow<Settings?>(null) { db.money().settings() } ?: Settings()
    val banks = rememberFlow(emptyList()) { db.money().banks() }
    val expenses = rememberFlow(emptyList(), from, today) { db.money().expenses(from, today) }
    val loans = rememberFlow(emptyList()) { db.money().loans() }
    val people = rememberFlow(emptyList()) { db.money().personDebts() }
    val payables = rememberFlow(emptyList()) { db.money().payables() }
    val savings = rememberFlow(emptyList()) { db.money().savings() }
    val timers = rememberFlow(emptyList<HabitTimer>(), today) { db.habits().timers(today) }

    val doneToday = logs.filter { it.date == today }.map { it.habitId }.toSet()
    // Timed habits started today and not yet finished, running ones first.
    val ongoing = timers
        .filter { (it.runningSince != null || it.accumulatedMillis > 0) && it.habitId !in doneToday }
        .mapNotNull { t -> habits.find { it.id == t.habitId && it.durationMinutes != null }?.let { it to t } }
        .sortedBy { it.second.runningSince == null }

    var showBackup by remember { mutableStateOf(false) }
    if (showBackup) BackupSheet { showBackup = false }

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle(
            when (LocalTime.now().hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            },
            subtitle = Day.long(today),
            trailing = { CircleIconButton(backupIcon(context), { showBackup = true }) },
        )

        if (ongoing.isNotEmpty()) {
            SectionHeader("In progress", action = "Habits", onAction = openHabits)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ongoing.forEach { (h, t) -> OngoingCard(h, t, habitColor(habits.indexOf(h)), openHabits) }
            }
        }

        BalanceHero(
            balance = banks.sumOf { it.balancePaise },
            savings = savings.sumOf { it.amountPaise },
            debt = Finance.totalDebt(loans, people),
            toGet = Finance.toReceive(people),
            settings = settings,
            today = today,
            openMoney = openMoney,
        )

        ConsistencyCard(habits, logs, today, openHabits)

        SectionHeader("Spending", action = "Details", onAction = { openMoney(MoneyTab.Spend) })
        SpendingCard(
            spentToday = Finance.spent(expenses, today, today),
            spentWeek = Finance.spent(expenses, weekStart, today),
            spentMonth = Finance.spent(expenses, month.first, today),
            limit = settings.monthlyLimitPaise,
            today = today,
        ) { openMoney(MoneyTab.Spend) }

        val due = Finance.unpaid(payables, today)
        SectionHeader("Due this month", action = "All", onAction = { openMoney(MoneyTab.Monthly) })
        AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(4.dp)) {
            if (due.isEmpty()) {
                EmptyState(
                    if (payables.isEmpty()) Icons.Rounded.CalendarMonth else Icons.Rounded.Check,
                    if (payables.isEmpty()) "No monthly payments yet" else "All paid this month",
                    if (payables.isEmpty()) "Add EMIs, rent and card bills in Money › Monthly." else "Nothing left to pay this month.",
                )
            } else {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Left to pay", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedAmount(due.sumOf { it.amountPaise }, MaterialTheme.typography.titleLarge, Bad)
                }
                due.take(4).forEach { p ->
                    RowDivider()
                    ListRow(
                        p.title, sub = Money.format(p.amountPaise),
                        leading = { IconBadge(Visuals.icon(p.kind), Visuals.color(p.kind), 36.dp) },
                        onClick = { openMoney(MoneyTab.Monthly) },
                        trailing = { DueTag(Day.inMonth(today, p.dueDay), today) },
                    )
                }
            }
        }
    }
}

/** Habits cycle through the muted palette by position. */
fun habitColor(index: Int) = Palette.cycle[Math.floorMod(index, Palette.cycle.size)]

@Composable
fun DueTag(dueDay: Long, today: Long) {
    val left = dueDay - today
    when {
        left < 0 -> Tag("Overdue", Bad)
        left == 0L -> Tag("Today", Warn)
        left <= 5 -> Tag("In $left d", Warn)
        else -> Tag(Day.short(dueDay), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalanceHero(
    balance: Long,
    savings: Long,
    debt: Long,
    toGet: Long,
    settings: Settings,
    today: Long,
    openMoney: (MoneyTab) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), hero = true, onClick = { openMoney(MoneyTab.Banks) }) {
        Text("Bank balance", color = muted(), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(2.dp))
        AnimatedAmount(balance, MaterialTheme.typography.displaySmall)
        if (settings.salaryPaise > 0) {
            val received = settings.lastSalaryMonth == Day.monthKey(today)
            Spacer(Modifier.height(6.dp))
            Text(
                if (received) "Salary of ${Money.format(settings.salaryPaise)} received"
                else "Salary due ${Day.short(Day.inMonth(today, settings.salaryDay))}",
                color = muted(), style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = muted().copy(alpha = 0.18f))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(IntrinsicSize.Min)) {
            HeroStat("Savings", savings, Modifier.weight(1f)) { openMoney(MoneyTab.Savings) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(muted().copy(alpha = 0.18f)))
            HeroStat("Debt", debt, Modifier.weight(1f).padding(start = 12.dp)) { openMoney(MoneyTab.Loans) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(muted().copy(alpha = 0.18f)))
            HeroStat("To get", toGet, Modifier.weight(1f).padding(start = 12.dp)) { openMoney(MoneyTab.People) }
        }
    }
}

@Composable
private fun HeroStat(label: String, paise: Long, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).bounceClick(onClick = onClick).padding(vertical = 2.dp)) {
        Text(label, color = muted(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        AnimatedAmount(paise, MaterialTheme.typography.titleSmall)
    }
}

/** Streak, best run and a calendar of the last five weeks, shaded by how much got done. */
@Composable
private fun ConsistencyCard(habits: List<Habit>, logs: List<HabitLog>, today: Long, onClick: () -> Unit) {
    val streak = Streaks.dayStreak(habits, logs, today)
    val best = Streaks.bestDayStreak(habits, logs, today)
    val next = Streaks.nextBadge(streak)
    val doneByDay = Streaks.byDay(logs)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest

    SectionHeader("Consistency")
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$streak", style = MaterialTheme.typography.displaySmall)
            Text(
                if (streak == 1) " day in a row" else " days in a row",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp).weight(1f),
            )
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("Best", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$best", style = MaterialTheme.typography.titleMedium.tabular())
            }
        }
        Text(
            when {
                habits.isEmpty() -> "Add a habit to start counting."
                next != null -> "${next.days - streak} more to reach ${next.name.lowercase()} (${next.days} days)."
                else -> "A full year. Keep the chain going."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        val start = Day.weekStart(today) - 28
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0..6) {
                Text(
                    Day.date(start + i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (week in 0..4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (d in 0..6) {
                        val day = start + week * 7 + d
                        val due = habits.filter { it.createdDay <= day }
                        val fraction = if (due.isEmpty()) 0f else due.count { it.id in doneByDay[day].orEmpty() }.toFloat() / due.size
                        val shape = RoundedCornerShape(4.dp)
                        Box(
                            Modifier.weight(1f).aspectRatio(2.2f).clip(shape)
                                .then(
                                    when {
                                        day > today -> Modifier
                                        fraction > 0f -> Modifier.background(primary.copy(alpha = 0.25f + 0.75f * fraction))
                                        else -> Modifier.background(empty)
                                    },
                                )
                                .then(if (day == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingCard(spentToday: Long, spentWeek: Long, spentMonth: Long, limit: Long, today: Long, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fraction = if (limit > 0) spentMonth.toFloat() / limit else 0f
            val color = when {
                fraction >= 1f -> Bad
                fraction >= 0.8f -> Warn
                else -> Good
            }
            ProgressRing(fraction, size = 96.dp, stroke = 7.dp, color = color) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (limit > 0) "${(fraction * 100).toInt()}%" else "—", style = MaterialTheme.typography.titleLarge.tabular())
                    Text("of limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Today", spentToday)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MiniStat("This week", spentWeek)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MiniStat(Day.monthLabel(today).substringBefore(' '), spentMonth)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                limit <= 0 -> "Set a monthly limit in Money › Spend to track your budget."
                spentMonth >= limit -> "Over the limit by ${Money.format(spentMonth - limit)}."
                else -> "${Money.format(limit - spentMonth)} left · ${Money.format(Finance.perDayLeft(limit, spentMonth, today))} a day"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (limit in 1..spentMonth) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniStat(label: String, paise: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedAmount(paise, MaterialTheme.typography.titleSmall)
    }
}

/** Share of the time between setting the goal and its end date that has passed. */
fun goalTimeFraction(goal: Goal, today: Long): Float {
    val start = if (goal.startDay in 1..goal.endDate) goal.startDay else goal.endDate - 30
    val span = (goal.endDate - start).coerceAtLeast(1)
    return ((today - start).toFloat() / span).coerceIn(0f, 1f)
}

/** A started habit timer: live time against the goal, with pause / resume. */
@Composable
private fun OngoingCard(habit: Habit, timer: HabitTimer, color: Color, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Glyphs.icon(habit.emoji), color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), tint = if (timer.runningSince != null) color else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (timer.runningSince != null) "Running" else "Paused", style = MaterialTheme.typography.bodySmall,
                        color = if (timer.runningSince != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TimerRow(habit, habit.durationMinutes ?: return@AppCard, timer, color, done = false)
    }
}
