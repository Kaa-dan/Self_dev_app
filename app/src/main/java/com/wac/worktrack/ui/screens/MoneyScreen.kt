package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.ConfirmDialog
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.FormDialog
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.ItemRow
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.MoneyInput
import com.wac.worktrack.ui.NumberInput
import com.wac.worktrack.ui.ProgressRow
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.TextInput
import com.wac.worktrack.ui.rememberFlow

enum class MoneyTab(val label: String, val add: String) {
    Spend("Spend", "Spend"),
    Monthly("Monthly", "Payable"),
    Banks("Banks", "Bank"),
    Loans("Loans", "Loan"),
    People("To give / get", "Entry"),
    Savings("Savings", "Saving"),
}

/** Which add/edit dialog is open; [item] is null when adding. */
private data class Editing(val tab: MoneyTab, val item: Any? = null)

@Composable
fun MoneyScreen(modifier: Modifier, tab: MoneyTab, onTab: (MoneyTab) -> Unit) {
    val db = LocalContext.current.db
    val banks = rememberFlow(emptyList<Bank>()) { db.money().banks() }
    val loans = rememberFlow(emptyList<Loan>()) { db.money().loans() }
    var editing by remember { mutableStateOf<Editing?>(null) }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                MoneyTab.entries.forEach {
                    Tab(selected = it == tab, onClick = { onTab(it) }, text = { Text(it.label) })
                }
            }
            val edit: (Any) -> Unit = { editing = Editing(tab, it) }
            // Key per tab so each tab starts scrolled to the top.
            key(tab) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
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
        ExtendedFloatingActionButton(
            onClick = { editing = Editing(tab) },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(tab.add) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { e ->
        val close = { editing = null }
        when (e.tab) {
            MoneyTab.Spend -> ExpenseDialog(banks, close)
            MoneyTab.Monthly -> PayableDialog(e.item as Payable?, loans, close)
            MoneyTab.Banks -> BankDialog(e.item as Bank?, close)
            MoneyTab.Loans -> LoanDialog(e.item as Loan?, close)
            MoneyTab.People -> PersonDialog(e.item as PersonDebt?, close)
            MoneyTab.Savings -> SavingDialog(e.item as Saving?, close)
        }
    }
}

/** "None" plus every bank, for "paid from" / "add to" pickers. */
@Composable
private fun BankChoice(label: String, banks: List<Bank>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (banks.isEmpty()) return
    Choice(
        label, listOf<Bank?>(null) + banks, banks.firstOrNull { it.id == selected }, { onSelect(it?.id) },
        Modifier.fillMaxWidth(), display = { it?.name ?: "None" },
    )
}

/** Balance-style amount: blank or 0 is allowed, unlike [Money.parse]. */
private fun parseBalance(text: String): Long? =
    if (text.isBlank() || text.trim().toBigDecimalOrNull()?.signum() == 0) 0L else Money.parse(text)

// ---------------------------------------------------------------- Spend

@Composable
private fun SpendTab(banks: List<Bank>) {
    val db = LocalContext.current.db
    val context = LocalContext.current
    val today = LocalToday.current
    var monthDay by remember { mutableLongStateOf(today) }
    val range = Day.monthRange(monthDay)
    val expenses = rememberFlow(emptyList<Expense>(), range.first) { db.money().expenses(range.first, range.last) }
    val settings = rememberFlow<Settings?>(null) { db.money().settings() } ?: Settings()
    var editSalary by remember { mutableStateOf(false) }
    var editLimit by remember { mutableStateOf(false) }
    var receiveSalary by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }

    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val received = settings.lastSalaryMonth == Day.monthKey(today)
            ItemRow(
                "Salary", if (settings.salaryPaise > 0) Money.format(settings.salaryPaise) else "Not set",
                sub = if (settings.salaryPaise > 0) "On day ${settings.salaryDay} of the month" + if (received) " · received ✓" else "" else "Tap to set",
                onClick = { editSalary = true },
            )
            if (settings.salaryPaise > 0 && !received) {
                FilledTonalButton(onClick = { receiveSalary = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Mark this month's salary received")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ItemRow(
                "Monthly limit", if (settings.monthlyLimitPaise > 0) Money.format(settings.monthlyLimitPaise) else "Not set",
                sub = "Cap on day-to-day spending", onClick = { editLimit = true },
            )
        }
    }

    Stepper(Day.monthLabel(monthDay), { monthDay = range.first - 1 }, if (range.last < today) ({ monthDay = range.last + 1 }) else null)
    val total = expenses.sumOf { it.amountPaise }
    Text(
        "Spent ${Money.format(total)}" + if (settings.monthlyLimitPaise > 0) " of ${Money.format(settings.monthlyLimitPaise)}" else "",
        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium,
        color = if (settings.monthlyLimitPaise in 1..total) Bad else MaterialTheme.colorScheme.onSurface,
    )
    if (expenses.isNotEmpty()) {
        SectionTitle("By category")
        expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            .entries.sortedByDescending { it.value }.forEach { (cat, sum) ->
                ProgressRow(cat, Money.format(sum), sum.toFloat() / total)
            }
    }
    if (expenses.isEmpty()) Hint("Nothing spent this month yet. Tap + Spend to add.")
    expenses.groupBy { it.date }.forEach { (day, list) ->
        SectionTitle("${Day.label(day, today)} · ${Money.format(list.sumOf { it.amountPaise })}")
        list.forEach { e ->
            ItemRow(
                e.note.ifBlank { e.category }, Money.format(e.amountPaise),
                sub = listOfNotNull(e.category.takeIf { e.note.isNotBlank() }, banks.firstOrNull { it.id == e.bankId }?.name)
                    .joinToString(" · ").ifBlank { null },
                onClick = { deleting = e },
            )
        }
    }

    if (editSalary) {
        var amount by remember { mutableStateOf(if (settings.salaryPaise > 0) Money.input(settings.salaryPaise) else "") }
        var day by remember { mutableStateOf(settings.salaryDay.toString()) }
        val dayNum = day.toIntOrNull()
        FormDialog(
            "Salary", canSave = parseBalance(amount) != null && dayNum in 1..31,
            onDismiss = { editSalary = false },
            onSave = { context.write { money().saveSettings(settings.copy(salaryPaise = parseBalance(amount)!!, salaryDay = dayNum!!)) } },
        ) {
            MoneyInput(amount, { amount = it }, "Monthly salary")
            NumberInput(day, { day = it }, "Pay day (1–31)")
        }
    }
    if (editLimit) {
        var amount by remember { mutableStateOf(if (settings.monthlyLimitPaise > 0) Money.input(settings.monthlyLimitPaise) else "") }
        FormDialog(
            "Monthly limit", canSave = parseBalance(amount) != null, onDismiss = { editLimit = false },
            onSave = { context.write { money().saveSettings(settings.copy(monthlyLimitPaise = parseBalance(amount)!!)) } },
        ) {
            MoneyInput(amount, { amount = it }, "Spend at most")
            Hint("Leave empty to turn the limit off.", Modifier)
        }
    }
    if (receiveSalary) {
        var bankId by remember { mutableStateOf(banks.firstOrNull()?.id) }
        FormDialog(
            "Salary received", canSave = true, onDismiss = { receiveSalary = false },
            onSave = { context.write { money().receiveSalary(settings, bankId, Day.monthKey(today)) } },
        ) {
            Text("${Money.format(settings.salaryPaise)} for ${Day.monthLabel(today)}")
            BankChoice("Add to", banks, bankId) { bankId = it }
        }
    }
    deleting?.let { e ->
        ConfirmDialog(
            "Delete spend?",
            "${Money.format(e.amountPaise)} · ${e.category}" + if (e.bankId != null) "\nThe amount goes back to its bank." else "",
            "Delete", onDismiss = { deleting = null },
        ) { context.write { money().deleteExpense(e) } }
    }
}

@Composable
private fun ExpenseDialog(banks: List<Bank>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.expense.first()) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(today) }
    var bankId by remember { mutableStateOf(banks.firstOrNull()?.id) }
    var pickDate by remember { mutableStateOf(false) }
    FormDialog(
        "Spent", canSave = Money.parse(amount) != null, onDismiss = onDismiss,
        onSave = { context.write { money().addExpense(Expense(0, Money.parse(amount)!!, category, note.trim(), date, bankId)) } },
    ) {
        MoneyInput(amount, { amount = it }, "Amount")
        Choice("Category", Categories.expense, category, { category = it }, Modifier.fillMaxWidth())
        TextInput(note, { note = it }, "Note (optional)")
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${Day.label(date, today)}") }
        BankChoice("Paid from", banks, bankId) { bankId = it }
    }
    if (pickDate) DatePickerDialogFor(date, { pickDate = false }) { date = minOf(it, today); pickDate = false }
}

// ---------------------------------------------------------------- Monthly payables

@Composable
private fun MonthlyTab(banks: List<Bank>, loans: List<Loan>, edit: (Any) -> Unit) {
    val db = LocalContext.current.db
    val context = LocalContext.current
    val today = LocalToday.current
    val payables = rememberFlow(emptyList<Payable>()) { db.money().payables() }
    var paying by remember { mutableStateOf<Payable?>(null) }
    var undoing by remember { mutableStateOf<Payable?>(null) }

    val total = payables.sumOf { it.amountPaise }
    val paid = payables.filter { Finance.isPaid(it, today) }.sumOf { it.amountPaise }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ItemRow("Every month", Money.format(total))
            ItemRow("Paid in ${Day.monthLabel(today)}", Money.format(paid), valueColor = Good)
            ItemRow("Still to pay", Money.format(total - paid), valueColor = if (total > paid) Bad else Good)
        }
    }
    if (payables.isEmpty()) Hint("Add EMIs, rent, credit card bills and other monthly payments.")
    payables.forEach { p ->
        val isPaid = Finance.isPaid(p, today)
        val dueDay = Day.inMonth(today, p.dueDay)
        val loan = loans.firstOrNull { it.id == p.loanId }
        ItemRow(
            p.title, Money.format(p.amountPaise),
            sub = listOfNotNull(
                p.kind,
                when {
                    isPaid -> "paid ✓"
                    dueDay < today -> "overdue since ${Day.short(dueDay)}"
                    else -> "due ${Day.label(dueDay, today)}"
                },
                loan?.let { "loan: ${it.name}" },
            ).joinToString(" · "),
            valueColor = if (!isPaid && dueDay < today) Bad else MaterialTheme.colorScheme.onSurface,
            onClick = { edit(p) },
            trailing = {
                if (isPaid) TextButton(onClick = { undoing = p }) { Text("Paid", color = Good) }
                else FilledTonalButton(onClick = { paying = p }) { Text("Pay") }
            },
        )
    }

    paying?.let { p ->
        var bankId by remember(p) { mutableStateOf(banks.firstOrNull()?.id) }
        FormDialog(
            "Pay ${p.title}", canSave = true, onDismiss = { paying = null },
            onSave = { context.write { money().payPayable(p, bankId, Day.monthKey(today)) } },
        ) {
            Text(Money.format(p.amountPaise) + if (p.loanId != null) " · reduces the loan's outstanding amount" else "")
            BankChoice("Paid from", banks, bankId) { bankId = it }
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
private fun PayableDialog(existing: Payable?, loans: List<Loan>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: Categories.payables.first()) }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    var day by remember { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var loanId by remember { mutableStateOf(existing?.loanId) }
    val dayNum = day.toIntOrNull()
    FormDialog(
        if (existing == null) "Monthly payment" else "Edit payment",
        canSave = title.isNotBlank() && Money.parse(amount) != null && dayNum in 1..31,
        onDismiss = onDismiss,
        onSave = {
            val p = (existing ?: Payable(title = "", kind = "", amountPaise = 0, dueDay = 1, loanId = null))
                .copy(title = title.trim(), kind = kind, amountPaise = Money.parse(amount)!!, dueDay = dayNum!!, loanId = loanId)
            context.write { money().savePayable(p) }
        },
        onDelete = existing?.let { { context.write { money().deletePayable(it) } } },
    ) {
        TextInput(title, { title = it }, "Name (e.g. Home loan EMI)")
        Choice("Type", Categories.payables, kind, { kind = it }, Modifier.fillMaxWidth())
        MoneyInput(amount, { amount = it }, "Amount")
        NumberInput(day, { day = it }, "Due day of month (1–31)")
        if (loans.isNotEmpty()) {
            Choice(
                "Loan", listOf<Loan?>(null) + loans, loans.firstOrNull { it.id == loanId }, { loanId = it?.id },
                Modifier.fillMaxWidth(), display = { it?.name ?: "None" },
            )
        }
    }
}

// ---------------------------------------------------------------- Banks

@Composable
private fun BanksTab(banks: List<Bank>, edit: (Any) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        ItemRow("Total balance", Money.format(banks.sumOf { it.balancePaise }), Modifier.padding(vertical = 8.dp))
    }
    if (banks.isEmpty()) Hint("Add your bank accounts and their balances. Spends, salary and payments update them.")
    banks.forEach { b -> ItemRow(b.name, Money.format(b.balancePaise), onClick = { edit(b) }) }
}

@Composable
private fun BankDialog(existing: Bank?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var balance by remember { mutableStateOf(existing?.let { Money.input(it.balancePaise) } ?: "") }
    FormDialog(
        if (existing == null) "Bank account" else "Edit bank",
        canSave = name.isNotBlank() && parseBalance(balance) != null, onDismiss = onDismiss,
        onSave = {
            context.write {
                money().saveBank(Bank(existing?.id ?: 0, name.trim(), parseBalance(balance)!!))
            }
        },
        onDelete = existing?.let { { context.write { money().deleteBank(it) } } },
    ) {
        TextInput(name, { name = it }, "Bank name")
        MoneyInput(balance, { balance = it }, "Current balance")
    }
}

// ---------------------------------------------------------------- Loans

@Composable
private fun LoansTab(loans: List<Loan>, edit: (Any) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ItemRow("Outstanding", Money.format(loans.sumOf { it.outstandingPaise }), valueColor = Bad)
            ItemRow("EMIs a month", Money.format(loans.sumOf { it.emiPaise }))
        }
    }
    if (loans.isEmpty()) Hint("Add your loans. Link an EMI under Monthly and paying it lowers the outstanding amount.")
    loans.forEach { l ->
        Column(Modifier.fillMaxWidth()) {
            ItemRow(
                l.name, Money.format(l.outstandingPaise),
                sub = listOfNotNull(
                    "of ${Money.format(l.totalPaise)}",
                    l.emiPaise.takeIf { it > 0 }?.let { "EMI ${Money.format(it)}" },
                    l.note.ifBlank { null },
                ).joinToString(" · "),
                onClick = { edit(l) },
            )
            val repaid = (l.totalPaise - l.outstandingPaise).toFloat() / l.totalPaise.coerceAtLeast(1)
            ProgressRow("Repaid", "${(repaid * 100).toInt()}%", repaid, Good)
        }
    }
}

@Composable
private fun LoanDialog(existing: Loan?, onDismiss: () -> Unit) {
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
    FormDialog(
        if (existing == null) "Loan" else "Edit loan",
        canSave = name.isNotBlank() && totalP != null && outstandingP != null && outstandingP <= totalP && emiP != null &&
            (!wantsPayable || dueDay.toIntOrNull() in 1..31),
        onDismiss = onDismiss,
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
        MoneyInput(total, { total = it }, "Loan amount")
        MoneyInput(outstanding, { outstanding = it }, "Still to repay", optional = existing == null)
        if (outstandingP != null && totalP != null && outstandingP > totalP) {
            Text("Can't be more than the loan amount", color = Bad, style = MaterialTheme.typography.bodySmall)
        }
        MoneyInput(emi, { emi = it }, "EMI", optional = true)
        TextInput(note, { note = it }, "Note (optional)")
        if (existing == null && emiP != null && emiP > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(addPayable, { addPayable = it })
                Text("Add EMI to monthly payments")
            }
            if (addPayable) NumberInput(dueDay, { dueDay = it }, "EMI due day (1–31)")
        }
    }
}

// ---------------------------------------------------------------- People (to give / to get)

@Composable
private fun PeopleTab(edit: (Any) -> Unit) {
    val db = LocalContext.current.db
    val context = LocalContext.current
    val today = LocalToday.current
    val people = rememberFlow(emptyList<PersonDebt>()) { db.money().personDebts() }
    var paying by remember { mutableStateOf<PersonDebt?>(null) }

    val open = people.filterNot { it.settled }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ItemRow("I have to give", Money.format(open.filter { it.toGive }.sumOf { it.remainingPaise }), valueColor = Bad)
            ItemRow("I have to get", Money.format(open.filterNot { it.toGive }.sumOf { it.remainingPaise }), valueColor = Good)
        }
    }
    if (people.isEmpty()) Hint("Track money you owe friends and family, and money they owe you.")

    @Composable
    fun Section(title: String, list: List<PersonDebt>) {
        if (list.isEmpty()) return
        SectionTitle(title)
        list.forEach { d ->
            ItemRow(
                d.person, Money.format(d.remainingPaise),
                sub = listOfNotNull(
                    if (d.paidPaise > 0) "${Money.format(d.paidPaise)} of ${Money.format(d.amountPaise)} settled" else null,
                    d.dueDate?.let { if (it < today) "was due ${Day.short(it)}" else "due ${Day.label(it, today)}" },
                    d.note.ifBlank { null },
                ).joinToString(" · ").ifBlank { null },
                valueColor = if (d.toGive) Bad else Good,
                onClick = { edit(d) },
                trailing = { TextButton(onClick = { paying = d }) { Text(if (d.toGive) "Paid" else "Got") } },
            )
        }
    }
    Section("To give", open.filter { it.toGive })
    Section("To get", open.filterNot { it.toGive })
    val settled = people.filter { it.settled }
    if (settled.isNotEmpty()) {
        SectionTitle("Settled")
        settled.forEach { d ->
            ItemRow(d.person, Money.format(d.amountPaise), sub = if (d.toGive) "gave back" else "got back", onClick = { edit(d) })
        }
    }

    paying?.let { d ->
        var amount by remember(d) { mutableStateOf(Money.input(d.remainingPaise)) }
        val p = Money.parse(amount)
        FormDialog(
            if (d.toGive) "Paid ${d.person}" else "Got from ${d.person}",
            canSave = p != null && p <= d.remainingPaise, onDismiss = { paying = null },
            onSave = { context.write { money().savePersonDebt(d.copy(paidPaise = d.paidPaise + p!!)) } },
        ) {
            Text("${Money.format(d.remainingPaise)} left")
            MoneyInput(amount, { amount = it }, "Amount")
        }
    }
}

@Composable
private fun PersonDialog(existing: PersonDebt?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = LocalToday.current
    var person by remember { mutableStateOf(existing?.person ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    var toGive by remember { mutableStateOf(existing?.toGive ?: true) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    FormDialog(
        if (existing == null) "To give / to get" else "Edit",
        canSave = person.isNotBlank() && Money.parse(amount) != null, onDismiss = onDismiss,
        onSave = {
            val d = (existing ?: PersonDebt(person = "", amountPaise = 0, toGive = true, dueDate = null, note = ""))
                .copy(person = person.trim(), amountPaise = Money.parse(amount)!!, toGive = toGive, dueDate = due, note = note.trim())
            context.write { money().savePersonDebt(d) }
        },
        onDelete = existing?.let { { context.write { money().deletePersonDebt(it) } } },
    ) {
        Choice("Type", listOf(true, false), toGive, { toGive = it }, Modifier.fillMaxWidth(), display = { if (it) "I have to give" else "I have to get" })
        TextInput(person, { person = it }, "Person")
        MoneyInput(amount, { amount = it }, "Amount")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                Text(due?.let { "Due ${Day.short(it)}" } ?: "Due date (optional)")
            }
            if (due != null) TextButton(onClick = { due = null }) { Text("Clear") }
        }
        TextInput(note, { note = it }, "Note (optional)")
    }
    if (pickDate) DatePickerDialogFor(due ?: today, { pickDate = false }) { due = it; pickDate = false }
}

// ---------------------------------------------------------------- Savings

@Composable
private fun SavingsTab(edit: (Any) -> Unit) {
    val db = LocalContext.current.db
    val savings = rememberFlow(emptyList<Saving>()) { db.money().savings() }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Total saved  ${Money.format(savings.sumOf { it.amountPaise })}",
            Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Good,
        )
    }
    if (savings.isEmpty()) Hint("Add FDs, RDs, an emergency fund, mutual funds, gold… anything you've put away.")
    savings.forEach { s -> ItemRow(s.name, Money.format(s.amountPaise), onClick = { edit(s) }) }
}

@Composable
private fun SavingDialog(existing: Saving?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Money.input(it.amountPaise) } ?: "") }
    FormDialog(
        if (existing == null) "Saving" else "Edit saving",
        canSave = name.isNotBlank() && parseBalance(amount) != null, onDismiss = onDismiss,
        onSave = { context.write { money().saveSaving(Saving(existing?.id ?: 0, name.trim(), parseBalance(amount)!!)) } },
        onDelete = existing?.let { { context.write { money().deleteSaving(it) } } },
    ) {
        TextInput(name, { name = it }, "Name (e.g. Emergency fund)")
        MoneyInput(amount, { amount = it }, "Amount saved")
    }
}
