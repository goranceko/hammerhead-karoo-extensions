package io.github.goranceko.devicejuice.spike

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gets the spike log off the Karoo without adb: share sheet, or a copy in Downloads (USB file transfer). */
object SpikeExport {
    private fun exportFile(context: Context, log: SpikeLog): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "device-juice-spike-$stamp.log")
        log.exportTo(file)
        return file
    }

    fun shareIntent(context: Context, log: SpikeLog): Intent {
        val file = exportFile(context, log)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share spike log")
    }

    /** Copies the log to Downloads/DeviceJuice. Returns a user-facing result line. */
    fun saveToDownloads(context: Context, log: SpikeLog): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Needs Android 10+; use Share instead"
        val file = exportFile(context, log)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DeviceJuice")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return "Could not create the file in Downloads"
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        return "Saved Download/DeviceJuice/${file.name}"
    }
}
