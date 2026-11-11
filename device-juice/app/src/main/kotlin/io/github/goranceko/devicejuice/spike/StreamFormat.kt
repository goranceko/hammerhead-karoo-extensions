package io.github.goranceko.devicejuice.spike

import io.hammerhead.karooext.models.StreamState

/** One-line description of a stream state for the spike log. Field ids are kept verbatim. */
fun describe(state: StreamState): String = when (state) {
    is StreamState.Streaming -> buildString {
        append("Streaming values=")
        append(state.dataPoint.values.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "${it.key}=${it.value}" })
        state.dataPoint.sourceId?.let { append(" sourceId=").append(it) }
    }
    StreamState.Idle -> "Idle"
    StreamState.Searching -> "Searching"
    StreamState.NotAvailable -> "NotAvailable"
}
