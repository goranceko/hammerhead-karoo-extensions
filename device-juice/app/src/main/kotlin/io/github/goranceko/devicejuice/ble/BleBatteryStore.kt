package io.github.goranceko.devicejuice.ble

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Last Bluetooth battery % per device, shared by the app and the extension service. */
class BleBatteryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_juice", Context.MODE_PRIVATE)

    fun get(): Map<String, BleBattery> = BleBatteries.decode(prefs.getString(KEY, null))

    fun put(results: Map<String, BleBattery>) {
        if (results.isEmpty()) return
        prefs.edit().putString(KEY, BleBatteries.encode(get() + results)).apply()
    }

    fun flow(): Flow<Map<String, BleBattery>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == KEY) trySend(get()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY = "ble_battery"
    }
}
