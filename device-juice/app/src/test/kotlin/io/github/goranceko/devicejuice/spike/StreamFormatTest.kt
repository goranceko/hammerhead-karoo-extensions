package io.github.goranceko.devicejuice.spike

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFormatTest {
    @Test
    fun `streaming shows sorted values and source`() {
        val state = StreamState.Streaming(DataPoint("TYPE_X", mapOf("B" to 2.0, "A" to 1.0), sourceId = "dev1"))
        assertEquals("Streaming values={A=1.0,B=2.0} sourceId=dev1", describe(state))
    }

    @Test
    fun `non streaming states use their names`() {
        assertEquals("NotAvailable", describe(StreamState.NotAvailable))
        assertEquals("Searching", describe(StreamState.Searching))
        assertEquals("Idle", describe(StreamState.Idle))
    }
}
