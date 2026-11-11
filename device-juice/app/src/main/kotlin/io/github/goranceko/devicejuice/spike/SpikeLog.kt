package io.github.goranceko.devicejuice.spike

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Append-only spike log with size-based rotation.
 *
 * `spike.log` is the current file; when it would exceed [maxBytes] it becomes `spike.1.log`,
 * `spike.1.log` becomes `spike.2.log`, and so on up to [maxFiles] files in total.
 * Every line is `<UTC ISO timestamp> [TAG] message`, and is also passed to [sink] (logcat).
 */
class SpikeLog(
    val dir: File,
    private val maxBytes: Long = 512 * 1024,
    private val maxFiles: Int = 4,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sink: (tag: String, message: String) -> Unit = { _, _ -> },
) {
    init {
        require(maxFiles >= 1)
        dir.mkdirs()
    }

    private val current = File(dir, "spike.log")

    private fun rotated(index: Int) = File(dir, "spike.$index.log")

    @Synchronized
    fun write(tag: String, message: String) {
        val line = "${formatTimestamp(clock())} [$tag] $message\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (current.exists() && current.length() > 0 && current.length() + bytes.size > maxBytes) rotate()
        current.appendBytes(bytes)
        sink(tag, message)
    }

    private fun rotate() {
        if (maxFiles == 1) {
            current.delete()
            return
        }
        rotated(maxFiles - 1).delete()
        for (i in maxFiles - 2 downTo 1) {
            val from = rotated(i)
            if (from.exists()) from.renameTo(rotated(i + 1))
        }
        current.renameTo(rotated(1))
    }

    /** Log files, oldest first. */
    @Synchronized
    fun files(): List<File> =
        ((maxFiles - 1 downTo 1).map(::rotated) + current).filter { it.exists() }

    /** The last [n] lines across all files, oldest first. */
    @Synchronized
    fun tail(n: Int): List<String> {
        val out = ArrayDeque<String>(n)
        for (file in files()) {
            file.forEachLine { line ->
                if (out.size == n) out.removeFirst()
                out.addLast(line)
            }
        }
        return out.toList()
    }

    /** Concatenates all log files, oldest first, into [target]. */
    @Synchronized
    fun exportTo(target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> files().forEach { f -> f.inputStream().use { it.copyTo(out) } } }
    }

    /** Keeps the latest value of a named snapshot (for example the last SavedDevices JSON) for the UI. */
    @Synchronized
    fun writeSnapshot(name: String, content: String) {
        File(dir, "$name.txt").writeText(content)
    }

    @Synchronized
    fun readSnapshot(name: String): String? = File(dir, "$name.txt").takeIf { it.exists() }?.readText()

    companion object {
        fun formatTimestamp(epochMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(epochMs))
    }
}
