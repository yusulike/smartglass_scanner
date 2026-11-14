package com.windyflow.glassesdetector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.util.size
import com.windyflow.glassesdetector.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure-Kotlin throttle guard enforcing Android's BLE scan restart budget
 * (max 5 `startScan` calls per 30s -> minimum 6.5s spacing between starts).
 */
class ScanThrottleGate(
  private val minRestartIntervalMs: Long = MIN_RESTART_INTERVAL_MS,
  private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
  private var lastStartMs: Long = Long.MIN_VALUE

  /**
   * Returns `0L` if a scan start is allowed immediately (and records the start timestamp),
   * or the positive delay in milliseconds to wait before starting.
   */
  @Synchronized
  fun tryAcquireStartDelayMs(): Long {
    val now = clockMs()
    if (lastStartMs == Long.MIN_VALUE) {
      lastStartMs = now
      return 0L
    }
    val elapsed = now - lastStartMs
    return if (elapsed >= minRestartIntervalMs) {
      lastStartMs = now
      0L
    } else {
      minRestartIntervalMs - elapsed
    }
  }

  @Synchronized
  fun reset() {
    lastStartMs = Long.MIN_VALUE
  }

  companion object {
    const val MIN_RESTART_INTERVAL_MS = 6_500L
  }
}

sealed interface ScannerStartResult {
  data object Started : ScannerStartResult
  data object Scheduled : ScannerStartResult
  data class Unavailable(val reason: String) : ScannerStartResult
  data class PermissionDenied(val reason: String) : ScannerStartResult
}

/**
 * Wrapper around Android [BluetoothLeScanner] providing:
 * - Dynamic [ScanFilter] creation from [BleSignature] list (never passes `null` filters so background scanning remains active)
 * - Foreground (`SCAN_MODE_BALANCED`) vs. Background (`SCAN_MODE_LOW_POWER`) scan mode switching
 * - 6.5s throttle guard against Android's 5-starts-per-30s silent block
 * - 20-minute periodic scan refresh to avoid the 30-minute opportunistic downgrade
 */
class SmartGlassesScanner(
  private val context: Context,
  private val scope: CoroutineScope,
  private val onAdvertisement: (BleAdvertisement) -> Unit,
  private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
  private val bluetoothManager: BluetoothManager? =
    context.getSystemService(BluetoothManager::class.java)

  private val throttleGate = ScanThrottleGate(clockMs = clockMs)
  private val scanMutex = Mutex()

  private var pendingStartJob: Job? = null
  private var refreshJob: Job? = null
  private var activeCallback: ScanCallback? = null
  private var currentSignatures: List<BleSignature> = emptyList()
  private var currentMode: BleScanMode = BleScanMode.LOW_POWER
  private var desiredRunning: Boolean = false

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _activeScanMode = MutableStateFlow(BleScanMode.LOW_POWER)
  val activeScanMode: StateFlow<BleScanMode> = _activeScanMode.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun isBluetoothEnabled(): Boolean = bluetoothManager?.adapter?.isEnabled == true

  fun hasBleHardware(): Boolean = bluetoothManager?.adapter != null

  /**
   * Starts or updates BLE scanning with [signatures] and [mode], respecting the 6.5s
   * restart throttle guard.
   */
  suspend fun startOrUpdateScan(
    signatures: List<BleSignature>,
    mode: BleScanMode
  ): ScannerStartResult =
    scanMutex.withLock {
      currentSignatures = signatures
      currentMode = mode
      desiredRunning = true
      _activeScanMode.value = mode

      if (signatures.isEmpty()) {
        stopInternalLocked()
        val msg = context.getString(R.string.scanner_error_no_signatures)
        _lastError.value = msg
        return@withLock ScannerStartResult.Unavailable(msg)
      }

      val adapter = bluetoothManager?.adapter
      if (adapter == null) {
        stopInternalLocked()
        _lastError.value = context.getString(R.string.scanner_error_no_ble)
        return@withLock ScannerStartResult.Unavailable(
          context.getString(R.string.scanner_reason_no_ble)
        )
      }
      if (!adapter.isEnabled) {
        stopInternalLocked()
        _lastError.value = context.getString(R.string.scanner_error_bt_disabled)
        return@withLock ScannerStartResult.Unavailable(
          context.getString(R.string.scanner_reason_bt_disabled)
        )
      }

      val delayMs = throttleGate.tryAcquireStartDelayMs()
      if (delayMs > 0L) {
        scheduleDelayedRestartLocked(delayMs)
        return@withLock ScannerStartResult.Scheduled
      }

      performStartLocked(adapter, signatures, mode)
    }

  /** Stops active BLE scanning and cancels scheduled refreshes or delayed restarts. */
  suspend fun stopScan() {
    scanMutex.withLock {
      desiredRunning = false
      pendingStartJob?.cancel()
      pendingStartJob = null
      stopInternalLocked()
      _lastError.value = null
    }
  }

  private fun scheduleDelayedRestartLocked(delayMs: Long) {
    pendingStartJob?.cancel()
    pendingStartJob =
      scope.launch {
        delay(delayMs)
        scanMutex.withLock {
          if (!desiredRunning) return@withLock
          val adapter = bluetoothManager?.adapter ?: return@withLock
          if (!adapter.isEnabled) return@withLock
          val gateDelay = throttleGate.tryAcquireStartDelayMs()
          if (gateDelay > 0L) {
            scheduleDelayedRestartLocked(gateDelay)
            return@withLock
          }
          performStartLocked(adapter, currentSignatures, currentMode)
        }
      }
  }

  @SuppressLint("MissingPermission")
  private fun performStartLocked(
    adapter: BluetoothAdapter,
    signatures: List<BleSignature>,
    mode: BleScanMode
  ): ScannerStartResult {
    val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    if (scanner == null) {
      _isScanning.value = false
      _lastError.value = context.getString(R.string.scanner_error_no_scanner)
      return ScannerStartResult.Unavailable("BluetoothLeScanner null")
    }

    stopCallbackOnlyLocked(scanner)

    val filters = resolveActiveScanFilters(signatures, mode)
    if (filters.isEmpty() && mode == BleScanMode.LOW_POWER) {
      _isScanning.value = false
      _lastError.value = context.getString(R.string.scanner_error_no_filters)
      return ScannerStartResult.Unavailable(context.getString(R.string.scanner_reason_no_filters))
    }

    val settings = buildScanSettings(mode)
    val callback = createScanCallback()

    return try {
      scanner.startScan(filters, settings, callback)
      activeCallback = callback
      _isScanning.value = true
      _activeScanMode.value = mode
      _lastError.value = null
      startPeriodicRefreshLocked()
      Log.i(TAG, "Started BLE scan: mode=$mode, filters=${filters.size}")
      ScannerStartResult.Started
    } catch (sec: SecurityException) {
      Log.w(TAG, "BLE scan blocked by missing permission", sec)
      activeCallback = null
      _isScanning.value = false
      _lastError.value = context.getString(R.string.scanner_error_permission)
      ScannerStartResult.PermissionDenied(sec.message ?: "Missing BLE permission")
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to start BLE scan", t)
      activeCallback = null
      _isScanning.value = false
      _lastError.value =
        context.getString(
          R.string.scanner_error_start_failed,
          t.message ?: context.getString(R.string.unknown_error)
        )
      ScannerStartResult.Unavailable(t.message ?: "Unknown error")
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopInternalLocked() {
    refreshJob?.cancel()
    refreshJob = null
    val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
    if (scanner != null) {
      stopCallbackOnlyLocked(scanner)
    } else {
      activeCallback = null
    }
    _isScanning.value = false
  }

  @SuppressLint("MissingPermission")
  private fun stopCallbackOnlyLocked(scanner: BluetoothLeScanner) {
    val callback = activeCallback ?: return
    activeCallback = null
    try {
      scanner.stopScan(callback)
    } catch (sec: SecurityException) {
      Log.w(TAG, "stopScan SecurityException", sec)
    } catch (t: Throwable) {
      Log.w(TAG, "stopScan failed", t)
    }
  }

  private fun startPeriodicRefreshLocked() {
    refreshJob?.cancel()
    refreshJob =
      scope.launch {
        while (isActive) {
          delay(REFRESH_INTERVAL_MS)
          scanMutex.withLock {
            if (!desiredRunning) return@withLock
            val adapter = bluetoothManager?.adapter
            if (adapter == null || !adapter.isEnabled) {
              stopInternalLocked()
              return@withLock
            }
            val delayMs = throttleGate.tryAcquireStartDelayMs()
            if (delayMs > 0L) {
              scheduleDelayedRestartLocked(delayMs)
            } else {
              Log.i(TAG, "Performing 20-minute BLE scan refresh")
              performStartLocked(adapter, currentSignatures, currentMode)
            }
          }
        }
      }
  }

  private fun createScanCallback(): ScanCallback =
    object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        onAdvertisement(result.toBleAdvertisement(clockMs()))
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        val now = clockMs()
        results.forEach { result ->
          onAdvertisement(result.toBleAdvertisement(now))
        }
      }

      override fun onScanFailed(errorCode: Int) {
        val description = scanErrorDescription(context, errorCode)
        Log.e(TAG, "BLE onScanFailed: code=$errorCode ($description)")
        _isScanning.value = false
        _lastError.value =
          context.getString(R.string.scanner_error_scan_failed, errorCode, description)
      }
    }

  companion object {
    private const val TAG = "SmartGlassesScanner"
    const val REFRESH_INTERVAL_MS = 20L * 60L * 1000L

    /**
     * In screen-on foreground modes (`BALANCED` / `LOW_LATENCY`), returns `true` when name-based rules
     * are active so case-insensitive substrings, Scan Response names, and pre-Android 13 devices are not
     * dropped by OS hardware [ScanFilter] limits. Background `LOW_POWER` always enforces strict hardware filters.
     */
    fun shouldUseUnfilteredForegroundScan(
      signatures: List<BleSignature>,
      mode: BleScanMode
    ): Boolean = mode != BleScanMode.LOW_POWER && signatures.any { it.hasNameCriterion() }

    fun resolveActiveScanFilters(signatures: List<BleSignature>, mode: BleScanMode): List<ScanFilter> =
      if (shouldUseUnfilteredForegroundScan(signatures, mode)) {
        emptyList()
      } else {
        buildScanFilters(signatures)
      }

    /**
     * Generates deduplicated [ScanFilter]s from [signatures] so scanning continues working
     * when the device screen is turned off.
     */
    fun buildScanFilters(signatures: List<BleSignature>): List<ScanFilter> {
      val filters = mutableListOf<ScanFilter>()
      val seenCompanyIds = HashSet<Int>()
      val seenUuids = HashSet<String>()
      val seenServiceDataUuids = HashSet<String>()
      val seenNamePrefixes = HashSet<String>()

      signatures.forEach { signature ->
        val companyId = signature.companyIdInt()
        if (companyId != null && seenCompanyIds.add(companyId)) {
          filters +=
            ScanFilter.Builder()
              .setManufacturerData(companyId, byteArrayOf(), byteArrayOf())
              .build()
        }

        val uuid = signature.serviceUuid?.trim()
        if (!uuid.isNullOrEmpty()) {
          val normalizedUuid = uuid.uppercase()
          val requiresServiceData = !signature.serviceDataHex.isNullOrBlank()
          runCatching { ParcelUuid.fromString(uuid) }
            .onSuccess { parcelUuid ->
              if (requiresServiceData) {
                if (seenServiceDataUuids.add(normalizedUuid)) {
                  filters +=
                    ScanFilter.Builder()
                      .setServiceData(parcelUuid, byteArrayOf(), byteArrayOf())
                      .build()
                }
              } else if (seenUuids.add(normalizedUuid)) {
                filters += ScanFilter.Builder().setServiceUuid(parcelUuid).build()
              }
            }
            .onFailure { Log.w(TAG, "Ignoring invalid service UUID: $uuid", it) }
        }

        val namePrefix = signature.deviceNamePrefix?.trim()
        if (!namePrefix.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (seenNamePrefixes.add(namePrefix)) {
            val prefixBytes = namePrefix.toByteArray(Charsets.UTF_8)
            val mask = ByteArray(prefixBytes.size) { 0xFF.toByte() }
            // Hardware prefix filter on Complete Local Name (AD type 0x09) to stay within chipset filter budget.
            filters += ScanFilter.Builder().setAdvertisingDataTypeWithData(0x09, prefixBytes, mask).build()
          }
        }
      }
      return filters
    }

    /** Resolves the Android BLE scan mode from [profile] and foreground UI state. */
    fun resolveScanMode(profile: ScanProfile, isForegroundUi: Boolean): BleScanMode =
      when {
        !isForegroundUi -> BleScanMode.LOW_POWER
        profile == ScanProfile.GATE_SCREENING -> BleScanMode.LOW_LATENCY
        else -> BleScanMode.BALANCED
      }

    fun buildScanSettings(mode: BleScanMode): ScanSettings {
      val androidMode =
        when (mode) {
          BleScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
          BleScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
          BleScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
        }
      return ScanSettings.Builder()
        .setScanMode(androidMode)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setReportDelay(0L)
        .build()
    }

    fun scanErrorDescription(context: Context, errorCode: Int): String =
      when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
          context.getString(R.string.scan_fail_already_started)
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
          context.getString(R.string.scan_fail_app_reg_failed)
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
          context.getString(R.string.scan_fail_internal_error)
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
          context.getString(R.string.scan_fail_unsupported)
        5 -> context.getString(R.string.scan_fail_too_frequently)
        else -> context.getString(R.string.scan_fail_unknown)
      }
  }
}

@SuppressLint("MissingPermission")
private fun ScanResult.toBleAdvertisement(timestampMs: Long): BleAdvertisement {
  val record = scanRecord
  val manufacturerMap = LinkedHashMap<Int, ByteArray>()
  val sparse = record?.manufacturerSpecificData
  if (sparse != null) {
    for (i in 0 until sparse.size) {
      val id = sparse.keyAt(i)
      val bytes = sparse.valueAt(i)
      if (bytes != null) {
        manufacturerMap[id] = bytes
      }
    }
  }
  val uuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
  val serviceDataMap = LinkedHashMap<String, ByteArray>()
  record?.serviceData?.forEach { (parcelUuid, bytes) ->
    if (bytes != null) {
      serviceDataMap[parcelUuid.uuid.toString()] = bytes
    }
  }
  val name =
    record?.deviceName
      ?: runCatching { device?.name }.getOrNull()

  return BleAdvertisement(
    address = device?.address.orEmpty(),
    deviceName = name,
    rssi = rssi,
    manufacturerData = manufacturerMap,
    serviceUuids = uuids,
    serviceData = serviceDataMap,
    timestampMs = timestampMs
  )
}
