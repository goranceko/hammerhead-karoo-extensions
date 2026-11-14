package io.github.goranceko.devicejuice.estimate

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The last [RideLogCodec.MAX_RIDES] rides, kept on the Karoo. Nothing leaves the device. */
class RideLogStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_juice", Context.MODE_PRIVATE)

    fun get(): List<RideEntry> = RideLogCodec.decode(prefs.getString(KEY, null))

    fun append(ride: RideEntry) = prefs.edit().putString(KEY, RideLogCodec.encode(get() + ride)).apply()

    fun flow(): Flow<List<RideEntry>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == KEY) trySend(get()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY = "ride_log"
    }
}
