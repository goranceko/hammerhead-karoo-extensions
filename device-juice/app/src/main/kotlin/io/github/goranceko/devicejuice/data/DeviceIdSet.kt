package io.github.goranceko.devicejuice.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A set of row ids kept on the Karoo: hidden devices, pinned devices. A device id covers all its components. */
class DeviceIdSet(context: Context, private val key: String) {
    private val prefs = context.applicationContext.getSharedPreferences("device_juice", Context.MODE_PRIVATE)

    fun get(): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty().toSet()

    fun add(id: String) = prefs.edit().putStringSet(key, get() + id).apply()

    fun remove(id: String) = prefs.edit().putStringSet(key, get() - id).apply()

    fun flow(): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> if (changed == key) trySend(get()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

fun hiddenDevices(context: Context) = DeviceIdSet(context, "hidden_ids")

fun pinnedDevices(context: Context) = DeviceIdSet(context, "pinned_ids")
