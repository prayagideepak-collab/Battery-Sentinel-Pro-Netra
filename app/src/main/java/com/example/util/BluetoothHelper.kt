package com.example.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.BluetoothDeviceItem

/**
 * Returns only currently connected Bluetooth devices.
 *
 * Bonded/paired but disconnected devices are intentionally excluded.
 * Battery level is exposed only through public Android APIs when available;
 * no reflection/private API access is used.
 */
object BluetoothHelper {

    fun getBluetoothDevices(context: Context): List<BluetoothDeviceItem> {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return emptyList()
            val adapter = manager.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()

            adapter.bondedDevices
                .asSequence()
                .filter { isDeviceConnected(manager, it) }
                .mapNotNull { device ->
                    val name = try {
                        device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                    } catch (_: SecurityException) {
                        "Bluetooth Device"
                    }
                    val address = try { device.address } catch (_: SecurityException) { return@mapNotNull null }
                    val deviceClass = try { device.bluetoothClass?.majorDeviceClass ?: 0 } catch (_: SecurityException) { 0 }

                    BluetoothDeviceItem(
                        name = name,
                        address = address,
                        isConnected = true,
                        isPaired = true,
                        deviceType = deviceType(deviceClass),
                        batteryPercent = null, // No public Android API for remote Bluetooth battery level.
                        profile = profileLabel(deviceClass)
                    )
                }
                .toList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isDeviceConnected(manager: BluetoothManager, device: BluetoothDevice): Boolean {
        return try {
            manager.getConnectionState(device, BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                manager.getConnectionState(device, BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED ||
                manager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED ||
                manager.getConnectionState(device, BluetoothProfile.HEALTH) == BluetoothProfile.STATE_CONNECTED
        } catch (_: SecurityException) {
            false
        }
    }

    private fun deviceType(majorClass: Int): String = when (majorClass) {
        1024 -> "Audio / Headphones / Speaker"
        1792 -> "Wearable / Smartwatch"
        1280 -> "Input Device / Keyboard / Mouse"
        512 -> "Phone / Tablet"
        else -> "Bluetooth Peripheral"
    }

    private fun profileLabel(majorClass: Int): String =
        if (majorClass == 1024) "A2DP / HFP" else "HID / Generic"
}
