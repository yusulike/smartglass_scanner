package com.windyflow.glassesdetector.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.windyflow.glassesdetector.R
import com.windyflow.glassesdetector.ble.BleScanMode
import com.windyflow.glassesdetector.ble.BleSignature
import com.windyflow.glassesdetector.ble.ConfidenceLevel
import com.windyflow.glassesdetector.ble.DetectedDevice
import com.windyflow.glassesdetector.ui.theme.SmartGlassesDetectorTheme
import org.junit.Rule
import org.junit.Test

/** UI tests for [MainScreen]. */
class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun permissionDeniedState_showsPermissionPromptWithoutLocationRequirement() {
    composeTestRule.setContent {
      SmartGlassesDetectorTheme {
        MainScreen(
          state =
            MainDashboardState(
              hasScanPermission = false,
              hasBleHardware = true,
              isBluetoothEnabled = true
            ),
          onToggleScan = {},
          onRequestPermissions = {},
          onOpenAppSettings = {},
          onOpenBluetoothSettings = {},
          onIgnoreDevice = {},
          onNavigateToSettings = {}
        )
      }
    }

    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.permission_missing_title))
      .assertExists()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.action_request_permission))
      .assertExists()
  }

  @Test
  fun detectedDevice_displaysLabelConfidenceAndIgnoreButton() {
    val device =
      DetectedDevice(
        key = "AA:BB:CC:DD:EE:FF",
        address = "AA:BB:CC:DD:EE:FF",
        deviceName = "Ray-Ban Meta",
        signature =
          BleSignature(
            id = "meta_rayban_tech",
            companyId = "0x058E",
            label = "Ray-Ban Meta (Meta Platforms Tech)",
            payloadAscii = "META_RB_GLASS",
            baseConfidence = ConfidenceLevel.HIGH
          ),
        confidence = ConfidenceLevel.HIGH,
        smoothedRssi = -64.0,
        rawRssi = -64,
        observationCount = 1,
        firstSeenMs = 1_000L,
        lastSeenMs = 1_000L,
        isAlertActive = true
      )

    composeTestRule.setContent {
      SmartGlassesDetectorTheme {
        MainScreen(
          state =
            MainDashboardState(
              isServiceRunning = true,
              isScanning = true,
              scanMode = BleScanMode.BALANCED,
              hasScanPermission = true,
              hasNotificationPermission = true,
              isBluetoothEnabled = true,
              detectedDevices = listOf(device)
            ),
          onToggleScan = {},
          onRequestPermissions = {},
          onOpenAppSettings = {},
          onOpenBluetoothSettings = {},
          onIgnoreDevice = {},
          onNavigateToSettings = {}
        )
      }
    }

    composeTestRule.onNodeWithText("Ray-Ban Meta (Meta Platforms Tech)").assertExists()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.confidence_badge_high))
      .assertExists()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.action_ignore))
      .assertExists()
  }

  @Test
  fun mainScreen_showsConnectedDeviceBleLimitationDisclaimer() {
    composeTestRule.setContent {
      SmartGlassesDetectorTheme {
        MainScreen(
          state = MainDashboardState(),
          onToggleScan = {},
          onRequestPermissions = {},
          onOpenAppSettings = {},
          onOpenBluetoothSettings = {},
          onIgnoreDevice = {},
          onNavigateToSettings = {}
        )
      }
    }

    composeTestRule
      .onNodeWithText(
        text = composeTestRule.activity.getString(R.string.banner_disclaimer),
        substring = true
      )
      .assertExists()
  }
}
