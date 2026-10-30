package com.wac.worktrack.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.wac.worktrack.ui.AnimatedAmount
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Confetti
import com.wac.worktrack.ui.EmojiBubble
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.GradientBar
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.ListRow
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SectionHeader
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.Visuals
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.bounceClick
import com.wac.worktrack.ui.rememberFlow
import java.time.LocalTime

@Composable
fun DashboardScreen(
    modifier: Modifier,
    openMoney: (MoneyTab) -> Unit,
    openHabits: () -> Unit,
    openGoals: () -> Unit,
) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    val weekStart = Day.weekStart(today)
    val month = Day.monthRange(today)
    val from = minOf(weekStart, month.first)

    val habitsOrNull = rememberFlow<List<Habit>?>(null) { db.habits().habits() }
    val logsOrNull = rememberFlow<List<HabitLog>?>(null) { db.habits().logs() }
    val habits = habitsOrNull.orEmpty()
    val logs = logsOrNull.orEmpty()
    val settings = rememberFlow<Settings?>(null) { db.money().settings() } ?: Settings()
    val banks = rememberFlow(emptyList()) { db.money().banks() }
    val expenses = rememberFlow(emptyList(), from, today) { db.money().expenses(from, today) }
    val loans = rememberFlow(emptyList()) { db.money().loans() }
    val people = rememberFlow(emptyList()) { db.money().personDebts() }
    val payables = rememberFlow(emptyList()) { db.money().payables() }
    val savings = rememberFlow(emptyList()) { db.money().savings() }
    val goals = rememberFlow(emptyList<Goal>()) { db.goals().goals() }

    val doneToday = logs.filter { it.date == today }.map { it.habitId }.toSet()
    val allDone = habits.isNotEmpty() && habits.all { it.id in doneToday }
    val celebrate = rememberCelebration(allDone, habitsOrNull != null && logsOrNull != null)

    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ScreenTitle(
                when (LocalTime.now().hour) {
                    in 5..11 -> "Good morning ☀️"
                    in 12..16 -> "Good afternoon 👋"
                    else -> "Good evening 🌙"
                },
                subtitle = Day.long(today),
            )

            BalanceHero(
                balance = banks.sumOf { it.balancePaise },
                savings = savings.sumOf { it.amountPaise },
                debt = Finance.totalDebt(loans, people),
                toGet = Finance.toReceive(people),
                settings = settings,
                today = today,
                openMoney = openMoney,
            )

            StreakCard(habits, logs, today, openHabits)

            if (habits.isNotEmpty()) {
                SectionHeader("Today's habits", action = "${habits.count { it.id in doneToday }}/${habits.size}", onAction = openHabits)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    habits.forEach { h ->
                        val done = h.id in doneToday
                        AppCard(
                            Modifier.width(132.dp),
                            gradient = if (done) Gradients.Mint else null,
                            onClick = { toggleHabit(context, h.id, today, !done) },
                            padding = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.emoji, fontSize = 26.sp, modifier = Modifier.weight(1f))
                                if (done) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                h.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (done) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (done) "Done!" else "Tap to finish", style = MaterialTheme.typography.bodySmall,
                                color = if (done) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

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
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padding = PaddingValues(8.dp)) {
                if (due.isEmpty()) {
                    EmptyState(
                        if (payables.isEmpty()) "🗓️" else "🎉",
                        if (payables.isEmpty()) "No monthly payments yet" else "All paid this month",
                        if (payables.isEmpty()) "Add EMIs, rent and card bills in Money › Monthly." else "Nothing left to pay. Nice!",
                    )
                } else {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Left to pay", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedAmount(due.sumOf { it.amountPaise }, MaterialTheme.typography.titleLarge, Bad)
                    }
                    due.take(4).forEach { p ->
                        ListRow(
                            p.title, sub = Money.format(p.amountPaise),
                            leading = { EmojiBubble(Visuals.emoji(p.kind), Visuals.color(p.kind)) },
                            onClick = { openMoney(MoneyTab.Monthly) },
                            trailing = { DueTag(Day.inMonth(today, p.dueDay), today) },
                        )
                    }
                }
            }

            SectionHeader("Goals", action = "All", onAction = openGoals)
            val active = goals.filter { it.doneDay == null }
            if (active.isEmpty()) {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = openGoals) {
                    EmptyState("🎯", "Set your first goal", "Pick something you want done and a date to finish it by.")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) { active.forEach { GoalMiniCard(it, today, openGoals) } }
            }
        }
        Confetti(celebrate)
    }
}

@Composable
fun DueTag(dueDay: Long, today: Long) {
    val left = dueDay - today
    when {
        left < 0 -> Tag("Overdue", Bad)
        left == 0L -> Tag("Today", Warn)
        left <= 5 -> Tag("In $left d", Warn)
        else -> Tag(Day.short(dueDay), Palette.Slate)
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
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), gradient = Gradients.Primary, onClick = { openMoney(MoneyTab.Banks) }) {
        Text("Total bank balance", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        AnimatedAmount(balance, MaterialTheme.typography.displaySmall, Color.White)
        if (settings.salaryPaise > 0) {
            val received = settings.lastSalaryMonth == Day.monthKey(today)
            Spacer(Modifier.height(6.dp))
            Text(
                if (received) "✓ Salary ${Money.format(settings.salaryPaise)} received"
                else "⏳ Salary due ${Day.short(Day.inMonth(today, settings.salaryDay))}",
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 5.dp),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip("💰", "Savings", savings, Modifier.weight(1f)) { openMoney(MoneyTab.Savings) }
            HeroChip("💸", "Debt", debt, Modifier.weight(1f)) { openMoney(MoneyTab.Loans) }
            HeroChip("🤝", "To get", toGet, Modifier.weight(1f)) { openMoney(MoneyTab.People) }
        }
    }
}

@Composable
private fun HeroChip(emoji: String, label: String, paise: Long, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).bounceClick(onClick = onClick).padding(10.dp),
    ) {
        Text("$emoji $label", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        AnimatedAmount(paise, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), Color.White)
    }
}

@Composable
private fun StreakCard(habits: List<Habit>, logs: List<HabitLog>, today: Long, onClick: () -> Unit) {
    val streak = Streaks.dayStreak(habits, logs, today)
    val best = Streaks.bestDayStreak(habits, logs, today)
    val next = Streaks.nextBadge(streak)
    val pulse = rememberInfiniteTransition(label = "flame")
    val flameScale by pulse.animateFloat(
        0.9f, 1.1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameScale",
    )
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), gradient = Gradients.Fire, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 54.sp, modifier = Modifier.scale(if (streak > 0) flameScale else 1f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streak", color = Color.White, style = MaterialTheme.typography.displaySmall)
                    Text(" day streak", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    if (habits.isEmpty()) "Add habits to start your streak" else "Best: $best day${if (best == 1) "" else "s"}",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // Badge shelf: earned ones bright, locked ones faded.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Streaks.badges.forEach { b ->
                val earned = streak >= b.days
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (earned) 0.28f else 0.1f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (earned) b.emoji else "🔒", fontSize = 20.sp)
                    Text("${b.days}d", color = Color.White.copy(alpha = if (earned) 1f else 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        next?.let {
            Spacer(Modifier.height(14.dp))
            GradientBar(streak.toFloat() / it.days, gradient = listOf(Color.White, Color.White), track = Color.White.copy(alpha = 0.25f), height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${it.days - streak} more day${if (it.days - streak == 1) "" else "s"} to unlock ${it.emoji} ${it.name}",
                color = Color.White, style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SpendingCard(spentToday: Long, spentWeek: Long, spentMonth: Long, limit: Long, today: Long, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fraction = if (limit > 0) spentMonth.toFloat() / limit else 0f
            val gradient = when {
                fraction >= 1f -> Gradients.Danger
                fraction >= 0.8f -> Gradients.Gold
                else -> Gradients.Mint
            }
            ProgressRing(fraction, size = 112.dp, stroke = 12.dp, gradient = gradient) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (limit > 0) "${(fraction * 100).toInt()}%" else "—", style = MaterialTheme.typography.titleLarge)
                    Text("of limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniStat("Today", spentToday)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MiniStat("This week", spentWeek)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MiniStat(Day.monthLabel(today).substringBefore(' '), spentMonth)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                limit <= 0 -> "Set a monthly limit in Money › Spend to track your budget"
                spentMonth >= limit -> "⚠️ Over the limit by ${Money.format(spentMonth - limit)}"
                else -> "✨ ${Money.format(limit - spentMonth)} left · ${Money.format(Finance.perDayLeft(limit, spentMonth, today))} a day"
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
        AnimatedAmount(paise, MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GoalMiniCard(goal: Goal, today: Long, onClick: () -> Unit) {
    val left = goal.endDate - today
    AppCard(Modifier.width(160.dp), onClick = onClick, padding = PaddingValues(14.dp)) {
        ProgressRing(
            goalTimeFraction(goal, today), size = 56.dp, stroke = 6.dp,
            gradient = if (left < 0) Gradients.Danger else if (left <= 7) Gradients.Gold else Gradients.Primary,
        ) { Text(goal.emoji, fontSize = 22.sp) }
        Spacer(Modifier.height(10.dp))
        Text(goal.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                left < 0 -> "${-left} days over"
                left == 0L -> "Due today"
                else -> "$left days left"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (left < 0) Bad else if (left <= 7) Warn else Good,
        )
    }
}

/** Share of the time between setting the goal and its end date that has passed. */
fun goalTimeFraction(goal: Goal, today: Long): Float {
    val start = if (goal.startDay in 1..goal.endDate) goal.startDay else goal.endDate - 30
    val span = (goal.endDate - start).coerceAtLeast(1)
    return ((today - start).toFloat() / span).coerceIn(0f, 1f)
}
