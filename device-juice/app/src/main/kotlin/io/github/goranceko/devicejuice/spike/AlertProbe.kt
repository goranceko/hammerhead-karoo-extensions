package io.github.goranceko.devicejuice.spike

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.github.goranceko.devicejuice.R
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.SystemNotification
import java.util.UUID

/**
 * Debug builds only: sends a test InRideAlert on
 * `adb shell am broadcast -a io.github.goranceko.devicejuice.PROBE_ALERT --es id X --es icon app|android --es color res|argb`.
 */
class AlertProbe(private val context: Context, private val karoo: KarooSystemService) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getStringExtra("id") ?: UUID.randomUUID().toString()
            val icon = if (i.getStringExtra("icon") == "android") android.R.drawable.ic_dialog_alert else R.drawable.ic_device_juice
            val argb = i.getStringExtra("color") == "argb"
            val alert = InRideAlert(
                id = id,
                icon = icon,
                title = i.getStringExtra("title") ?: "Probe $id",
                detail = i.getStringExtra("detail") ?: "icon=${i.getStringExtra("icon")} color=${i.getStringExtra("color")}",
                autoDismissMs = i.getLongExtra("dismiss", 10_000),
                backgroundColor = if (argb) 0xFFFF8A1F.toInt() else R.color.alert_orange,
                textColor = if (argb) 0xFF090B0A.toInt() else R.color.alert_text,
            )
            Log.i("DeviceJuice", "probe dispatch $alert connected=${karoo.connected}")
            val ok = if (i.getBooleanExtra("notify", false)) {
                karoo.dispatch(SystemNotification(id = id, message = "Probe notify $id"))
            } else {
                karoo.dispatch(alert)
            }
            Log.i("DeviceJuice", "probe dispatch returned $ok")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        val f = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) else context.registerReceiver(receiver, f)
    }

    fun stop() = runCatching { context.unregisterReceiver(receiver) }

    companion object {
        const val ACTION = "io.github.goranceko.devicejuice.PROBE_ALERT"
    }
}
