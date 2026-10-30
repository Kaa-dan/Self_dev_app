package com.wac.worktrack.sms

import com.wac.worktrack.data.Bank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsTest {
    private val banks = listOf(Bank(1, "HDFC", 0), Bank(2, "SBI", 0), Bank(3, "Federal Bank", 0))
    private fun parse(address: String, body: String) = BankSms.parse(address, body, 0, banks)

    @Test
    fun upiDebit() {
        val t = parse(
            "VM-HDFCBK",
            "Sent Rs.250.00 From HDFC Bank A/C *4521 To SWIGGY On 29/10/26 Ref 123456789012 Not You? Call 18002586161",
        )!!
        assertEquals(25_000, t.amountPaise)
        assertEquals(false, t.credit)
        assertEquals("SWIGGY", t.party)
        assertEquals(1L, t.bankId)
        assertEquals("4521", t.account)
    }

    @Test
    fun debitThenCreditedToPayee() {
        // SBI writes the amount with no "Rs".
        val t = parse(
            "AD-SBIUPI",
            "Dear UPI user A/C X1234 debited by 1,500.0 on date 29Oct26 trf to RAVI KUMAR Refno 430212345678. If not u? call 1800111109. -SBI",
        )!!
        assertEquals(150_000, t.amountPaise)
        assertEquals(false, t.credit)
        assertEquals("RAVI KUMAR", t.party)
        assertEquals(2L, t.bankId)
        assertEquals("1234", t.account)
    }

    @Test
    fun credit() {
        val t = parse(
            "JD-FEDBNK",
            "Rs 12,000.00 credited to your A/c XX7788 on 29-10-2026 by UPI from ANAND S. Avl Bal Rs 45,210.55 -Federal Bank",
        )!!
        assertEquals(1_200_000, t.amountPaise)
        assertEquals(true, t.credit)
        assertEquals("ANAND S", t.party)
        assertEquals(3L, t.bankId)
        assertEquals("7788", t.account)
    }

    @Test
    fun cardSpend() {
        val t = parse("VK-HDFCBK", "Spent Rs.1,299 On HDFC Bank Card 9876 At AMAZON PAY INDIA On 2026-10-29:10:22:11. Not You? Call 18002586161")!!
        assertEquals(129_900, t.amountPaise)
        assertEquals(false, t.credit)
        assertEquals("AMAZON PAY INDIA", t.party)
        assertEquals("9876", t.account)
    }

    @Test
    fun skipsNoise() {
        assertNull(parse("VM-HDFCBK", "OTP is 482913 for txn of Rs 2,000.00 at AMAZON. Do not share it with anyone."))
        assertNull(parse("VM-HDFCBK", "Rs 3,000 will be debited from your A/c XX4521 on 05-11-2026 towards NETFLIX"))
        assertNull(parse("VM-ICICIT", "ICICI Bank Credit Card XX1111 bill of Rs 5,400 is due on 10-Nov-26."))
        assertNull(parse("+919876543210", "I sent you Rs 500, check"))
        assertNull(parse("VM-HDFCBK", "Rahul has requested money of Rs 400 from you on Google Pay"))
    }
}
