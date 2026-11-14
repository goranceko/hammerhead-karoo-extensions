package io.github.goranceko.devicejuice.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeTextTest {
    private val now = 1_000_000_000_000L
    private fun age(minutesAgo: Long) = ageText(now - minutesAgo * 60_000, now) { "2 Jun" }

    @Test
    fun `short ages`() {
        assertEquals("just now", age(0))
        assertEquals("42m ago", age(42))
        assertEquals("3h ago", age(3 * 60 + 5))
        assertEquals("5d ago", age(5 * 24 * 60))
        assertEquals("2 Jun", age(8 * 24 * 60))
        assertNull(ageText(null, now))
    }
}
