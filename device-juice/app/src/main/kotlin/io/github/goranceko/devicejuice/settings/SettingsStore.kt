package io.github.goranceko.devicejuice.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_juice", Context.MODE_PRIVATE)

    fun get(): Settings = Settings(
        names = Settings.decodeNames(prefs.getString(NAMES, null)),
        thresholds = Thresholds(
            green = prefs.getInt(GREEN, Thresholds.DEFAULT.green),
            yellow = prefs.getInt(YELLOW, Thresholds.DEFAULT.yellow),
            orange = prefs.getInt(ORANGE, Thresholds.DEFAULT.orange),
        ).takeIf { it.valid() } ?: Thresholds.DEFAULT,
        rideStartAlert = prefs.getBoolean(START_ALERT, true),
        rideEndNotice = prefs.getBoolean(END_NOTICE, true),
        preRideNotice = prefs.getBoolean(PRE_RIDE_NOTICE, true),
        bleExact = prefs.getBoolean(BLE_EXACT, false),
    )

    fun rename(id: String, name: String?) {
        val names = get().names.toMutableMap()
        val clean = name?.let(Settings::clean).orEmpty()
        if (clean.isEmpty()) names.remove(id) else names[id] = clean
        prefs.edit().putString(NAMES, Settings.encodeNames(names)).apply()
    }

    fun setThresholds(t: Thresholds) {
        if (!t.valid()) return
        prefs.edit().putInt(GREEN, t.green).putInt(YELLOW, t.yellow).putInt(ORANGE, t.orange).apply()
    }

    fun setRideStartAlert(on: Boolean) = prefs.edit().putBoolean(START_ALERT, on).apply()
    fun setRideEndNotice(on: Boolean) = prefs.edit().putBoolean(END_NOTICE, on).apply()
    fun setPreRideNotice(on: Boolean) = prefs.edit().putBoolean(PRE_RIDE_NOTICE, on).apply()
    fun setBleExact(on: Boolean) = prefs.edit().putBoolean(BLE_EXACT, on).apply()

    fun flow(): Flow<Settings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key in KEYS) trySend(get()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val NAMES = "names"
        const val GREEN = "threshold_green"
        const val YELLOW = "threshold_yellow"
        const val ORANGE = "threshold_orange"
        const val START_ALERT = "ride_start_alert"
        const val END_NOTICE = "ride_end_notice"
        const val PRE_RIDE_NOTICE = "pre_ride_notice"
        const val BLE_EXACT = "ble_exact"
        val KEYS = setOf(NAMES, GREEN, YELLOW, ORANGE, START_ALERT, END_NOTICE, PRE_RIDE_NOTICE, BLE_EXACT)
    }
}
