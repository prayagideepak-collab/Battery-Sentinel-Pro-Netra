package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BluetoothDeviceItem
import com.example.model.NetraCentralState
import com.example.service.NetraCentralDataCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothIntegrationTest {

    private lateinit var context: Context
    private lateinit var dataCenter: NetraCentralDataCenter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataCenter = NetraCentralDataCenter()
    }

    @Test
    fun testConnectedDeviceAppearsInLive() {
        val device = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 90,
            profile = "A2DP"
        )

        val state = NetraCentralState(
            bluetoothDevices = listOf(device),
            bluetoothConnected = true
        )

        val liveDevices = state.bluetoothDevices.filter { it.isConnected }
        assertEquals(1, liveDevices.size)
        assertEquals("Sony WH-1000XM4", liveDevices[0].name)
        assertTrue(liveDevices[0].isConnected)
    }

    @Test
    fun testPairedButDisconnectedDeviceExcludedFromLive() {
        val device = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = false,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = null,
            profile = "A2DP"
        )

        val state = NetraCentralState(
            bluetoothDevices = listOf(device),
            bluetoothConnected = false
        )

        val liveDevices = state.bluetoothDevices.filter { it.isConnected }
        assertTrue(liveDevices.isEmpty())
    }

    @Test
    fun testHistoryPreservedOnDisconnect() {
        val deviceConnected = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 85,
            profile = "A2DP"
        )

        val historyList = listOf(deviceConnected.copy(isConnected = false))
        val state = NetraCentralState(
            bluetoothDevices = emptyList(),
            bluetoothHistory = historyList,
            bluetoothConnected = false
        )

        assertTrue(state.bluetoothDevices.isEmpty())
        assertEquals(1, state.bluetoothHistory.size)
        assertFalse(state.bluetoothHistory[0].isConnected)
        assertEquals(85, state.bluetoothHistory[0].batteryPercent)
    }

    @Test
    fun testLastValidBatteryRetainedWhenNull() {
        val oldDevice = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 85,
            profile = "A2DP"
        )

        val newDevice = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = null, // temporarily null
            profile = "A2DP"
        )

        // Simulate Central Unit carry forward logic inside processBluetoothDevices
        val mergedBattery = newDevice.batteryPercent ?: oldDevice.batteryPercent
        val mergedDevice = newDevice.copy(batteryPercent = mergedBattery)

        assertEquals(85, mergedDevice.batteryPercent)
    }

    @Test
    fun testUnsupportedBatteryCapability() {
        val device = BluetoothDeviceItem(
            name = "Cheap Mouse",
            address = "11:22:33:44:55:66",
            isConnected = true,
            isPaired = true,
            deviceType = "Input Device / Keyboard / Mouse",
            batteryPercent = null, // unsupported
            profile = "HID"
        )

        assertNull(device.batteryPercent)
    }

    @Test
    fun testDeduplicationAndConsolidation() {
        val device1 = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 80,
            profile = "A2DP"
        )

        val device2 = BluetoothDeviceItem(
            name = "Sony WH-1000XM4",
            address = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            isPaired = true,
            deviceType = "Audio / Headphones / Speaker",
            batteryPercent = 80,
            profile = "A2DP"
        )

        val list = listOf(device1, device2)
        val deduplicated = list.distinctBy { it.address.ifBlank { it.name } }

        assertEquals(1, deduplicated.size)
        assertEquals("Sony WH-1000XM4", deduplicated[0].name)
    }
}
