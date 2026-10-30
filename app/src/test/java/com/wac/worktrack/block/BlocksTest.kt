package com.wac.worktrack.block

import com.wac.worktrack.data.AppBlock
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocksTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val workday = AppBlock(packages = "a", daily = true, startMinutes = 9 * 60, endMinutes = 17 * 60)
    private val night = AppBlock(packages = "a,b", daily = true, startMinutes = 22 * 60, endMinutes = 7 * 60)

    @Test
    fun daily() {
        assertEquals(at(30, 17), Blocks.endsAt(workday, at(30, 9)))
        assertEquals(at(30, 17), Blocks.endsAt(workday, at(30, 16, 59)))
        assertNull(Blocks.endsAt(workday, at(30, 17)))
        assertNull(Blocks.endsAt(workday, at(30, 8, 59)))
    }

    @Test
    fun overnight() {
        assertEquals(at(31, 7), Blocks.endsAt(night, at(30, 23)))
        assertEquals(at(30, 7), Blocks.endsAt(night, at(30, 2)))
        assertNull(Blocks.endsAt(night, at(30, 12)))
    }

    @Test
    fun forAWhile() {
        val block = AppBlock(packages = "a", daily = false, untilMillis = at(30, 18))
        assertEquals(at(30, 18), Blocks.endsAt(block, at(30, 17)))
        assertNull(Blocks.endsAt(block, at(30, 18)))
    }

    @Test
    fun onlyChosenApps() {
        assertEquals(at(31, 7), Blocks.blockedUntil(listOf(workday, night), "b", at(30, 23)))
        assertNull(Blocks.blockedUntil(listOf(workday, night), "c", at(30, 23)))
    }
}
