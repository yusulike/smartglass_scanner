package com.windyflow.smartglassscanner.ble

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.windyflow.smartglassscanner.data.detectorDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Pure-Kotlin JSON parser and hex-ID normalizer for smart-glasses BLE signatures. */
object SignatureJsonParser {
  private val json = Json { ignoreUnknownKeys = true }
  private val hexRegex = Regex("^(?:0[xX])?([0-9A-Fa-f]{1,4})$")

  /** Parses the contents of `signatures.json` into a [SignatureDatabase]. */
  fun parse(rawJson: String): SignatureDatabase = json.decodeFromString<SignatureDatabase>(rawJson)

  /**
   * Normalizes user-entered hex company ID (`0000`..`FFFF`, optional `0x` prefix)
   * into uppercase 4-digit `0xXXXX` format, or returns `null` if invalid.
   */
  fun normalizeHexCompanyId(input: String): String? {
    val match = hexRegex.matchEntire(input.trim()) ?: return null
    val digits = match.groupValues[1].uppercase().padStart(4, '0')
    return "0x$digits"
  }

  /** Converts a normalized custom hex ID into a [BleSignature] with `LOW` base confidence. */
  fun customSignatureFor(normalizedHex: String): BleSignature =
    BleSignature(
      id = "custom_${normalizedHex.lowercase()}",
      companyId = normalizedHex,
      label = "Custom Company ID ($normalizedHex)",
      payloadAscii = null,
      serviceUuid = null,
      baseConfidence = ConfidenceLevel.LOW,
      isCustom = true
    )
}

/**
 * Loads bundled BLE signatures from `assets/signatures.json` and merges them with
 * user-defined custom Company IDs stored in Jetpack DataStore.
 */
class SignatureRepository(
  private val dataStore: DataStore<Preferences>,
  private val bundledLoader: () -> List<BleSignature>
) {
  constructor(context: Context) : this(
    dataStore = context.applicationContext.detectorDataStore,
    bundledLoader = {
      context.applicationContext.assets
        .open("signatures.json")
        .bufferedReader()
        .use { reader -> SignatureJsonParser.parse(reader.readText()).signatures }
    }
  )

  private val customCompanyIdsKey = stringSetPreferencesKey("custom_company_ids")
  private val includeGenericLowSignaturesKey =
    booleanPreferencesKey("include_generic_low_signatures")

  /** Lazily loaded built-in signatures from `assets/signatures.json`. */
  val bundledSignatures: List<BleSignature> by lazy { bundledLoader() }

  /** Whether built-in generic `LOW`-confidence signatures (`0x01AB`, `0x05D6`) are active. */
  val includeGenericLowSignatures: Flow<Boolean> =
    dataStore.data.map { prefs ->
      prefs[includeGenericLowSignaturesKey] ?: DEFAULT_INCLUDE_GENERIC_LOW_SIGNATURES
    }

  /** Sorted list of normalized custom hex company IDs (`0xXXXX`). */
  val customHexIds: Flow<List<String>> =
    dataStore.data.map { prefs ->
      prefs[customCompanyIdsKey]?.sorted().orEmpty()
    }

  /** Reactive merged list of active bundled signatures followed by user custom signatures. */
  val activeSignatures: Flow<List<BleSignature>> =
    combine(customHexIds, includeGenericLowSignatures) { customIds, includeGenericLow ->
      val activeBundled =
        if (includeGenericLow) {
          bundledSignatures
        } else {
          bundledSignatures.filterNot(BleSignature::isBuiltInGenericLow)
        }
      val customSignatures = customIds.map(SignatureJsonParser::customSignatureFor)
      activeBundled + customSignatures
    }

  /** Enables or disables built-in generic `LOW`-confidence signatures (`0x01AB`, `0x05D6`). */
  suspend fun setIncludeGenericLowSignatures(enabled: Boolean) {
    dataStore.edit { prefs ->
      prefs[includeGenericLowSignaturesKey] = enabled
    }
  }

  /** Exception thrown when a user-supplied custom Company ID cannot be normalized as 1–4 hex digits. */
  class InvalidHexCompanyIdException :
    IllegalArgumentException("Only 1-4 hex digits in 0000-FFFF are allowed.")

  /**
   * Validates, normalizes, and persists a custom Company ID (`0000`~`FFFF`).
   * Returns [Result.success] with the normalized ID or [Result.failure] on invalid input.
   */
  suspend fun addCustomHexId(hex: String): Result<String> {
    val normalized = SignatureJsonParser.normalizeHexCompanyId(hex)
      ?: return Result.failure(InvalidHexCompanyIdException())

    dataStore.edit { prefs ->
      val current = prefs[customCompanyIdsKey].orEmpty()
      prefs[customCompanyIdsKey] = current + normalized
    }
    return Result.success(normalized)
  }

  /** Removes a previously saved custom Company ID from DataStore. */
  suspend fun removeCustomHexId(hex: String) {
    val normalized = SignatureJsonParser.normalizeHexCompanyId(hex) ?: hex.trim()
    dataStore.edit { prefs ->
      val current = prefs[customCompanyIdsKey].orEmpty()
      prefs[customCompanyIdsKey] = current - normalized
    }
  }

  companion object {
    const val DEFAULT_INCLUDE_GENERIC_LOW_SIGNATURES = true
  }
}
