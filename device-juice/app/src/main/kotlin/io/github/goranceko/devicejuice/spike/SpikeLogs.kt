package io.github.goranceko.devicejuice.spike

import android.content.Context
import android.util.Log
import java.io.File

/** Process-wide [SpikeLog] in app storage (`files/spike/`), mirrored to logcat under tag `DeviceJuice`. */
object SpikeLogs {
    const val LOGCAT_TAG = "DeviceJuice"
    private const val LOGCAT_CHUNK = 3500

    @Volatile
    private var instance: SpikeLog? = null

    fun get(context: Context): SpikeLog = instance ?: synchronized(this) {
        instance ?: SpikeLog(File(context.applicationContext.filesDir, "spike"), sink = ::toLogcat).also { instance = it }
    }

    private fun toLogcat(tag: String, message: String) {
        // logcat truncates long lines; SavedDevices JSON can be several KB.
        val chunks = message.chunked(LOGCAT_CHUNK)
        chunks.forEachIndexed { i, chunk ->
            val part = if (chunks.size > 1) " (${i + 1}/${chunks.size})" else ""
            Log.i(LOGCAT_TAG, "[$tag]$part $chunk")
        }
    }
}
