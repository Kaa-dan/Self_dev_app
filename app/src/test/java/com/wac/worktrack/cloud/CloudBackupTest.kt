package com.wac.worktrack.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cloud backup chunks must stay under Firestore's 1 MiB document limit. */
class CloudBackupTest {

    private fun row(id: Int, text: String) = """{"id":$id,"text":"$text"}"""

    /** Joins the chunks back together the way restore does. */
    private fun rejoin(chunks: List<String>): List<String> =
        chunks.flatMap { it.removePrefix("[").removeSuffix("]").split(Regex(",(?=\\{)")) }.filter { it.isNotEmpty() }

    @Test
    fun noRowsMeansNoChunks() {
        assertEquals(emptyList<String>(), CloudBackup.chunk(emptyList()))
    }

    @Test
    fun smallTableIsOneChunk() {
        val rows = List(5) { row(it, "x") }
        val chunks = CloudBackup.chunk(rows)
        assertEquals(1, chunks.size)
        assertEquals("[" + rows.joinToString(",") + "]", chunks[0])
    }

    @Test
    fun longJournalSplitsUnderLimitAndKeepsOrder() {
        // ~5 KB entries: 400 of them (the old fixed chunk) would be ~2 MB, over Firestore's limit.
        val rows = List(1000) { row(it, "a".repeat(5000)) }
        val chunks = CloudBackup.chunk(rows)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.toByteArray().size <= CloudBackup.MAX_CHUNK_BYTES) }
        assertEquals(rows, rejoin(chunks))
    }

    @Test
    fun countsUtf8BytesNotChars() {
        // Malayalam letters are 3 bytes each in UTF-8.
        val rows = List(10) { row(it, "മ".repeat(100)) }
        val chunks = CloudBackup.chunk(rows, maxBytes = 1000)
        chunks.forEach { assertTrue(it.toByteArray().size <= 1000) }
        assertEquals(rows, rejoin(chunks))
    }

    @Test
    fun chunkExactlyAtLimitIsAllowed() {
        val rows = listOf(row(1, "a"), row(2, "b"))
        val exact = ("[" + rows.joinToString(",") + "]").length
        assertEquals(1, CloudBackup.chunk(rows, maxBytes = exact).size)
        assertEquals(2, CloudBackup.chunk(rows, maxBytes = exact - 1).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun singleRowTooBigFails() {
        CloudBackup.chunk(listOf(row(1, "a".repeat(2000))), maxBytes = 1000)
    }
}
