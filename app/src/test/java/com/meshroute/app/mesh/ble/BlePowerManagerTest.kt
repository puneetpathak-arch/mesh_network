package com.meshroute.app.mesh.ble

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings
import org.junit.Assert.*
import org.junit.Test

class BlePowerManagerTest {

    @Test
    fun testPowerProfileMapping() {
        val fullProfile = PowerProfile(
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        )

        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, fullProfile.scanMode)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, fullProfile.advertiseMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, fullProfile.advertiseTxPower)

        val saverProfile = PowerProfile(
            scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_LOW
        )

        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, saverProfile.scanMode)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, saverProfile.advertiseMode)
    }
}
