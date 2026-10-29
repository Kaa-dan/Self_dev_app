package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Finance
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.Streaks
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.data.Settings
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.ItemRow
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatCard
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.daysLeftLabel
import com.wac.worktrack.ui.rememberFlow

@Composable
fun DashboardScreen(
    modifier: Modifier,
    openMoney: (MoneyTab) -> Unit,
    openHabits: () -> Unit,
    openGoals: () -> Unit,
) {
    val db = LocalContext.current.db
    val today = LocalToday.current
    val weekStart = Day.weekStart(today)
    val month = Day.monthRange(today)
    val from = minOf(weekStart, month.first)

    val habits = rememberFlow(emptyList<Habit>()) { db.habits().habits() }
    val logs = rememberFlow(emptyList<HabitLog>()) { db.habits().logs() }
    val settings = rememberFlow<Settings?>(null) { db.money().settings() } ?: Settings()
    val banks = rememberFlow(emptyList()) { db.money().banks() }
    val expenses = rememberFlow(emptyList(), from, today) { db.money().expenses(from, today) }
    val loans = rememberFlow(emptyList()) { db.money().loans() }
    val people = rememberFlow(emptyList()) { db.money().personDebts() }
    val payables = rememberFlow(emptyList()) { db.money().payables() }
    val savings = rememberFlow(emptyList()) { db.money().savings() }
    val goals = rememberFlow(emptyList<Goal>()) { db.goals().goals() }

    val spentToday = Finance.spent(expenses, today, today)
    val spentWeek = Finance.spent(expenses, weekStart, today)
    val spentMonth = Finance.spent(expenses, month.first, today)
    val limit = settings.monthlyLimitPaise
    val due = Finance.unpaid(payables, today)

    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(Day.long(today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        StreakCard(habits, logs, today, openHabits)

        SectionTitle("Money")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Spent today", Money.format(spentToday), Modifier.weight(1f), onClick = { openMoney(MoneyTab.Spend) })
                StatCard("Spent this week", Money.format(spentWeek), Modifier.weight(1f), onClick = { openMoney(MoneyTab.Spend) })
            }
            MonthLimitCard(spentMonth, limit, today) { openMoney(MoneyTab.Spend) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    "Bank balance", Money.format(banks.sumOf { it.balancePaise }), Modifier.weight(1f),
                    sub = "${banks.size} account${if (banks.size == 1) "" else "s"}", onClick = { openMoney(MoneyTab.Banks) },
                )
                StatCard(
                    "Savings", Money.format(savings.sumOf { it.amountPaise }), Modifier.weight(1f), color = Good,
                    onClick = { openMoney(MoneyTab.Savings) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    "Total debt", Money.format(Finance.totalDebt(loans, people)), Modifier.weight(1f), color = Bad,
                    sub = "loans + to give", onClick = { openMoney(MoneyTab.Loans) },
                )
                StatCard(
                    "To receive", Money.format(Finance.toReceive(people)), Modifier.weight(1f),
                    onClick = { openMoney(MoneyTab.People) },
                )
            }
            if (settings.salaryPaise > 0) {
                val received = settings.lastSalaryMonth == Day.monthKey(today)
                StatCard(
                    "Salary", Money.format(settings.salaryPaise), Modifier.fillMaxWidth(),
                    color = if (received) Good else MaterialTheme.colorScheme.onSurface,
                    sub = if (received) "Received this month" else "Due on ${Day.short(Day.inMonth(today, settings.salaryDay))} · tap to mark received",
                    onClick = { openMoney(MoneyTab.Spend) },
                )
            }
        }

        SectionTitle("Due this month", trailing = {
            TextButton(onClick = { openMoney(MoneyTab.Monthly) }) { Text("All") }
        })
        if (due.isEmpty()) {
            Hint(if (payables.isEmpty()) "Add EMIs, rent and card bills under Money › Monthly." else "Everything's paid this month. 🎉")
        } else {
            Text(
                "${Money.format(due.sumOf { it.amountPaise })} left to pay",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            due.forEach { p ->
                val dueDay = Day.inMonth(today, p.dueDay)
                ItemRow(
                    p.title, Money.format(p.amountPaise),
                    sub = "${p.kind} · " + if (dueDay < today) "overdue since ${Day.short(dueDay)}" else "due ${Day.label(dueDay, today)}",
                    valueColor = if (dueDay < today) Bad else MaterialTheme.colorScheme.onSurface,
                    onClick = { openMoney(MoneyTab.Monthly) },
                )
            }
        }

        SectionTitle("Goals", trailing = { TextButton(onClick = openGoals) { Text("All") } })
        val active = goals.filter { it.doneDay == null }
        if (active.isEmpty()) {
            Hint("No goals yet. Add one with an end date under Goals.")
        } else {
            active.take(3).forEach { g ->
                ItemRow(
                    g.title, "", sub = daysLeftLabel(g.endDate, today),
                    onClick = openGoals,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(habits: List<Habit>, logs: List<HabitLog>, today: Long, onClick: () -> Unit) {
    val streak = Streaks.dayStreak(habits, logs, today)
    val best = Streaks.bestDayStreak(habits, logs, today)
    val badge = Streaks.badgeFor(streak)
    val next = Streaks.nextBadge(streak)
    val doneToday = logs.count { it.date == today }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(badge?.emoji ?: "🔥", fontSize = 44.sp)
            Spacer(Modifier.padding(start = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$streak-day streak", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    when {
                        habits.isEmpty() -> "Add habits to start a streak"
                        badge != null -> "${badge.name} badge · best $best days"
                        else -> "Finish every habit each day · best $best days"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (habits.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                next?.let {
                    LinearProgressIndicator(
                        progress = { streak.toFloat() / it.days }, modifier = Modifier.fillMaxWidth().height(6.dp),
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${it.days - streak} more day${if (it.days - streak == 1) "" else "s"} to ${it.emoji} ${it.name}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    "Today: $doneToday of ${habits.size} habits done",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MonthLimitCard(spent: Long, limit: Long, today: Long, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Spent in ${Day.monthLabel(today)}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (limit <= 0) {
                Text(Money.format(spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Set a monthly limit under Money › Spend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val fraction = spent.toFloat() / limit
                val color = when {
                    fraction >= 1f -> Bad
                    fraction >= 0.8f -> Warn
                    else -> Good
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.format(spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                    Text(
                        " of ${Money.format(limit)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) }, color = color,
                    modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (spent >= limit) "Over the limit by ${Money.format(spent - limit)}"
                    else "${Money.format(limit - spent)} left · ${Money.format(Finance.perDayLeft(limit, spent, today))} a day",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
