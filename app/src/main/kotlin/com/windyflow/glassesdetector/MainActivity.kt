package com.windyflow.glassesdetector

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windyflow.glassesdetector.data.DetectionStateHolder
import com.windyflow.glassesdetector.data.DetectorSettings
import com.windyflow.glassesdetector.data.WhitelistRepository
import com.windyflow.glassesdetector.ui.theme.SmartGlassesDetectorTheme
import com.windyflow.glassesdetector.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DetectionStateHolder.initialize(applicationContext)

    enableEdgeToEdge()
    setContent {
      val settingsRepository = remember { WhitelistRepository(applicationContext) }
      val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = DetectorSettings())
      val darkTheme = resolveDarkTheme(settings.themeMode)

      LaunchedEffect(darkTheme) {
        val barStyle =
          if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
          }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
      }

      SmartGlassesDetectorTheme(
        themeMode = settings.themeMode,
        appSkin = settings.appSkin
      ) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainNavigation()
        }
      }
    }
  }
}
