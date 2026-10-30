package com.wac.worktrack.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Finance
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.core.write
import com.wac.worktrack.data.Bank
import com.wac.worktrack.data.Expense
import com.wac.worktrack.data.Loan
import com.wac.worktrack.data.Payable
import com.wac.worktrack.data.PersonDebt
import com.wac.worktrack.data.Saving
import com.wac.worktrack.data.Settings
import com.wac.worktrack.ui.AmountText
import com.wac.worktrack.ui.AnimatedAmount
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.Avatar
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.ChipPicker
import com.wac.worktrack.ui.ConfirmDialog
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.DonutChart
import com.wac.worktrack.ui.EmojiBubble
import com.wac.worktrack.ui.EmptyState
import com.wac.worktrack.ui.FieldLabel
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.GradientBar
import com.wac.worktrack.ui.GradientButton
import com.wac.worktrack.ui.GradientFab
import com.wac.worktrack.ui.Gradients
import com.wac.worktrack.ui.ListRow
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.MoneyInput
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.Palette
import com.wac.worktrack.ui.PickerField
import com.wac.worktrack.ui.PillTabs
import com.wac.worktrack.ui.ProgressRing
import com.wac.worktrack.ui.ScreenTitle
import com.wac.worktrack.ui.SectionHeader
import com.wac.worktrack.ui.SheetForm
import com.wac.worktrack.ui.StatTile
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.Tag
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.Visuals
import com.wac.worktrack.ui.rememberFlow

enum class MoneyTab(val label: String, val add: String, val gradient: List<Color>) {
    Spend("💸 Spend", "Spend", Gradients.Fire),
    Monthly("🗓️ Monthly", "Bill", Gradients.Ocean),
    Banks("🏦 Banks", "Bank", Gradients.Primary),
    Loans("📉 Loans", "Loan", Gradients.Danger),
    People("🤝 Give / Get", "Entry", Gradients.Grape),
    Savings("💰 Savings", "Saving", Gradients.Mint),
}

/** Which add/edit sheet is open; [item] is null when adding. */
private data class Editing(val tab: MoneyTab, val item: Any? = null)

@Composable
fun MoneyScreen(modifier: Modifier, tab: MoneyTab, onTab: (MoneyTab) -> Unit) {
    val db = LocalContext.current.db
    val banks = rememberFlow(emptyList<Bank>()) { db.money().banks() }
    val loans = rememberFlow(emptyList<Loan>()) { db.money().loans() }
    var editing by remember { mutableStateOf<Editing?>(null) }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Money", subtitle = "Every rupee, in one place")
            PillTabs(MoneyTab.entries, tab, { it.label }, onTab)
            val edit: (Any) -> Unit = { editing = Editing(tab, it) }
            // Key per tab so each tab starts scrolled to the top.
            key(tab) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 110.dp)) {
                    when (tab) {
                        MoneyTab.Spend -> SpendTab(banks)
                        MoneyTab.Monthly -> MonthlyTab(banks, loans, edit)
                        MoneyTab.Banks -> BanksTab(banks, edit)
                        MoneyTab.Loans -> LoansTab(loans, edit)
                        MoneyTab.People -> PeopleTab(edit)
                        MoneyTab.Savings -> SavingsTab(edit)
                    }
                }
            }
        }
        GradientFab(tab.add, { editing = Editing(tab) }, Modifier.align(Alignment.BottomEnd).padding(20.dp), tab.gradient)
    }

    editing?.let { e ->
        val close = { editing = null }
        when (e.tab) {
            MoneyTab.Spend -> ExpenseSheet(banks, close)
            MoneyTab.Monthly -> PayableSheet(e.item as Payable?, loans, close)
            MoneyTab.Banks -> BankSheet(e.item as Bank?, close)
            MoneyTab.Loans -> LoanSheet(e.item as Loan?, close)
            MoneyTab.People -> PersonSheet(e.item as PersonDebt?, close)
            MoneyTab.Savings -> SavingSheet(e.item as Saving?, close)
        }
    }
}

/** "Cash / none" plus every bank, for "paid from" / "add to" pickers. */
@Composable
private fun BankPicker(label: String, banks: List<Bank>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (banks.isEmpty()) return
    FieldLabel(label)
    ChipPicker(
        listOf<Bank?>(null) + banks, banks.firstOrNull { it.id == selected }, { it?.name ?: "Cash / none" }, { onSelect(it?.id) },
        emoji = { if (it == null) "💵" else "🏦" },
    )
}

/** Balance-style amount: blank or 0 is allowed, unlike [Money.parse]. */
private fun parseBalance(text: String): Long? =
    if (text.isBlank() || text.trim().toBigDecimalOrNull()?.signum() == 0) 0L else Money.parse(text)

private val Pad = Modifier.fillMaxWidth().padding(horizontal = 16.dp)

// ---------------------------------------------------------------- Spend

@Composable
private fun SpendTab(banks: List<Bank>) {
    val context = LocalContext.current
    val db = context.db
    val today = LocalToday.current
    var monthDay by remember { mutableLongStateOf(today) }
    val range = Day.monthRange(monthDay)
    val expenses = rememberFlow(emptyList<Expense>(), range.first) { db.money().expenses(range.first, range.last) }
    val settings = rememberFlow<Settings?>(null) { db.money().settings() } ?: Settings()
    var editSalary by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf(false) }
    var receiveSalary by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }

    val total = expenses.sumOf { it.amountPaise }
    val limit = settings.monthlyLimitPaise
    AppCard(Pad, gradient = Gradients.Fire) {
        Stepper(Day.monthLabel(monthDay), { monthDay = range.first - 1 }, if (range.last < today) ({ monthDay = range.last + 1 }) else null, tint = Color.White)
        Spacer(Modifier.height(10.dp))
        Text("Spent", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        AnimatedAmount(total, MaterialTheme.typography.displaySmall, Color.White)
        if (limit > 0) {
            Spacer(Modifier.height(10.dp))
            GradientBar(total.toFloat() / limit, gradient = listOf(Color.White, Color.White), track = Color.White.copy(alpha = 0.25f))
            Spacer(Modifier.height(6.dp))
            Text(
                if (total >= limit) "⚠️ ${Money.format(total - limit)} over your ${Money.format(limit)} limit"
                else "${Money.format(limit - total)} left of ${Money.format(limit)}" +
                    if (range.first <= today && today <= range.last) " · ${Money.format(Finance.perDayLeft(limit, total, today))}/day" else "",
                color = Color.White, style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    val received = settings.lastSalaryMonth == Day.monthKey(today)
    Row(Pad.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            "💼", "Salary", settings.salaryPaise, Palette.Mint, Modifier.weight(1f),
            sub = when {
                settings.salaryPaise == 0L -> "Tap to set"
                received -> "Received ✓"
                else -> "On day ${settings.salaryDay}"
            },
            onClick = { editSalary = true },
        )
        StatTile(
            "🎯", "Monthly limit", limit, Palette.Orange, Modifier.weight(1f),
            sub = if (limit == 0L) "Tap to set" else "Day-to-day cap",
            onClick = { editLimit = true },
        )
    }
    if (settings.salaryPaise > 0 && !received) {
        GradientButton("💼 Mark this month's salary received", { receiveSalary = true }, Pad.padding(top = 12.dp), gradient = Gradients.Mint)
    }

    if (expenses.isEmpty()) {
        EmptyState("🧾", "Nothing spent yet", "Tap + Spend to log what you spend. It's quick.")
    } else {
        SectionHeader("Where it went")
        val byCategory = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountPaise } }.entries.sortedByDescending { it.value }
        AppCard(Pad) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(byCategory.map { Visuals.color(it.key) to it.value.toFloat() }, size = 130.dp, stroke = 20.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${byCategory.size}", style = MaterialTheme.typography.titleLarge)
                        Text("categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    byCategory.take(5).forEach { (cat, sum) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Visuals.emoji(cat), fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("${(sum * 100 / total)}%", style = MaterialTheme.typography.labelLarge, color = Visuals.color(cat))
                        }
                    }
                }
            }
        }
        SectionHeader("Transactions")
        expenses.groupBy { it.date }.forEach { (day, list) ->
            Row(Pad.padding(top = 8.dp, bottom = 4.dp)) {
                Text(Day.label(day, today), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(list.sumOf { it.amountPaise }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppCard(Pad, padding = PaddingValues(6.dp)) {
                list.forEach { e ->
                    ListRow(
                        e.note.ifBlank { e.category },
                        sub = listOfNotNull(e.category.takeIf { e.note.isNotBlank() }, banks.firstOrNull { it.id == e.bankId }?.name).joinToString(" · ").ifBlank { null },
                        leading = { EmojiBubble(Visuals.emoji(e.category), Visuals.color(e.category)) },
                        onClick = { deleting = e },
                        trailing = { AmountText(e.amountPaise, prefix = "−") },
                    )
                }
            }
        }
    }

    if (editSalary) {
        var amount by remember { mutableStateOf(if (settings.salaryPaise > 0) Money.input(settings.salaryPaise) else "") }
        var day by remember { mutableStateOf(settings.salaryDay.toString()) }
        val dayNum = day.toIntOrNull()
        SheetForm(
            "💼 Salary", canSave = parseBalance(amount) != null && dayNum in 1..31, gradient = Gradients.Mint,
            onDismiss = { editSalary = false },
            onSave = { context.write { money().saveSettings(settings.copy(salaryPaise = parseBalance(amount)!!, salaryDay = dayNum!!)) } },
        ) {
            MoneyInput(amount, { amount = it }, "Monthly salary", big = true)
            NumberInput(day, { day = it }, "Pay day of the month (1–31)")
        }
    }
    if (editLimit) {
        var amount by remember { mutableStateOf(if (limit > 0) Money.input(limit) else "") }
        SheetForm(
            "🎯 Monthly limit", canSave = parseBalance(amount) != null, onDismiss = { editLimit = false }, gradient = Gradients.Gold,
            onSave = { context.write { money().saveSettings(settings.copy(monthlyLimitPaise = parseBalance(amount)!!)) } },
        ) {
            MoneyInput(amount, { amount = it }, "Spend at most", big = true)
            Text("Leave empty to turn the limit off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (receiveSalary) {
        var bankId by remember { mutableStateOf(banks.firstOrNull()?.id) }
        SheetForm(
            "💼 Salary received", canSave = true, onDismiss = { receiveSalary = false }, saveLabel = "Add salary", gradient = Gradients.Mint,
            onSave = { context.write { money().receiveSalary(settings, bankId, Day.monthKey(today)) } },
        ) {
            Text("${Money.format(settings.salaryPaise)} for ${Day.monthLabel(today)}", style = MaterialTheme.typography.headlineSmall, color = Good)
            BankPicker("Add to", banks, bankId) { bankId = it }
        }
    }
    deleting?.let { e ->
        ConfirmDialog(
            "Delete this spend?",
            "${Money.format(e.amountPaise)} · ${e.category}" + if (e.bankId != null) "\nThe amount goes back to its bank." else "",
            "Delete", onDismiss = { deleting = null },
        ) { context.write { money().deleteExpense(e) } }
    }
}

@Composable
private fun ExpenseSheet(banks: List<Bank>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.expense.first()) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(today) }
    var bankId by remember { mutableStateOf(banks.firstOrNull()?.id) }
    var pickDate by remember { mutableStateOf(false) }
    SheetForm(
        "💸 New spend", canSave = Money.parse(amount) != null, onDismiss = onDismiss, saveLabel = "Add spend", gradient = Gradients.Fire,
        onSave = { context.write { money().addExpense(Expense(0, Money.parse(amount)!!, category, note.trim(), date, bankId)) } },
    ) {
        MoneyInput(amount, { amount = it }, "How much?", big = true)
        FieldLabel("Category")
        ChipPicker(Categories.expense, category, { it }, { category = it }, emoji = Visuals::emoji, color = Visuals::color)
        TextInput(note, { note = it }, "Note (optional)")
        FieldLabel("When")
        ChipPicker(listOf(today, today - 1), date.takeIf { it >= today - 1 }, { if (it == today) "Today" else "Yesterday" }, { date = it ?: today })
        PickerField("Date", Day.long(date), "📅", onClick = { pickDate = true })
        BankPicker("Paid from", banks, bankId) { bankId = it }
    }
    if (pickDate) DatePickerDialogFor(date, { pickDate = false }) { date = minOf(it, today); pickDate = false }
}

// ---------------------------------------------------------------- Monthly payables

@Composable
private fun MonthlyTab(banks: List<Bank>, loans: List<Loan>, edit: (Any) -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    val payables = rememberFlow(emptyList<Payable>()) { context.db.money().payables() }
    var paying by remember { mutableStateOf<Payable?>(null) }
    var undoing by remember { mutableStateOf<Payable?>(null) }

    val total = payables.sumOf { it.amountPaise }
    val paid = payables.filter { Finance.isPaid(it, today) }.sumOf { it.amountPaise }
    AppCard(Pad, gradient = Gradients.Ocean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                if (total > 0) paid.toFloat() / total else 0f, size = 100.dp, stroke = 10.dp,
                gradient = listOf(Color.White, Color(0xFFCFF3FF)), track = Color.White.copy(alpha = 0.22f),
            ) { Text(if (total > 0) "${paid * 100 / total}%" else "—", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Left to pay in ${Day.monthLabel(today).substringBefore(' ')}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                AnimatedAmount(total - paid, MaterialTheme.typography.headlineMedium, Color.White)
                Text("${Money.format(paid)} paid of ${Money.format(total)}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (payables.isEmpty()) {
        EmptyState("🗓️", "No monthly bills yet", "Add EMIs, rent, credit card bills and subscriptions. Tick them off each month.")
    } else {
        SectionHeader("This month")
        Column(Pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            payables.forEach { p ->
                val isPaid = Finance.isPaid(p, today)
                val loan = loans.firstOrNull { it.id == p.loanId }
                AppCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                    ListRow(
                        p.title,
                        sub = listOfNotNull(Money.format(p.amountPaise), "day ${p.dueDay}", loan?.let { "🔗 ${it.name}" }).joinToString(" · "),
                        leading = { EmojiBubble(Visuals.emoji(p.kind), Visuals.color(p.kind)) },
                        onClick = { edit(p) },
                        trailing = {
                            if (isPaid) {
                                Box(Modifier.padding(end = 4.dp)) {
                                    GradientButton("Paid ✓", { undoing = p }, gradient = Gradients.Mint, small = true)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.End) {
                                    DueTag(Day.inMonth(today, p.dueDay), today)
                                    Spacer(Modifier.height(6.dp))
                                    GradientButton("Pay", { paying = p }, gradient = Gradients.Ocean, small = true)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    paying?.let { p ->
        var bankId by remember(p) { mutableStateOf(banks.firstOrNull()?.id) }
        SheetForm(
            "${Visuals.emoji(p.kind)} Pay ${p.title}", canSave = true, onDismiss = { paying = null }, saveLabel = "Mark paid", gradient = Gradients.Ocean,
            onSave = { context.write { money().payPayable(p, bankId, Day.monthKey(today)) } },
        ) {
            Text(Money.format(p.amountPaise), style = MaterialTheme.typography.headlineMedium)
            if (p.loanId != null) Text("This also lowers what's left on the loan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BankPicker("Paid from", banks, bankId) { bankId = it }
        }
    }
    undoing?.let { p ->
        ConfirmDialog(
            "Mark ${p.title} unpaid?", "Any bank or loan change made when paying is reversed.", "Mark unpaid",
            onDismiss = { undoing = null },
        ) { context.write { money().undoPayable(p) } }
    }
}

@Composable
private fun PayableSheet(existing: Payable?, loans: List<Loan>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: Categories.payables.first()) }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    var day by remember { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var loanId by remember { mutableStateOf(existing?.loanId) }
    val dayNum = day.toIntOrNull()
    SheetForm(
        if (existing == null) "🗓️ Monthly bill" else "Edit bill",
        canSave = title.isNotBlank() && Money.parse(amount) != null && dayNum in 1..31,
        onDismiss = onDismiss, gradient = Gradients.Ocean,
        onSave = {
            val p = (existing ?: Payable(title = "", kind = "", amountPaise = 0, dueDay = 1, loanId = null))
                .copy(title = title.trim(), kind = kind, amountPaise = Money.parse(amount)!!, dueDay = dayNum!!, loanId = loanId)
            context.write { money().savePayable(p) }
        },
        onDelete = existing?.let { { context.write { money().deletePayable(it) } } },
    ) {
        FieldLabel("Type")
        ChipPicker(Categories.payables, kind, { it }, { kind = it }, emoji = Visuals::emoji, color = Visuals::color)
        TextInput(title, { title = it }, "Name (e.g. House rent)")
        MoneyInput(amount, { amount = it }, "Amount every month", big = true)
        NumberInput(day, { day = it }, "Due day of month (1–31)")
        if (loans.isNotEmpty()) {
            FieldLabel("Linked loan (paying lowers it)")
            ChipPicker(listOf<Loan?>(null) + loans, loans.firstOrNull { it.id == loanId }, { it?.name ?: "None" }, { loanId = it?.id })
        }
    }
}

// ---------------------------------------------------------------- Banks

@Composable
private fun BanksTab(banks: List<Bank>, edit: (Any) -> Unit) {
    AppCard(Pad) {
        Text("Total across ${banks.size} account${if (banks.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedAmount(banks.sumOf { it.balancePaise }, MaterialTheme.typography.displaySmall)
    }
    if (banks.isEmpty()) EmptyState("🏦", "No banks yet", "Add your accounts and balances. Spends, salary and bill payments update them for you.")
    Column(Pad.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        banks.forEachIndexed { i, b ->
            // Styled like a debit card.
            AppCard(Modifier.fillMaxWidth().height(150.dp), gradient = Gradients.cards[i % Gradients.cards.size], onClick = { edit(b) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("🏦", fontSize = 26.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("Balance", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                AnimatedAmount(b.balancePaise, MaterialTheme.typography.headlineMedium, Color.White)
            }
        }
    }
}

@Composable
private fun BankSheet(existing: Bank?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var balance by remember { mutableStateOf(existing?.let { Money.input(it.balancePaise) } ?: "") }
    SheetForm(
        if (existing == null) "🏦 Bank account" else "Edit bank",
        canSave = name.isNotBlank() && parseBalance(balance) != null, onDismiss = onDismiss,
        onSave = { context.write { money().saveBank(Bank(existing?.id ?: 0, name.trim(), parseBalance(balance)!!)) } },
        onDelete = existing?.let { { context.write { money().deleteBank(it) } } },
    ) {
        TextInput(name, { name = it }, "Bank name (e.g. HDFC Savings)")
        MoneyInput(balance, { balance = it }, "Current balance", big = true)
    }
}

// ---------------------------------------------------------------- Loans

@Composable
private fun LoansTab(loans: List<Loan>, edit: (Any) -> Unit) {
    AppCard(Pad, gradient = Gradients.Danger) {
        Text("Still to repay", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        AnimatedAmount(loans.sumOf { it.outstandingPaise }, MaterialTheme.typography.displaySmall, Color.White)
        Text("EMIs: ${Money.format(loans.sumOf { it.emiPaise })} a month", color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
    if (loans.isEmpty()) EmptyState("📉", "No loans", "Add a loan and its EMI. Paying the EMI under Monthly lowers what's left.")
    Column(Pad.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        loans.forEach { l ->
            val repaid = (l.totalPaise - l.outstandingPaise).toFloat() / l.totalPaise.coerceAtLeast(1)
            AppCard(Modifier.fillMaxWidth(), onClick = { edit(l) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBubble("🏦", Palette.Red)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.titleMedium)
                        Text("of ${Money.format(l.totalPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AmountText(l.outstandingPaise, Bad)
                }
                Spacer(Modifier.height(12.dp))
                GradientBar(repaid, gradient = Gradients.Mint)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("${(repaid * 100).toInt()}% repaid", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Good)
                    if (l.emiPaise > 0) Tag("EMI ${Money.format(l.emiPaise)}", Palette.Violet)
                }
                if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoanSheet(existing: Loan?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var total by remember { mutableStateOf(existing?.let { Money.input(it.totalPaise) } ?: "") }
    var outstanding by remember { mutableStateOf(existing?.let { Money.input(it.outstandingPaise) } ?: "") }
    var emi by remember { mutableStateOf(existing?.emiPaise?.takeIf { it > 0 }?.let(Money::input) ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var addPayable by remember { mutableStateOf(existing == null) }
    var dueDay by remember { mutableStateOf("") }
    val totalP = Money.parse(total)
    // Outstanding defaults to the full amount; 0 means paid off.
    val outstandingP = if (outstanding.isBlank()) totalP else parseBalance(outstanding)
    val emiP = if (emi.isBlank()) 0L else Money.parse(emi)
    val wantsPayable = existing == null && addPayable && emiP != null && emiP > 0
    SheetForm(
        if (existing == null) "📉 New loan" else "Edit loan",
        canSave = name.isNotBlank() && totalP != null && outstandingP != null && outstandingP <= totalP && emiP != null &&
            (!wantsPayable || dueDay.toIntOrNull() in 1..31),
        onDismiss = onDismiss, gradient = Gradients.Danger,
        onSave = {
            context.write {
                val loan = Loan(existing?.id ?: 0, name.trim(), totalP!!, outstandingP!!, emiP!!, note.trim())
                val id = money().saveLoan(loan)
                if (wantsPayable) {
                    money().savePayable(Payable(title = "${loan.name} EMI", kind = "EMI", amountPaise = emiP!!, dueDay = dueDay.toInt(), loanId = id))
                }
            }
        },
        onDelete = existing?.let { { context.write { money().deleteLoan(it) } } },
    ) {
        TextInput(name, { name = it }, "Loan (e.g. HDFC car loan)")
        MoneyInput(total, { total = it }, "Loan amount", big = true)
        MoneyInput(outstanding, { outstanding = it }, "Still to repay", optional = existing == null)
        if (outstandingP != null && totalP != null && outstandingP > totalP) {
            Text("Can't be more than the loan amount", color = Bad, style = MaterialTheme.typography.bodySmall)
        }
        MoneyInput(emi, { emi = it }, "EMI", optional = true)
        TextInput(note, { note = it }, "Note (optional)")
        if (existing == null && emiP != null && emiP > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(addPayable, { addPayable = it })
                Text("Add EMI to monthly bills")
            }
            if (addPayable) NumberInput(dueDay, { dueDay = it }, "EMI due day (1–31)")
        }
    }
}

// ---------------------------------------------------------------- People (to give / to get)

@Composable
private fun PeopleTab(edit: (Any) -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    val people = rememberFlow(emptyList<PersonDebt>()) { context.db.money().personDebts() }
    var paying by remember { mutableStateOf<PersonDebt?>(null) }

    val open = people.filterNot { it.settled }
    Row(Pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("📤", "I have to give", open.filter { it.toGive }.sumOf { it.remainingPaise }, Bad, Modifier.weight(1f), valueColor = Bad)
        StatTile("📥", "I have to get", open.filterNot { it.toGive }.sumOf { it.remainingPaise }, Good, Modifier.weight(1f), valueColor = Good)
    }
    if (people.isEmpty()) EmptyState("🤝", "All square", "Track money you owe friends and family, and money they owe you.")

    @Composable
    fun Section(title: String, list: List<PersonDebt>) {
        if (list.isEmpty()) return
        SectionHeader(title)
        AppCard(Pad, padding = PaddingValues(6.dp)) {
            list.forEach { d ->
                ListRow(
                    d.person,
                    sub = listOfNotNull(
                        if (d.paidPaise > 0) "${Money.format(d.paidPaise)} of ${Money.format(d.amountPaise)} settled" else null,
                        d.dueDate?.let { if (it < today) "⚠️ was due ${Day.short(it)}" else "due ${Day.label(it, today)}" },
                        d.note.ifBlank { null },
                    ).joinToString(" · ").ifBlank { null },
                    leading = { Avatar(d.person) },
                    onClick = { edit(d) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(d.remainingPaise, if (d.toGive) Bad else Good)
                            Spacer(Modifier.height(4.dp))
                            GradientButton(
                                if (d.toGive) "Paid" else "Got", { paying = d },
                                gradient = if (d.toGive) Gradients.Danger else Gradients.Mint, small = true,
                            )
                        }
                    },
                )
            }
        }
    }
    Section("📤 To give", open.filter { it.toGive })
    Section("📥 To get", open.filterNot { it.toGive })
    val settled = people.filter { it.settled }
    if (settled.isNotEmpty()) {
        SectionHeader("✅ Settled")
        AppCard(Pad, padding = PaddingValues(6.dp)) {
            settled.forEach { d ->
                ListRow(
                    d.person, sub = if (d.toGive) "Gave back" else "Got back", leading = { Avatar(d.person) }, onClick = { edit(d) },
                    trailing = { AmountText(d.amountPaise, MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }

    paying?.let { d ->
        var amount by remember(d) { mutableStateOf(Money.input(d.remainingPaise)) }
        val p = Money.parse(amount)
        SheetForm(
            if (d.toGive) "📤 Paid ${d.person}" else "📥 Got from ${d.person}",
            canSave = p != null && p <= d.remainingPaise, onDismiss = { paying = null },
            gradient = if (d.toGive) Gradients.Danger else Gradients.Mint,
            onSave = { context.write { money().savePersonDebt(d.copy(paidPaise = d.paidPaise + p!!)) } },
        ) {
            Text("${Money.format(d.remainingPaise)} left", color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyInput(amount, { amount = it }, "Amount", big = true)
            if (p != null && p > d.remainingPaise) Text("More than what's left", color = Bad, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PersonSheet(existing: PersonDebt?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var person by remember { mutableStateOf(existing?.person ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    var toGive by remember { mutableStateOf(existing?.toGive ?: true) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    SheetForm(
        if (existing == null) "🤝 Give / get" else "Edit",
        canSave = person.isNotBlank() && Money.parse(amount) != null, onDismiss = onDismiss, gradient = Gradients.Grape,
        onSave = {
            val d = (existing ?: PersonDebt(person = "", amountPaise = 0, toGive = true, dueDate = null, note = ""))
                .copy(person = person.trim(), amountPaise = Money.parse(amount)!!, toGive = toGive, dueDate = due, note = note.trim())
            context.write { money().savePersonDebt(d) }
        },
        onDelete = existing?.let { { context.write { money().deletePersonDebt(it) } } },
    ) {
        ChipPicker(
            listOf(true, false), toGive, { if (it) "I have to give" else "I have to get" }, { toGive = it },
            emoji = { if (it) "📤" else "📥" }, color = { if (it) Bad else Good },
        )
        TextInput(person, { person = it }, "Person")
        MoneyInput(amount, { amount = it }, "Amount", big = true)
        PickerField(
            "Due date", due?.let { Day.long(it) } ?: "No due date", "📅", onClick = { pickDate = true },
            trailing = if (due != null) ({ androidx.compose.material3.TextButton(onClick = { due = null }) { Text("Clear") } }) else null,
        )
        TextInput(note, { note = it }, "Note (optional)")
    }
    if (pickDate) DatePickerDialogFor(due ?: today, { pickDate = false }) { due = it; pickDate = false }
}

// ---------------------------------------------------------------- Savings

@Composable
private fun SavingsTab(edit: (Any) -> Unit) {
    val context = LocalContext.current
    val savings = rememberFlow(emptyList<Saving>()) { context.db.money().savings() }
    val total = savings.sumOf { it.amountPaise }
    AppCard(Pad, gradient = Gradients.Mint) {
        Text("Total saved", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        AnimatedAmount(total, MaterialTheme.typography.displaySmall, Color.White)
        Text("across ${savings.size} pot${if (savings.size == 1) "" else "s"} 🌱", color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
    if (savings.isEmpty()) EmptyState("💰", "Start saving", "Add FDs, RDs, an emergency fund, mutual funds, gold… anything you've put away.")
    Column(Pad.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val colors = listOf(Palette.Mint, Palette.Sky, Palette.Amber, Palette.Violet, Palette.Pink)
        savings.forEachIndexed { i, s ->
            val c = colors[i % colors.size]
            AppCard(Modifier.fillMaxWidth(), onClick = { edit(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBubble("💰", c)
                    Spacer(Modifier.width(12.dp))
                    Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    AmountText(s.amountPaise, c)
                }
                Spacer(Modifier.height(10.dp))
                GradientBar(if (total > 0) s.amountPaise.toFloat() / total else 0f, gradient = listOf(c, c.copy(alpha = 0.6f)), height = 8.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${if (total > 0) s.amountPaise * 100 / total else 0}% of your savings",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SavingSheet(existing: Saving?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    SheetForm(
        if (existing == null) "💰 New saving" else "Edit saving",
        canSave = name.isNotBlank() && parseBalance(amount) != null, onDismiss = onDismiss, gradient = Gradients.Mint,
        onSave = { context.write { money().saveSaving(Saving(existing?.id ?: 0, name.trim(), parseBalance(amount)!!)) } },
        onDelete = existing?.let { { context.write { money().deleteSaving(it) } } },
    ) {
        TextInput(name, { name = it }, "Name (e.g. Emergency fund)")
        MoneyInput(amount, { amount = it }, "Amount saved", big = true)
    }
}
