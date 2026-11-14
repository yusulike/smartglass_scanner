package com.windyflow.smartglassscanner.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.windyflow.smartglassscanner.R
import com.windyflow.smartglassscanner.data.AppSkin
import com.windyflow.smartglassscanner.data.ThemeMode
import com.windyflow.smartglassscanner.ui.theme.SmartGlassesDetectorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests for skin and theme mode controls in [SettingsScreen]. */
class SettingsScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun appearanceSection_displaysAndInvokesThemeModeAndSkinCallbacks() {
    var selectedMode: ThemeMode? = null
    var selectedSkin: AppSkin? = null

    composeTestRule.setContent {
      SmartGlassesDetectorTheme {
        SettingsScreen(
          state = SettingsUiState(),
          snackbarHostState = SnackbarHostState(),
          onBack = {},
          onThemeModeChange = { selectedMode = it },
          onAppSkinChange = { selectedSkin = it },
          onRssiThresholdChange = {},
          onCooldownSecondsChange = {},
          onCustomHexInputChange = {},
          onAddCustomHexId = {},
          onRemoveCustomHexId = {},
          onRemoveWhitelistEntry = {},
          onExportLogs = {},
          onClearLogs = {}
        )
      }
    }

    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.settings_section_appearance))
      .assertExists()

    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.theme_mode_dark))
      .performClick()
    assertEquals(ThemeMode.DARK, selectedMode)

    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.skin_emerald))
      .performClick()
    assertEquals(AppSkin.EMERALD, selectedSkin)
  }
}
