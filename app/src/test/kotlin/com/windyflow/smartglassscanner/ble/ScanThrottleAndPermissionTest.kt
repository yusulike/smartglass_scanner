package com.windyflow.smartglassscanner.ble

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanThrottleAndPermissionTest {
  @Test
  fun throttleGate_enforcesMinimum6500MsSpacingBetweenStarts() {
    var nowMs = 10_000L
    val gate = ScanThrottleGate(minRestartIntervalMs = 6_500L, clockMs = { nowMs })

    // First start allowed immediately
    assertEquals(0L, gate.tryAcquireStartDelayMs())

    // 2 seconds later -> must wait 4500ms
    nowMs += 2_000L
    assertEquals(4_500L, gate.tryAcquireStartDelayMs())

    // 4.5 seconds later (6.5s total) -> allowed immediately
    nowMs += 4_500L
    assertEquals(0L, gate.tryAcquireStartDelayMs())
  }

  @Test
  fun permissionHelper_neverRequestsFineLocationOnApi31Plus() {
    val api31 = PermissionHelper.requiredRuntimePermissions(Build.VERSION_CODES.S)
    assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN), api31)
    assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in api31)

    val api33 = PermissionHelper.requiredRuntimePermissions(Build.VERSION_CODES.TIRAMISU)
    assertEquals(
      listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.POST_NOTIFICATIONS),
      api33
    )
    assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in api33)

    val api30 = PermissionHelper.requiredRuntimePermissions(Build.VERSION_CODES.R)
    assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), api30)
    assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in api30)
  }

  @Test
  fun resolveScanMode_switchesBetweenBalancedLowPowerAndLowLatency() {
    assertEquals(
      BleScanMode.BALANCED,
      SmartGlassesScanner.resolveScanMode(ScanProfile.BACKGROUND_GUARD, isForegroundUi = true)
    )
    assertEquals(
      BleScanMode.LOW_POWER,
      SmartGlassesScanner.resolveScanMode(ScanProfile.BACKGROUND_GUARD, isForegroundUi = false)
    )
    assertEquals(
      BleScanMode.LOW_LATENCY,
      SmartGlassesScanner.resolveScanMode(ScanProfile.GATE_SCREENING, isForegroundUi = true)
    )
    assertEquals(
      BleScanMode.LOW_POWER,
      SmartGlassesScanner.resolveScanMode(ScanProfile.GATE_SCREENING, isForegroundUi = false)
    )
  }

  @Test
  fun shouldUseUnfilteredForegroundScan_onlyWhenForegroundAndNameRulesPresent() {
    val cidOnly = listOf(BleSignature(id = "meta", companyId = "0x058E", label = "Meta"))
    val withName = cidOnly + BleSignature(id = "xreal", label = "XREAL", deviceNamePatterns = listOf("XREAL"))

    assertFalse(SmartGlassesScanner.shouldUseUnfilteredForegroundScan(cidOnly, BleScanMode.BALANCED))
    assertFalse(SmartGlassesScanner.shouldUseUnfilteredForegroundScan(withName, BleScanMode.LOW_POWER))
    assertTrue(SmartGlassesScanner.shouldUseUnfilteredForegroundScan(withName, BleScanMode.BALANCED))
    assertTrue(SmartGlassesScanner.shouldUseUnfilteredForegroundScan(withName, BleScanMode.LOW_LATENCY))
  }
}
