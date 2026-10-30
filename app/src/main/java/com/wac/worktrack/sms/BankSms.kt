package com.wac.worktrack.sms

import com.wac.worktrack.core.Money
import com.wac.worktrack.data.Bank
import com.wac.worktrack.data.PendingTxn
import java.time.Instant
import java.time.ZoneId

/** Reads money sent / received out of a bank SMS. Anything it isn't sure about is skipped. */
object BankSms {

    private val amount = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    // SBI-style "debited by 1,500.0", with no currency.
    private val bareAmount = Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val debit = Regex("""\b(debited|debit|spent|paid|sent|withdrawn|purchase|transferred)\b""", RegexOption.IGNORE_CASE)
    private val credit = Regex("""\b(credited|received|deposited|refunded)\b""", RegexOption.IGNORE_CASE)

    /** OTPs, reminders, requests and failures mention amounts but move no money. */
    private val noise = Regex(
        """\b(otp|one.?time|password|verification code|will be (debited|credited)|is due|due (on|date|by)|requested|collect request|request(ed)? (money|rs|inr)|declined|failed|unsuccessful|reversal|minimum amount|bill (generated|is)|statement)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val account = Regex("""(?:a/?c|acct|account|card)\D{0,15}(\d{3,6})""", RegexOption.IGNORE_CASE)

    // Who it went to / came from: the words after "to" / "from", up to the next keyword or punctuation.
    private const val PARTY = """(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|ref\w*|upi|via|imps|neft|rtgs|from|to|avl|bal|a/c|for|is|has|dated|txn\w*)\b|\s+-|[.,;:(]|$)"""
    private val paidTo = Regex("""\b(?:to|at|towards|trf to|paid to)\s+$PARTY""", RegexOption.IGNORE_CASE)
    private val gotFrom = Regex("""\b(?:from|by)\s+$PARTY""", RegexOption.IGNORE_CASE)

    private val channels = setOf("UPI", "IMPS", "NEFT", "RTGS", "NET BANKING", "NETBANKING", "ATM", "CASH", "CHEQUE", "POS")

    /** A pending transaction for [body] sent by [address] at [millis], or null when it isn't one. */
    fun parse(address: String, body: String, millis: Long, banks: List<Bank>): PendingTxn? {
        // Banks send from letter codes like "VM-HDFCBK"; people send from phone numbers.
        if (address.none { it.isLetter() }) return null
        if (noise.containsMatchIn(body)) return null
        val paise = (amount.find(body) ?: bareAmount.find(body))?.groupValues?.get(1)?.replace(",", "")?.let(Money::parse) ?: return null
        val d = debit.find(body)?.range?.first
        val c = credit.find(body)?.range?.first
        // "Debited from your A/c … credited to Ravi": the first one says which way the money went.
        val isCredit = when {
            d == null && c == null -> return null
            d == null -> true
            c == null -> false
            else -> c < d
        }
        val party = (if (isCredit) gotFrom else paidTo).findAll(body)
            .map { it.groupValues[1].trim() }
            // Skip "from your A/c", "to your account" and the channel in "by UPI from Anand".
            .firstOrNull { p ->
                p.isNotBlank() && !p.startsWith("your", true) && !p.startsWith("a/c", true) && !p.startsWith("ac ", true) &&
                    p.uppercase() !in channels
            }
            .orEmpty()
        return PendingTxn(
            smsKey = "${address.uppercase()}|${body.trim().hashCode()}",
            amountPaise = paise,
            credit = isCredit,
            party = party,
            bankId = matchBank(address, body, banks),
            account = account.find(body)?.groupValues?.get(1)?.takeLast(4).orEmpty(),
            date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
            body = body.trim(),
        )
    }

    /** The bank whose name is in the SMS, or whose first letters are in the sender code ("HDFC" in "VM-HDFCBK"). */
    private fun matchBank(address: String, body: String, banks: List<Bank>): Long? {
        val sender = address.substringAfter('-').uppercase()
        return banks.firstOrNull { body.contains(it.name.trim(), ignoreCase = true) }?.id
            ?: banks.firstOrNull { b ->
                val word = b.name.trim().substringBefore(' ').uppercase().filter { it.isLetter() }
                word.length >= 3 && sender.contains(word.take(3))
            }?.id
    }
}
