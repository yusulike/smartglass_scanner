package com.windyflow.smartglassscanner

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.windyflow.smartglassscanner.ui.gate.GateScreeningScreen
import com.windyflow.smartglassscanner.ui.main.MainScreen
import com.windyflow.smartglassscanner.ui.settings.SettingsScreen

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
