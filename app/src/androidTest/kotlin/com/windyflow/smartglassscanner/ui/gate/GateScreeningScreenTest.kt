package com.windyflow.smartglassscanner.ui.gate

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.windyflow.smartglassscanner.R
import com.windyflow.smartglassscanner.ble.BleScanMode
import com.windyflow.smartglassscanner.ble.BleSignature
import com.windyflow.smartglassscanner.ble.ConfidenceLevel
import com.windyflow.smartglassscanner.ble.DetectedDevice
import com.windyflow.smartglassscanner.ble.GateResult
import com.windyflow.smartglassscanner.ble.GateStatus
import com.windyflow.smartglassscanner.ui.theme.SmartGlassesDetectorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GateScreeningScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun alertState_displaysManufacturerRssiAndResetButton() {
    var resetClicked = false
    composeTestRule.setContent {
      SmartGlassesDetectorTheme {
        GateScreeningScreen(
          state =
            GateScreeningUiState(
              isServiceRunning = true,
              isScanning = true,
              scanMode = BleScanMode.LOW_LATENCY,
              hasScanPermission = true,
              isBluetoothEnabled = true,
              gateResult =
                GateResult(
                  status = GateStatus.ALERT,
                  rawRssi = -48,
                  effectiveRssi = -48.0,
                  device =
                    DetectedDevice(
                      key = "AA:BB:CC:DD:EE:FF",
                      address = "AA:BB:CC:DD:EE:FF",
                      deviceName = "RB Meta",
                      signature =
                        BleSignature(
                          id = "meta_rayban_tech",
                          companyId = "0x058E",
                          label = "Ray-Ban Meta (Meta Platforms Tech)",
                          payloadAscii = "META_RB_GLASS",
                          baseConfidence = ConfidenceLevel.HIGH
                        ),
                      confidence = ConfidenceLevel.HIGH,
                      smoothedRssi = -48.0,
                      rawRssi = -48,
                      observationCount = 1,
                      firstSeenMs = 1_000L,
                      lastSeenMs = 1_000L,
                      isAlertActive = true
                    )
                )
            ),
          onBack = {},
          onResetNextExaminee = { resetClicked = true },
          onRequestPermissions = {}
        )
      }
    }

    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.gate_alert_title))
      .assertExists()
    composeTestRule.onNodeWithText("Ray-Ban Meta (Meta Platforms Tech)").assertExists()
    composeTestRule.onNodeWithText("-48 dBm").assertExists()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.action_next_examinee))
      .performClick()
    assertTrue(resetClicked)
  }
}
