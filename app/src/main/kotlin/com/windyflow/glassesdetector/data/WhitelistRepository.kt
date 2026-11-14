package com.windyflow.glassesdetector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WhitelistEntry(
  val key: String,
  val label: String,
  val addedAtMs: Long = 0L
)

/** App brightness mode preference overriding or following the system setting. */
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromStorage(raw: String?): ThemeMode =
      entries.firstOrNull { it.name == raw } ?: SYSTEM
  }
}

/** Curated color skin presets available in Settings. */
enum class AppSkin {
  OCEAN_BLUE,
  EMERALD,
  AMETHYST,
  AMBER_GUARD,
  MONOCHROME,
  DYNAMIC;

  companion object {
    fun fromStorage(raw: String?): AppSkin =
      entries.firstOrNull { it.name == raw } ?: OCEAN_BLUE
  }
}

data class DetectorSettings(
  val rssiThresholdDbm: Int = DEFAULT_RSSI_THRESHOLD_DBM,
  val cooldownSeconds: Int = DEFAULT_COOLDOWN_SECONDS,
  val whitelist: List<WhitelistEntry> = emptyList(),
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val appSkin: AppSkin = AppSkin.OCEAN_BLUE
) {
  val whitelistedKeys: Set<String>
    get() = whitelist.mapTo(HashSet()) { it.key }

  companion object {
    const val DEFAULT_RSSI_THRESHOLD_DBM = -75
    const val MIN_RSSI_THRESHOLD_DBM = -95
    const val MAX_RSSI_THRESHOLD_DBM = -50

    const val DEFAULT_COOLDOWN_SECONDS = 10
    const val MIN_COOLDOWN_SECONDS = 5
    const val MAX_COOLDOWN_SECONDS = 60
  }
}

/**
 * DataStore-backed repository managing RSSI alert threshold, notification cooldown,
 * and user-ignored device keys (whitelist).
 */
class WhitelistRepository(
  private val dataStore: DataStore<Preferences>,
  private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
  constructor(context: Context) : this(context.applicationContext.detectorDataStore)

  private val json = Json { ignoreUnknownKeys = true }

  private val rssiThresholdKey = intPreferencesKey("rssi_threshold_dbm")
  private val cooldownSecondsKey = intPreferencesKey("cooldown_seconds")
  private val whitelistJsonKey = stringPreferencesKey("whitelist_entries_json")
  private val themeModeKey = stringPreferencesKey("theme_mode")
  private val appSkinKey = stringPreferencesKey("app_skin")

  val settings: Flow<DetectorSettings> =
    dataStore.data.map { prefs ->
      val threshold =
        (prefs[rssiThresholdKey] ?: DetectorSettings.DEFAULT_RSSI_THRESHOLD_DBM)
          .coerceIn(
            DetectorSettings.MIN_RSSI_THRESHOLD_DBM,
            DetectorSettings.MAX_RSSI_THRESHOLD_DBM
          )
      val cooldown =
        (prefs[cooldownSecondsKey] ?: DetectorSettings.DEFAULT_COOLDOWN_SECONDS)
          .coerceIn(
            DetectorSettings.MIN_COOLDOWN_SECONDS,
            DetectorSettings.MAX_COOLDOWN_SECONDS
          )
      val entries = decodeWhitelist(prefs[whitelistJsonKey])
      DetectorSettings(
        rssiThresholdDbm = threshold,
        cooldownSeconds = cooldown,
        whitelist = entries,
        themeMode = ThemeMode.fromStorage(prefs[themeModeKey]),
        appSkin = AppSkin.fromStorage(prefs[appSkinKey])
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
  }

  suspend fun setAppSkin(skin: AppSkin) {
    dataStore.edit { prefs -> prefs[appSkinKey] = skin.name }
  }

  suspend fun setRssiThresholdDbm(value: Int) {
    val clamped =
      value.coerceIn(
        DetectorSettings.MIN_RSSI_THRESHOLD_DBM,
        DetectorSettings.MAX_RSSI_THRESHOLD_DBM
      )
    dataStore.edit { prefs -> prefs[rssiThresholdKey] = clamped }
  }

  suspend fun setCooldownSeconds(seconds: Int) {
    val clamped =
      seconds.coerceIn(
        DetectorSettings.MIN_COOLDOWN_SECONDS,
        DetectorSettings.MAX_COOLDOWN_SECONDS
      )
    dataStore.edit { prefs -> prefs[cooldownSecondsKey] = clamped }
  }

  suspend fun addToWhitelist(key: String, label: String) {
    val normalizedKey = key.trim().uppercase()
    if (normalizedKey.isEmpty()) return

    dataStore.edit { prefs ->
      val current = decodeWhitelist(prefs[whitelistJsonKey]).toMutableList()
      current.removeAll { it.key == normalizedKey }
      current.add(
        0,
        WhitelistEntry(
          key = normalizedKey,
          label = label.ifBlank { normalizedKey },
          addedAtMs = clockMs()
        )
      )
      prefs[whitelistJsonKey] = json.encodeToString(current)
    }
  }

  suspend fun removeFromWhitelist(key: String) {
    val normalizedKey = key.trim().uppercase()
    dataStore.edit { prefs ->
      val current = decodeWhitelist(prefs[whitelistJsonKey]).filterNot { it.key == normalizedKey }
      prefs[whitelistJsonKey] = json.encodeToString(current)
    }
  }

  private fun decodeWhitelist(raw: String?): List<WhitelistEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<WhitelistEntry>>(raw) }.getOrDefault(emptyList())
  }
}
