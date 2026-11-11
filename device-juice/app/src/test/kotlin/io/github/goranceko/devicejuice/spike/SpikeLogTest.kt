package io.github.goranceko.devicejuice.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpikeLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val t0 = 1_762_898_400_000L // 2025-11-11T22:00:00Z

    @Test
    fun `formats lines with UTC timestamp and tag, and mirrors to sink`() {
        val sunk = mutableListOf<Pair<String, String>>()
        val log = SpikeLog(tmp.root, clock = { t0 }, sink = { tag, msg -> sunk += tag to msg })
        log.write("BOOT", "hello")
        assertEquals(listOf("2025-11-11T22:00:00.000Z [BOOT] hello"), log.tail(10))
        assertEquals(listOf("BOOT" to "hello"), sunk)
    }

    @Test
    fun `rotates by size and keeps at most maxFiles`() {
        val log = SpikeLog(tmp.root, maxBytes = 100, maxFiles = 3, clock = { t0 })
        // Each line is 24 (timestamp) + 1 + 6 ([TAG] ) ... about 40 bytes, so ~2 lines per file.
        repeat(20) { log.write("TAG", "line %02d".format(it)) }
        val files = log.files()
        assertEquals(3, files.size)
        assertEquals(listOf("spike.2.log", "spike.1.log", "spike.log"), files.map { it.name })
        files.forEach { assertTrue("${it.name} is ${it.length()} bytes", it.length() <= 100) }
        // Newest lines survive, in order.
        val tail = log.tail(3).map { it.substringAfter("] ") }
        assertEquals(listOf("line 17", "line 18", "line 19"), tail)
    }

    @Test
    fun `a single line larger than maxBytes is still written`() {
        val log = SpikeLog(tmp.root, maxBytes = 10, maxFiles = 2, clock = { t0 })
        log.write("BIG", "x".repeat(50))
        log.write("BIG", "y".repeat(50))
        assertEquals(2, log.files().size)
        assertTrue(log.tail(1).single().endsWith("y".repeat(50)))
    }

    @Test
    fun `export concatenates oldest first`() {
        val log = SpikeLog(tmp.root, maxBytes = 100, maxFiles = 3, clock = { t0 })
        repeat(5) { log.write("TAG", "line $it") }
        val out = tmp.newFile("export.log")
        log.exportTo(out)
        assertEquals((0..4).map { "line $it" }, out.readLines().map { it.substringAfter("] ") })
    }

    @Test
    fun `snapshot round trip`() {
        val log = SpikeLog(tmp.root)
        assertEquals(null, log.readSnapshot("latest"))
        log.writeSnapshot("latest", "{\"devices\":[]}")
        assertEquals("{\"devices\":[]}", log.readSnapshot("latest"))
    }
}
