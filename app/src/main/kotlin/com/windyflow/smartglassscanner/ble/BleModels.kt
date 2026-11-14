package com.windyflow.smartglassscanner.ble

import kotlinx.serialization.Serializable

/** Confidence classification for a matched smart-glasses BLE signature. */
@Serializable
enum class ConfidenceLevel {
  HIGH,
  MEDIUM,
  LOW
}

/** Functional hardware category of detected smart glasses. */
@Serializable
enum class GlassesCategory {
  /** Eyewear that includes (or may include) an integrated camera. */
  CAMERA,
  /** Head-up display or audio-only smart eyewear without an onboard camera. */
  DISPLAY_AR
}

/**
 * High-level detector operating profile:
 * - [BACKGROUND_GUARD]: everyday continuous monitoring (`BALANCED` in foreground, `LOW_POWER` in background)
 * - [GATE_SCREENING]: foreground-only ultra-close-range (`<= 1m`) exam/security gate screening (`LOW_LATENCY`)
 */
enum class ScanProfile {
  BACKGROUND_GUARD,
  GATE_SCREENING
}

/**
 * BLE scan mode selected from [ScanProfile] and foreground visibility:
 * - [LOW_POWER]: background standby
 * - [BALANCED]: foreground regular dashboard
 * - [LOW_LATENCY]: foreground gate screening
 */
enum class BleScanMode {
  BALANCED,
  LOW_POWER,
  LOW_LATENCY
}

/** Three-tier visual/haptic screening verdict for [ScanProfile.GATE_SCREENING]. */
enum class GateStatus {
  CLEAR,
  CAUTION,
  ALERT
}

/** Result emitted by [BleSignalFilter.processGateScreening] for exam/gate screening UI. */
data class GateResult(
  val status: GateStatus = GateStatus.CLEAR,
  val device: DetectedDevice? = null,
  val observedDevice: DetectedDevice? = null,
  val matchedSignature: BleSignature? = null,
  val rawRssi: Int? = null,
  val effectiveRssi: Double? = null,
  val shouldVibrate: Boolean = false,
  val vibrationNonce: Long = 0L,
  val timestampMs: Long = 0L
) {
  companion object {
    fun clear(timestampMs: Long = 0L): GateResult = GateResult(timestampMs = timestampMs)
  }
}

/**
 * Smart-glasses BLE signature entry loaded from `assets/signatures.json` or added
 * by the user as a custom 16-bit Company ID.
 */
@Serializable
data class BleSignature(
  val id: String,
  val companyId: String? = null,
  val label: String,
  val payloadAscii: String? = null,
  val payloadHex: String? = null,
  val serviceUuid: String? = null,
  val serviceDataHex: String? = null,
  val deviceNamePrefix: String? = null,
  val deviceNamePatterns: List<String>? = null,
  val category: GlassesCategory = GlassesCategory.CAMERA,
  val baseConfidence: ConfidenceLevel = ConfidenceLevel.LOW,
  val isCustom: Boolean = false
) {
  /** Returns `true` when this rule relies on device-name matching (`prefix` or `patterns`). */
  fun hasNameCriterion(): Boolean =
    !deviceNamePrefix.isNullOrBlank() || !deviceNamePatterns.isNullOrEmpty()
  /**
   * Parses [companyId] (e.g. `"0x058E"`) into an unsigned 16-bit integer.
   * Returns `null` when [companyId] is missing or malformed.
   */
  fun companyIdInt(): Int? {
    val raw = companyId?.trim()?.removePrefix("0x")?.removePrefix("0X") ?: return null
    if (raw.isEmpty() || raw.length > 4) return null
    return raw.toIntOrNull(16)
  }

  /** Parses [payloadHex] (e.g. `"4D455441"` or `"0x4D, 0x45"`) into raw bytes, or `null`. */
  fun payloadHexBytes(): ByteArray? = parseHexBytes(payloadHex)

  /** Parses [serviceDataHex] (e.g. `"413149010000"`) into raw bytes, or `null`. */
  fun serviceDataBytes(): ByteArray? = parseHexBytes(serviceDataHex)

  /** Primary identifier label for UI summaries. */
  fun displayIdentifier(): String =
    companyId
      ?: serviceUuid
      ?: deviceNamePrefix?.let { "Name: $it*" }
      ?: deviceNamePatterns?.takeIf { it.isNotEmpty() }?.joinToString(" / ") { "*$it*" }
      ?: ""

  /** Formatted secondary match pattern suffix for Settings & diagnostic screens. */
  fun displayPatternSummary(): String {
    val details =
      listOfNotNull(
        payloadAscii?.takeIf { it.isNotBlank() }?.let { "Payload: $it" },
        payloadHex?.takeIf { it.isNotBlank() }?.let { "Hex: $it" },
        serviceDataHex?.takeIf { it.isNotBlank() }?.let { "Service Data: $it" },
        deviceNamePrefix?.takeIf { companyId != null || serviceUuid != null }?.let { "Name: $it*" }
      )
    return if (details.isEmpty()) "" else " · ${details.joinToString(" · ")}"
  }

  /**
   * Flexible N-of-M observation threshold within the sliding window:
   * - `HIGH` confidence signatures (e.g. `0x058E + META_RB_GLASS`, `0x0D53`, HeyCyan/Rokid UUID)
   *   confirm on 1 observation so short reconnection bursts are not missed.
   * - `MEDIUM` confidence signatures (e.g. Snap Spectacles) confirm on 2 observations.
   * - `LOW` confidence / generic Company IDs confirm on 3 observations to suppress false positives.
   */
  fun requiredObservations(): Int =
    when (baseConfidence) {
      ConfidenceLevel.HIGH -> 1
      ConfidenceLevel.MEDIUM -> 2
      ConfidenceLevel.LOW -> 3
    }

  companion object {
    private val hexSepRegex = Regex("(?:0[xX]|[\\s,:-])+")

    /** Decodes an even-length hex string (allowing `0x`, spaces, commas, colons) into bytes. */
    fun parseHexBytes(raw: String?): ByteArray? {
      val trimmed = raw?.trim() ?: return null
      if (trimmed.isEmpty()) return null
      val cleaned = hexSepRegex.replace(trimmed, "")
      if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
      val result = ByteArray(cleaned.length / 2)
      for (i in result.indices) {
        val byteVal = cleaned.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
        result[i] = byteVal.toByte()
      }
      return result
    }
  }
}

@Serializable
data class SignatureDatabase(
  val version: Int = 1,
  val signatures: List<BleSignature> = emptyList()
)

/**
 * Hardware-independent representation of a BLE advertisement packet so signature matching
 * and signal filtering can be unit-tested on the JVM without Android's final `ScanResult`.
 */
data class BleAdvertisement(
  val address: String,
  val deviceName: String? = null,
  val rssi: Int,
  val manufacturerData: Map<Int, ByteArray> = emptyMap(),
  val serviceUuids: List<String> = emptyList(),
  val serviceData: Map<String, ByteArray> = emptyMap(),
  val timestampMs: Long
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is BleAdvertisement) return false
    if (address != other.address) return false
    if (deviceName != other.deviceName) return false
    if (rssi != other.rssi) return false
    if (serviceUuids != other.serviceUuids) return false
    if (timestampMs != other.timestampMs) return false
    if (manufacturerData.keys != other.manufacturerData.keys) return false
    if (!manufacturerData.all { (key, value) -> value.contentEquals(other.manufacturerData[key]) }) {
      return false
    }
    if (serviceData.keys != other.serviceData.keys) return false
    return serviceData.all { (key, value) -> value.contentEquals(other.serviceData[key]) }
  }

  override fun hashCode(): Int {
    var result = address.hashCode()
    result = 31 * result + (deviceName?.hashCode() ?: 0)
    result = 31 * result + rssi
    result = 31 * result + serviceUuids.hashCode()
    result = 31 * result + timestampMs.hashCode()
    manufacturerData.entries.sortedBy { it.key }.forEach { (key, value) ->
      result = 31 * result + key
      result = 31 * result + value.contentHashCode()
    }
    serviceData.entries.sortedBy { it.key }.forEach { (key, value) ->
      result = 31 * result + key.hashCode()
      result = 31 * result + value.contentHashCode()
    }
    return result
  }
}

/** Represents a smart-glasses candidate currently tracked by [BleSignalFilter]. */
data class DetectedDevice(
  val key: String,
  val address: String,
  val deviceName: String?,
  val signature: BleSignature,
  val confidence: ConfidenceLevel,
  val smoothedRssi: Double,
  val rawRssi: Int,
  val observationCount: Int,
  val firstSeenMs: Long,
  val lastSeenMs: Long,
  val isAlertActive: Boolean
)
