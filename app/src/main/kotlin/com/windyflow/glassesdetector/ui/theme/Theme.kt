/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.windyflow.glassesdetector.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.windyflow.glassesdetector.data.AppSkin
import com.windyflow.glassesdetector.data.ThemeMode

private data class SkinPalette(
  val light: ColorScheme,
  val dark: ColorScheme,
  val swatchLight: Color,
  val swatchDark: Color
)

private val OceanBluePalette =
  SkinPalette(
    light =
      lightColorScheme(
        primary = primaryLight,
        primaryContainer = primaryContainerLight,
        secondary = secondaryLight,
        tertiary = tertiaryLight
      ),
    dark =
      darkColorScheme(
        primary = primaryDark,
        primaryContainer = primaryContainerDark,
        secondary = secondaryDark,
        tertiary = tertiaryDark
      ),
    swatchLight = primaryLight,
    swatchDark = primaryDark
  )

private val EmeraldPalette =
  SkinPalette(
    light =
      lightColorScheme(
        primary = Color(0xFF006B52),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF8EF7D2),
        onPrimaryContainer = Color(0xFF002117),
        secondary = Color(0xFF4B635A),
        secondaryContainer = Color(0xFFCDE8DC),
        onSecondaryContainer = Color(0xFF072018),
        tertiary = Color(0xFF3E6375),
        tertiaryContainer = Color(0xFFC2E8FD),
        background = Color(0xFFF5FBF7),
        surface = Color(0xFFF5FBF7),
        surfaceContainerLow = Color(0xFFEFF5F1)
      ),
    dark =
      darkColorScheme(
        primary = Color(0xFF70DAB7),
        onPrimary = Color(0xFF003829),
        primaryContainer = Color(0xFF00513D),
        onPrimaryContainer = Color(0xFF8EF7D2),
        secondary = Color(0xFFB2CCC0),
        secondaryContainer = Color(0xFF344B42),
        onSecondaryContainer = Color(0xFFCDE8DC),
        tertiary = Color(0xFFA6CCE0),
        background = Color(0xFF0E1512),
        surface = Color(0xFF0E1512),
        surfaceContainerLow = Color(0xFF171D1A)
      ),
    swatchLight = Color(0xFF006B52),
    swatchDark = Color(0xFF70DAB7)
  )

private val AmethystPalette =
  SkinPalette(
    light =
      lightColorScheme(
        primary = Color(0xFF5A46A8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE5DEFF),
        onPrimaryContainer = Color(0xFF190063),
        secondary = Color(0xFF5F5C71),
        secondaryContainer = Color(0xFFE4DFF9),
        onSecondaryContainer = Color(0xFF1B192B),
        tertiary = Color(0xFF7B5268),
        tertiaryContainer = Color(0xFFFFD8EB),
        background = Color(0xFFFBF8FF),
        surface = Color(0xFFFBF8FF),
        surfaceContainerLow = Color(0xFFF4F1FA)
      ),
    dark =
      darkColorScheme(
        primary = Color(0xFFC9BEFF),
        onPrimary = Color(0xFF2B0E77),
        primaryContainer = Color(0xFF422D8E),
        onPrimaryContainer = Color(0xFFE5DEFF),
        secondary = Color(0xFFC8C3DC),
        secondaryContainer = Color(0xFF474459),
        onSecondaryContainer = Color(0xFFE4DFF9),
        tertiary = Color(0xFFECB8D1),
        background = Color(0xFF131218),
        surface = Color(0xFF131218),
        surfaceContainerLow = Color(0xFF1B1A21)
      ),
    swatchLight = Color(0xFF5A46A8),
    swatchDark = Color(0xFFC9BEFF)
  )

private val AmberGuardPalette =
  SkinPalette(
    light =
      lightColorScheme(
        primary = Color(0xFF8B4F00),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCBE),
        onPrimaryContainer = Color(0xFF2C1600),
        secondary = Color(0xFF725A43),
        secondaryContainer = Color(0xFFFEDDC0),
        onSecondaryContainer = Color(0xFF281806),
        tertiary = Color(0xFF596238),
        tertiaryContainer = Color(0xFFDDE7B2),
        background = Color(0xFFFFF8F4),
        surface = Color(0xFFFFF8F4),
        surfaceContainerLow = Color(0xFFFAF1EA)
      ),
    dark =
      darkColorScheme(
        primary = Color(0xFFFFB86E),
        onPrimary = Color(0xFF4A2800),
        primaryContainer = Color(0xFF6A3C00),
        onPrimaryContainer = Color(0xFFFFDCBE),
        secondary = Color(0xFFE1C1A4),
        secondaryContainer = Color(0xFF59432D),
        onSecondaryContainer = Color(0xFFFEDDC0),
        tertiary = Color(0xFFC1CB98),
        background = Color(0xFF17130E),
        surface = Color(0xFF17130E),
        surfaceContainerLow = Color(0xFF201B16)
      ),
    swatchLight = Color(0xFF8B4F00),
    swatchDark = Color(0xFFFFB86E)
  )

private val MonochromePalette =
  SkinPalette(
    light =
      lightColorScheme(
        primary = Color(0xFF333940),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDDE3EA),
        onPrimaryContainer = Color(0xFF13171B),
        secondary = Color(0xFF555B62),
        secondaryContainer = Color(0xFFE2E6EC),
        onSecondaryContainer = Color(0xFF1A1E23),
        tertiary = Color(0xFF5E5C64),
        background = Color(0xFFF8F9FA),
        surface = Color(0xFFF8F9FA),
        surfaceContainerLow = Color(0xFFF0F2F4)
      ),
    dark =
      darkColorScheme(
        primary = Color(0xFFC6CED6),
        onPrimary = Color(0xFF1C2126),
        primaryContainer = Color(0xFF3F464D),
        onPrimaryContainer = Color(0xFFE4E9EE),
        secondary = Color(0xFFB9C0C7),
        secondaryContainer = Color(0xFF393E44),
        onSecondaryContainer = Color(0xFFD8DEE4),
        tertiary = Color(0xFFC7C4CE),
        background = Color(0xFF111315),
        surface = Color(0xFF111315),
        surfaceContainerLow = Color(0xFF1A1C1E)
      ),
    swatchLight = Color(0xFF333940),
    swatchDark = Color(0xFFC6CED6)
  )

private fun paletteFor(skin: AppSkin): SkinPalette =
  when (skin) {
    AppSkin.OCEAN_BLUE, AppSkin.DYNAMIC -> OceanBluePalette
    AppSkin.EMERALD -> EmeraldPalette
    AppSkin.AMETHYST -> AmethystPalette
    AppSkin.AMBER_GUARD -> AmberGuardPalette
    AppSkin.MONOCHROME -> MonochromePalette
  }

/** Resolves whether dark mode should be active for the given [ThemeMode] preference. */
@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean =
  when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

/** Returns a representative preview swatch color for [skin] suitable for selector chips. */
@Composable
fun skinSwatchColor(skin: AppSkin, darkTheme: Boolean): Color {
  if (skin == AppSkin.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val ctx = LocalContext.current
    return (if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
  }
  val palette = paletteFor(skin)
  return if (darkTheme) palette.swatchDark else palette.swatchLight
}

@Composable
fun SmartGlassesDetectorTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  appSkin: AppSkin = AppSkin.OCEAN_BLUE,
  content: @Composable () -> Unit
) {
  val darkTheme = resolveDarkTheme(themeMode)
  val colorScheme =
    if (appSkin == AppSkin.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      val palette = paletteFor(appSkin)
      if (darkTheme) palette.dark else palette.light
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
