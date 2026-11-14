package com.windyflow.glassesdetector.ble

/** Result of evaluating a single BLE advertisement through [BleSignalFilter]. */
data class FilterDecision(
  val matchedSignature: BleSignature?,
  val device: DetectedDevice?,
  val shouldNotify: Boolean,
  val isWhitelisted: Boolean = false
)

/**
 * Stateful BLE false-positive reduction engine implementing:
 * - Signature matching (Company ID + optional ASCII payload, or Service UUID)
 * - EMA RSSI smoothing (`0.3 * raw + 0.7 * prev`)
 * - Hysteresis (`entryThresholdDbm` / `entryThresholdDbm - hysteresisGapDbm`)
 * - Flexible N-of-M sliding window (`8s` window; `1` for HIGH, `2` for MEDIUM, `3` for LOW)
 * - Whitelist exclusion and per-device notification cooldown
 */
class BleSignalFilter(
  private val windowMs: Long = DEFAULT_WINDOW_MS,
  private val hysteresisGapDbm: Int = DEFAULT_HYSTERESIS_GAP_DBM,
  private val emaAlpha: Double = DEFAULT_EMA_ALPHA,
  private val staleDeviceMs: Long = DEFAULT_STALE_DEVICE_MS
) {
  private data class TrackedState(
    val key: String,
    var address: String,
    var deviceName: String?,
    var signature: BleSignature,
    var smoothedRssi: Double,
    var rawRssi: Int,
    val timestamps: ArrayDeque<Long>,
    val firstSeenMs: Long,
    var lastSeenMs: Long,
    var isAlertActive: Boolean,
    var lastNotifiedMs: Long?
  ) {
    fun toDetectedDevice(): DetectedDevice =
      DetectedDevice(
        key = key,
        address = address,
        deviceName = deviceName,
        signature = signature,
        confidence = signature.baseConfidence,
        smoothedRssi = smoothedRssi,
        rawRssi = rawRssi,
        observationCount = timestamps.size,
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
        isAlertActive = isAlertActive
      )
  }

  private val tracked = LinkedHashMap<String, TrackedState>()
  private val gateFastRssiByKey = HashMap<String, Double>()
  private val gateObservationsByKey = HashMap<String, Int>()
  private var activeGateResult: GateResult = GateResult.clear()
  private var lastGateVibrateMs: Long? = null
  private var gateVibrationNonce: Long = 0L

  @Synchronized
  fun process(
    advertisement: BleAdvertisement,
    signatures: List<BleSignature>,
    whitelistedKeys: Set<String> = emptySet(),
    entryThresholdDbm: Int = DEFAULT_ENTRY_THRESHOLD_DBM,
    cooldownMs: Long = DEFAULT_COOLDOWN_MS
  ): FilterDecision {
    val signature = matchSignature(advertisement, signatures)
      ?: return FilterDecision(matchedSignature = null, device = null, shouldNotify = false)

    val key = buildDeviceKey(advertisement, signature)
    if (key in whitelistedKeys) {
      tracked.remove(key)
      return FilterDecision(
        matchedSignature = signature,
        device = null,
        shouldNotify = false,
        isWhitelisted = true
      )
    }

    val now = advertisement.timestampMs
    val exitThresholdDbm = entryThresholdDbm - hysteresisGapDbm
    val existing = tracked[key]

    val state =
      if (existing == null) {
        TrackedState(
          key = key,
          address = advertisement.address,
          deviceName = advertisement.deviceName,
          signature = signature,
          smoothedRssi = advertisement.rssi.toDouble(),
          rawRssi = advertisement.rssi,
          timestamps = ArrayDeque<Long>().apply { addLast(now) },
          firstSeenMs = now,
          lastSeenMs = now,
          isAlertActive = false,
          lastNotifiedMs = null
        ).also { tracked[key] = it }
      } else {
        existing.apply {
          address = advertisement.address
          if (!advertisement.deviceName.isNullOrBlank()) {
            deviceName = advertisement.deviceName
          }
          this.signature = signature
          smoothedRssi = emaAlpha * advertisement.rssi + (1.0 - emaAlpha) * smoothedRssi
          rawRssi = advertisement.rssi
          lastSeenMs = now
          timestamps.addLast(now)
          pruneWindow(timestamps, now)
        }
      }

    val requiredCount = signature.requiredObservations()
    val inWindowCount = state.timestamps.size

    if (state.isAlertActive) {
      if (state.smoothedRssi < exitThresholdDbm) {
        state.isAlertActive = false
      }
    } else {
      if (state.smoothedRssi >= entryThresholdDbm && inWindowCount >= requiredCount) {
        state.isAlertActive = true
      }
    }

    val cooldownElapsed =
      state.lastNotifiedMs == null || (now - (state.lastNotifiedMs ?: 0L)) >= cooldownMs
    val shouldNotify = state.isAlertActive && cooldownElapsed
    if (shouldNotify) {
      state.lastNotifiedMs = now
    }

    return FilterDecision(
      matchedSignature = signature,
      device = state.toDetectedDevice(),
      shouldNotify = shouldNotify
    )
  }

  /** Returns active/tracked devices sorted by strongest signal, pruning whitelisted or stale items. */
  @Synchronized
  fun currentDevices(
    nowMs: Long,
    whitelistedKeys: Set<String> = emptySet()
  ): List<DetectedDevice> {
    val iterator = tracked.entries.iterator()
    while (iterator.hasNext()) {
      val (key, state) = iterator.next()
      if (key in whitelistedKeys || (nowMs - state.lastSeenMs) > staleDeviceMs) {
        iterator.remove()
      }
    }
    return tracked.values
      .map { it.toDetectedDevice() }
      .sortedWith(
        compareByDescending<DetectedDevice> { it.isAlertActive }
          .thenByDescending { it.smoothedRssi }
          .thenByDescending { it.lastSeenMs }
      )
  }

  @Synchronized
  fun removeDevice(key: String) {
    tracked.remove(key)
  }

  @Synchronized
  fun clear() {
    tracked.clear()
    resetGateScreening()
  }

  /**
   * Evaluates a BLE advertisement in [ScanProfile.GATE_SCREENING] mode:
   * - Requires `rawRssi >= gateThresholdDbm` (default `-55 dBm`, ~0.5–1m)
   * - Emits `ALERT` (red) for HIGH/MEDIUM signatures or `CAUTION` (amber) for LOW/generic IDs
   *   immediately without waiting for the 8-second sliding window
   * - Preserves active `ALERT`/`CAUTION` state on screen until [resetGateScreening] is invoked,
   *   never downgrading an active `ALERT` to `CAUTION`
   * - Triggers vibration at most once per [gateCooldownMs] (default `2000ms`)
   */
  @Synchronized
  fun processGateScreening(
    advertisement: BleAdvertisement,
    signatures: List<BleSignature>,
    whitelistedKeys: Set<String> = emptySet(),
    gateThresholdDbm: Int = DEFAULT_GATE_RSSI_THRESHOLD_DBM,
    gateCooldownMs: Long = DEFAULT_GATE_COOLDOWN_MS
  ): GateResult {
    val signature = matchSignature(advertisement, signatures)
      ?: return activeGateResult.copy(observedDevice = null, shouldVibrate = false)

    val key = buildDeviceKey(advertisement, signature)
    if (key in whitelistedKeys) {
      return activeGateResult.copy(observedDevice = null, shouldVibrate = false)
    }

    val now = advertisement.timestampMs
    val subThresholdCandidate =
      DetectedDevice(
        key = key,
        address = advertisement.address,
        deviceName = advertisement.deviceName,
        signature = signature,
        confidence = signature.baseConfidence,
        smoothedRssi = advertisement.rssi.toDouble(),
        rawRssi = advertisement.rssi,
        observationCount = 1,
        firstSeenMs = now,
        lastSeenMs = now,
        isAlertActive = false
      )

    if (advertisement.rssi < gateThresholdDbm) {
      return activeGateResult.copy(observedDevice = subThresholdCandidate, shouldVibrate = false)
    }

    val prevFastRssi = gateFastRssiByKey[key]
    val fastRssi =
      if (prevFastRssi == null) {
        advertisement.rssi.toDouble()
      } else {
        GATE_EMA_ALPHA * advertisement.rssi + (1.0 - GATE_EMA_ALPHA) * prevFastRssi
      }
    gateFastRssiByKey[key] = fastRssi
    val count = (gateObservationsByKey[key] ?: 0) + 1
    gateObservationsByKey[key] = count

    val newStatus =
      when (signature.baseConfidence) {
        ConfidenceLevel.HIGH, ConfidenceLevel.MEDIUM -> GateStatus.ALERT
        ConfidenceLevel.LOW -> GateStatus.CAUTION
      }

    // Do not downgrade an existing ALERT to a lower-severity CAUTION before manual reset.
    if (activeGateResult.status == GateStatus.ALERT && newStatus == GateStatus.CAUTION) {
      return activeGateResult.copy(observedDevice = subThresholdCandidate, shouldVibrate = false)
    }

    val canVibrate =
      lastGateVibrateMs == null || (now - (lastGateVibrateMs ?: 0L)) >= gateCooldownMs
    if (canVibrate) {
      lastGateVibrateMs = now
      gateVibrationNonce += 1L
    }

    val device =
      DetectedDevice(
        key = key,
        address = advertisement.address,
        deviceName = advertisement.deviceName,
        signature = signature,
        confidence = signature.baseConfidence,
        smoothedRssi = fastRssi,
        rawRssi = advertisement.rssi,
        observationCount = count,
        firstSeenMs = now,
        lastSeenMs = now,
        isAlertActive = true
      )

    activeGateResult =
      GateResult(
        status = newStatus,
        device = device,
        observedDevice = device,
        matchedSignature = signature,
        rawRssi = advertisement.rssi,
        effectiveRssi = fastRssi,
        shouldVibrate = canVibrate,
        vibrationNonce = gateVibrationNonce,
        timestampMs = now
      )
    return activeGateResult
  }

  /** Clears the current gate verdict and 2s vibration cooldown for the next examinee. */
  @Synchronized
  fun resetGateScreening(nowMs: Long = 0L): GateResult {
    gateFastRssiByKey.clear()
    gateObservationsByKey.clear()
    lastGateVibrateMs = null
    activeGateResult = GateResult.clear(timestampMs = nowMs)
    return activeGateResult
  }

  private fun pruneWindow(timestamps: ArrayDeque<Long>, nowMs: Long) {
    val cutoff = nowMs - windowMs
    while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
      timestamps.removeFirst()
    }
  }

  companion object {
    const val DEFAULT_WINDOW_MS = 8_000L
    const val DEFAULT_ENTRY_THRESHOLD_DBM = -72
    const val DEFAULT_HYSTERESIS_GAP_DBM = 8
    const val DEFAULT_EMA_ALPHA = 0.3
    const val DEFAULT_COOLDOWN_MS = 10_000L
    const val DEFAULT_STALE_DEVICE_MS = 120_000L
    const val DEFAULT_GATE_RSSI_THRESHOLD_DBM = -55
    const val DEFAULT_GATE_COOLDOWN_MS = 2_000L
    const val GATE_EMA_ALPHA = 0.7

    /**
     * Finds the highest-priority matching signature for [advertisement].
     * Priority favors explicit payload/service-data/service-UUID/name matches and higher confidence levels.
     */
    fun matchSignature(
      advertisement: BleAdvertisement,
      signatures: List<BleSignature>
    ): BleSignature? {
      val matches = signatures.filter { sig -> matchesSingleSignature(advertisement, sig) }
      if (matches.isEmpty()) return null
      return matches.maxWithOrNull(
        compareBy<BleSignature> { confidenceRank(it.baseConfidence) }
          .thenBy { specificityScore(it) }
          .thenBy { if (!it.isCustom) 1 else 0 }
      )
    }

    fun buildDeviceKey(advertisement: BleAdvertisement, signature: BleSignature): String {
      if (advertisement.address.isNotBlank()) {
        return advertisement.address.uppercase()
      }
      val companyId = signature.companyIdInt()
      val payloadHash =
        companyId?.let { advertisement.manufacturerData[it]?.contentHashCode() }
          ?: signature.serviceUuid?.trim()?.let { uuid ->
            advertisement.serviceData.entries
              .firstOrNull { it.key.equals(uuid, ignoreCase = true) }
              ?.value
              ?.contentHashCode()
          }
          ?: advertisement.deviceName?.hashCode()
          ?: 0
      return "${signature.id}:$payloadHash"
    }

    private fun specificityScore(signature: BleSignature): Int {
      var score = 0
      if (!signature.payloadAscii.isNullOrEmpty() || !signature.payloadHex.isNullOrBlank()) score += 2
      if (!signature.serviceDataHex.isNullOrBlank()) score += 2
      if (!signature.serviceUuid.isNullOrBlank()) score += 1
      if (!signature.deviceNamePrefix.isNullOrBlank() || !signature.deviceNamePatterns.isNullOrEmpty()) {
        score += 1
      }
      return score
    }

    private fun matchesSingleSignature(
      advertisement: BleAdvertisement,
      signature: BleSignature
    ): Boolean {
      var hasCondition = false

      if (!signature.companyId.isNullOrBlank()) {
        hasCondition = true
        val companyId = signature.companyIdInt() ?: return false
        val payload = advertisement.manufacturerData[companyId] ?: return false
        val expectedAscii = signature.payloadAscii
        if (!expectedAscii.isNullOrEmpty() && !containsBytes(payload, expectedAscii.encodeToByteArray())) {
          return false
        }
        if (!signature.payloadHex.isNullOrBlank()) {
          val expectedHex = signature.payloadHexBytes() ?: return false
          if (expectedHex.isEmpty() || !containsBytes(payload, expectedHex)) return false
        }
      } else if (!signature.payloadAscii.isNullOrEmpty() || !signature.payloadHex.isNullOrBlank()) {
        return false
      }

      val expectedUuid = signature.serviceUuid?.trim()?.takeIf { it.isNotEmpty() }
      val hasServiceData = !signature.serviceDataHex.isNullOrBlank()
      if (expectedUuid != null || hasServiceData) {
        hasCondition = true
        if (hasServiceData) {
          val expectedBytes = signature.serviceDataBytes() ?: return false
          if (expectedBytes.isEmpty()) return false
          val matched =
            if (expectedUuid != null) {
              advertisement.serviceData.entries.any { (uuid, bytes) ->
                uuid.equals(expectedUuid, ignoreCase = true) && containsBytes(bytes, expectedBytes)
              }
            } else {
              advertisement.serviceData.values.any { bytes -> containsBytes(bytes, expectedBytes) }
            }
          if (!matched) return false
        } else if (expectedUuid != null) {
          val uuidMatched =
            advertisement.serviceUuids.any { it.equals(expectedUuid, ignoreCase = true) } ||
              advertisement.serviceData.keys.any { it.equals(expectedUuid, ignoreCase = true) }
          if (!uuidMatched) return false
        }
      }

      val namePrefix = signature.deviceNamePrefix?.trim()?.takeIf { it.isNotEmpty() }
      if (namePrefix != null) {
        hasCondition = true
        val deviceName = advertisement.deviceName?.trim() ?: return false
        if (!deviceName.startsWith(namePrefix, ignoreCase = true)) return false
      }

      val namePatterns =
        signature.deviceNamePatterns?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.orEmpty()
      if (namePatterns.isNotEmpty()) {
        hasCondition = true
        val deviceName = advertisement.deviceName?.trim() ?: return false
        if (namePatterns.none { pattern -> deviceName.contains(pattern, ignoreCase = true) }) {
          return false
        }
      }

      return hasCondition
    }

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
      if (needle.isEmpty()) return true
      if (haystack.size < needle.size) return false
      val maxStart = haystack.size - needle.size
      for (i in 0..maxStart) {
        var matched = true
        for (j in needle.indices) {
          if (haystack[i + j] != needle[j]) {
            matched = false
            break
          }
        }
        if (matched) return true
      }
      return false
    }

    private fun confidenceRank(level: ConfidenceLevel): Int =
      when (level) {
        ConfidenceLevel.HIGH -> 3
        ConfidenceLevel.MEDIUM -> 2
        ConfidenceLevel.LOW -> 1
      }
  }
}
