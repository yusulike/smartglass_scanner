package com.windyflow.glassesdetector

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.windyflow.glassesdetector.ui.gate.GateScreeningScreen
import com.windyflow.glassesdetector.ui.main.MainScreen
import com.windyflow.glassesdetector.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigateToSettings = { backStack.add(Settings) },
            onNavigateToGateScreening = { backStack.add(GateScreening) }
          )
        }
        entry<GateScreening> {
          GateScreeningScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<Settings> {
          SettingsScreen(onBack = { backStack.removeLastOrNull() })
        }
      }
  )
}
