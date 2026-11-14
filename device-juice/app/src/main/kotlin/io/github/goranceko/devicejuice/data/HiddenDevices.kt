package io.github.goranceko.devicejuice.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Row ids the rider chose to hide (a device id hides all its components). Stays on the Karoo. */
class HiddenDevices(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device_juice", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun hide(id: String) = prefs.edit().putStringSet(KEY, get() + id).apply()

    fun unhide(id: String) = prefs.edit().putStringSet(KEY, get() - id).apply()

    fun flow(): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) trySend(get()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY = "hidden_ids"
    }
}
