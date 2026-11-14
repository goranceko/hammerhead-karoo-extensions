package io.github.goranceko.devicejuice.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Opt-in exact % for BLE-paired sensors (PLAN.md option D): connect by address, read Battery Level, disconnect.
 * Android shares the radio link with the Karoo's own connection; a sensor that allows only one connection
 * just times out and keeps its Karoo level.
 */
class BleBatteryReader(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Reads each address in turn, [TIMEOUT_MS] per device. Returns only successful reads. */
    suspend fun read(ids: Collection<String>): Map<String, BleBattery> {
        if (!hasPermission()) return emptyMap()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyMap()
        if (!adapter.isEnabled) return emptyMap()
        val out = mutableMapOf<String, BleBattery>()
        for (id in ids.filter(BleBatteries::isBleId)) {
            val pct = withTimeoutOrNull(TIMEOUT_MS) { readOne(adapter.getRemoteDevice(id.uppercase())) }
            Log.i(TAG, "BLE battery $id -> ${pct ?: "no answer"}")
            if (pct != null) out[id] = BleBattery(pct, clock())
        }
        return out
    }

    @SuppressLint("MissingPermission") // checked in read()
    private suspend fun readOne(device: BluetoothDevice): Int? = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<Int?>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                    newState == BluetoothProfile.STATE_DISCONNECTED -> result.complete(null)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val c = gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
                if (c == null || !gatt.readCharacteristic(c)) result.complete(null)
            }

            @Deprecated("Pre-API 33 callback; Karoo 3 runs API 32")
            override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                @Suppress("DEPRECATION")
                val v = c.value?.firstOrNull()?.toInt()?.and(0xFF)
                result.complete(v?.takeIf { status == BluetoothGatt.GATT_SUCCESS && it in 0..100 })
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                val v = value.firstOrNull()?.toInt()?.and(0xFF)
                result.complete(v?.takeIf { status == BluetoothGatt.GATT_SUCCESS && it in 0..100 })
            }
        }
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            result.await()
        } finally {
            gatt.disconnect()
            gatt.close()
        }
    }

    companion object {
        private const val TAG = "DeviceJuice"
        private const val TIMEOUT_MS = 10_000L
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
}
