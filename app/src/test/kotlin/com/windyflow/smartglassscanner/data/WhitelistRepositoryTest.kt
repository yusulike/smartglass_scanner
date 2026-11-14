package com.windyflow.smartglassscanner.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistRepositoryTest {
  @Test
  fun settingsPersistThresholdCooldownAndWhitelist() = runTest {
    val dataStore = InMemoryPreferencesStore()
    val repo = WhitelistRepository(dataStore = dataStore, clockMs = { 42_000L })

    val initial = repo.settings.first()
    assertEquals(DetectorSettings.DEFAULT_RSSI_THRESHOLD_DBM, initial.rssiThresholdDbm)
    assertEquals(DetectorSettings.DEFAULT_COOLDOWN_SECONDS, initial.cooldownSeconds)
    assertTrue(initial.whitelist.isEmpty())

    repo.setRssiThresholdDbm(-68)
    repo.setCooldownSeconds(25)
    repo.addToWhitelist("aa:bb:cc:dd:ee:ff", "My Ray-Ban")

    val updated = repo.settings.first()
    assertEquals(-68, updated.rssiThresholdDbm)
    assertEquals(25, updated.cooldownSeconds)
    assertEquals(setOf("AA:BB:CC:DD:EE:FF"), updated.whitelistedKeys)
    assertEquals("My Ray-Ban", updated.whitelist.single().label)

    repo.removeFromWhitelist("AA:BB:CC:DD:EE:FF")
    assertTrue(repo.settings.first().whitelist.isEmpty())
  }

  @Test
  fun settingsPersistThemeModeAndAppSkin() = runTest {
    val dataStore = InMemoryPreferencesStore()
    val repo = WhitelistRepository(dataStore = dataStore)

    val initial = repo.settings.first()
    assertEquals(ThemeMode.SYSTEM, initial.themeMode)
    assertEquals(AppSkin.OCEAN_BLUE, initial.appSkin)

    repo.setThemeMode(ThemeMode.DARK)
    repo.setAppSkin(AppSkin.EMERALD)

    val updated = repo.settings.first()
    assertEquals(ThemeMode.DARK, updated.themeMode)
    assertEquals(AppSkin.EMERALD, updated.appSkin)
  }

  private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
      mutex.withLock {
        val next = transform(state.value)
        state.value = next
        next
      }
  }
}
