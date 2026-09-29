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

object BluetoothHelper {

    fun getBluetoothDevices(context: Context): List<BluetoothDeviceItem> {
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        if (!btGranted) {
            return emptyList()
        }

        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return emptyList()

            if (!adapter.isEnabled) return emptyList()

            val pairedDevices = adapter.bondedDevices ?: emptySet()
            val result = mutableListOf<BluetoothDeviceItem>()

            for (device in pairedDevices) {
                val name = try { device.name ?: "Unknown Device" } catch (_: SecurityException) { "Bluetooth Device" }
                val address = device.address ?: "00:00:00:00:00:00"
                val deviceClass = device.bluetoothClass?.majorDeviceClass ?: 0

                val deviceType = when (deviceClass) {
                    1024 -> "Audio / Headphones / Speaker"
                    1792 -> "Wearable / Smartwatch"
                    1280 -> "Input Device / Keyboard / Mouse"
                    512 -> "Phone / Tablet"
                    else -> "Bluetooth Peripheral"
                }

                // Check battery level via hidden Battery level API if supported (reflection)
                var batteryLevel: Int? = null
                try {
                    val method = device.javaClass.getMethod("getBatteryLevel")
                    val level = method.invoke(device) as? Int
                    if (level != null && level in 0..100) {
                        batteryLevel = level
                    }
                } catch (_: Exception) {}

                val isConnected = isDeviceConnected(bluetoothManager, device)

                result.add(
                    BluetoothDeviceItem(
                        name = name,
                        address = address,
                        isConnected = isConnected,
                        isPaired = true,
                        deviceType = deviceType,
                        batteryPercent = batteryLevel,
                        profile = if (deviceClass == 1024) "A2DP / HFP" else "HID / Generic"
                    )
                )
            }
            result
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isDeviceConnected(manager: BluetoothManager?, device: BluetoothDevice): Boolean {
        if (manager == null) return false
        return try {
            val a2dp = manager.getConnectionState(device, BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
            val headset = manager.getConnectionState(device, BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            val gatt = manager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
            a2dp || headset || gatt
        } catch (_: Exception) {
            false
        }
    }
}
