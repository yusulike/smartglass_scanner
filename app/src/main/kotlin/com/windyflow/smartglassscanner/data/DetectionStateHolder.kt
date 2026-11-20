package com.windyflow.smartglassscanner.data

import android.content.Context
import com.windyflow.smartglassscanner.ble.BleScanMode
import com.windyflow.smartglassscanner.ble.ConfidenceLevel
import com.windyflow.smartglassscanner.ble.DetectedDevice
import com.windyflow.smartglassscanner.ble.GateResult
import com.windyflow.smartglassscanner.ble.GlassesCategory
import com.windyflow.smartglassscanner.ble.ScanProfile
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DetectionLogEntry(
  val timestampMs: Long,
  val key: String,
  val label: String,
  val confidence: ConfidenceLevel,
  val category: GlassesCategory = GlassesCategory.CAMERA,
  val smoothedRssi: Double,
  val rawRssi: Int,
  val notified: Boolean,
  val matchReason: String = ""
)

/** Atomic JSON file persistence for on-device [DetectionLogEntry] history. */
internal class DetectionLogStorage(private val file: File) {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun load(): List<DetectionLogEntry> {
    if (!file.exists()) return emptyList()
    return runCatching {
      val raw = file.readText(Charsets.UTF_8)
      if (raw.isBlank()) emptyList() else json.decodeFromString<List<DetectionLogEntry>>(raw)
    }.getOrElse { emptyList() }
  }

  fun save(entries: List<DetectionLogEntry>) {
    runCatching {
      file.parentFile?.mkdirs()
      val temp = File(file.parentFile, "${file.name}.tmp")
      temp.writeText(json.encodeToString(entries), Charsets.UTF_8)
      if (!temp.renameTo(file)) {
        file.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
        temp.delete()
      }
    }
  }
}

data class ScannerRuntimeState(
  val isServiceRunning: Boolean = false,
  val isScanning: Boolean = false,
  val isForegroundUi: Boolean = false,
  val scanProfile: ScanProfile = ScanProfile.BACKGROUND_GUARD,
  val scanMode: BleScanMode = BleScanMode.LOW_POWER,
  val detectedDevices: List<DetectedDevice> = emptyList(),
  val gateResult: GateResult = GateResult.clear(),
  val detectionLogs: List<DetectionLogEntry> = emptyList(),
  val statusMessage: String? = null,
  val errorMessage: String? = null
)

/**
 * Process-wide reactive bridge between [com.windyflow.smartglassscanner.service.BleScannerService]
 * and Compose UI screens. Detection logs persist locally in `files/detection_logs.json`
 * so `.txt` exports survive app restarts while remaining strictly on-device.
 */
object DetectionStateHolder {
  private const val MAX_LOG_ENTRIES = 500
  private const val LOG_FILE_NAME = "detection_logs.json"

  private val logTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val initialized = AtomicBoolean(false)
  private val stateLock = Any()
  private val fileLock = Any()

  @Volatile private var logStorage: DetectionLogStorage? = null
  @Volatile private var persistSynchronously: Boolean = false
  private var clearEpoch: Long = 0L
  private val lastLoggedMsByKey = HashMap<String, Long>()

  private val _state = MutableStateFlow(ScannerRuntimeState())
  val state: StateFlow<ScannerRuntimeState> = _state.asStateFlow()

  private val _gateResetEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val gateResetEvents: SharedFlow<Unit> = _gateResetEvents.asSharedFlow()

  /** Attaches local disk log persistence once per process and loads prior logs asynchronously. */
  fun initialize(context: Context) {
    if (!initialized.compareAndSet(false, true)) return
    val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
    configureStorage(file, synchronous = false)
  }

  /** Configures backing JSON file; intended for unit tests to test restart persistence cleanly. */
  internal fun configureStorage(file: File?, synchronous: Boolean = true) {
    val storage = file?.let(::DetectionLogStorage)
    val loadEpoch: Long
    synchronized(stateLock) {
      logStorage = storage
      loadEpoch = clearEpoch
    }
    if (storage == null) return

    val loadTask = {
      val diskEntries =
        synchronized(fileLock) {
          storage.load()
        }
      if (diskEntries.isNotEmpty()) {
        synchronized(stateLock) {
          if (clearEpoch == loadEpoch) {
            _state.update { current ->
              val merged =
                (current.detectionLogs + diskEntries)
                  .sortedByDescending { it.timestampMs }
                  .distinctBy { Triple(it.timestampMs, it.key, it.notified) }
                  .take(MAX_LOG_ENTRIES)
              current.copy(detectionLogs = merged)
            }
          }
        }
      }
    }

    if (synchronous) {
      loadTask()
    } else {
      ioScope.launch { loadTask() }
    }
  }

  internal fun resetForTest(file: File? = null, syncPersist: Boolean = true) {
    initialized.set(false)
    persistSynchronously = syncPersist
    synchronized(stateLock) {
      clearEpoch += 1L
      lastLoggedMsByKey.clear()
      _state.update { ScannerRuntimeState() }
    }
    configureStorage(file, synchronous = true)
  }

  fun setServiceState(
    isServiceRunning: Boolean,
    isScanning: Boolean,
    scanMode: BleScanMode,
    statusMessage: String? = null,
    errorMessage: String? = null
  ) {
    _state.update { current ->
      current.copy(
        isServiceRunning = isServiceRunning,
        isScanning = isScanning,
        scanMode = scanMode,
        statusMessage = statusMessage,
        errorMessage = errorMessage
      )
    }
  }

  fun setForegroundUi(isForeground: Boolean) {
    _state.update { it.copy(isForegroundUi = isForeground) }
  }

  fun setScanProfile(profile: ScanProfile) {
    _state.update { current ->
      val resetGate =
        if (profile == ScanProfile.BACKGROUND_GUARD) GateResult.clear() else current.gateResult
      current.copy(scanProfile = profile, gateResult = resetGate)
    }
    if (profile == ScanProfile.BACKGROUND_GUARD) {
      _gateResetEvents.tryEmit(Unit)
    }
  }

  fun updateGateResult(result: GateResult) {
    _state.update { it.copy(gateResult = result) }
  }

  fun requestGateReset() {
    _state.update { it.copy(gateResult = GateResult.clear(System.currentTimeMillis())) }
    _gateResetEvents.tryEmit(Unit)
  }

  fun updateDetectedDevices(devices: List<DetectedDevice>) {
    _state.update { it.copy(detectedDevices = devices) }
  }

  /** Appends an immediate detection log (e.g., confirmed `[ALERT]`) and persists it locally. */
  fun appendLog(entry: DetectionLogEntry) {
    val snapshot: List<DetectionLogEntry>
    synchronized(stateLock) {
      lastLoggedMsByKey[entry.key] = entry.timestampMs
      snapshot = insertLogEntryLocked(entry)
    }
    schedulePersist(snapshot)
  }

  /**
   * Appends a non-alert `[OBSERVED]` log entry at most once per [cooldownMs] per device key
   * (always recording the first observation immediately) so repeated BLE beacons do not flood storage.
   */
  fun appendObservationIfDue(entry: DetectionLogEntry, cooldownMs: Long): Boolean {
    var snapshot: List<DetectionLogEntry>? = null
    synchronized(stateLock) {
      val lastMs = lastLoggedMsByKey[entry.key]
      if (lastMs == null || (entry.timestampMs - lastMs) >= cooldownMs) {
        lastLoggedMsByKey[entry.key] = entry.timestampMs
        snapshot = insertLogEntryLocked(entry)
      }
    }
    snapshot?.let {
      schedulePersist(it)
      return true
    }
    return false
  }

  private fun insertLogEntryLocked(entry: DetectionLogEntry): List<DetectionLogEntry> {
    var updated = emptyList<DetectionLogEntry>()
    _state.update { current ->
      updated = (listOf(entry) + current.detectionLogs).take(MAX_LOG_ENTRIES)
      current.copy(detectionLogs = updated)
    }
    return updated
  }

  fun clearLogs() {
    synchronized(stateLock) {
      clearEpoch += 1L
      lastLoggedMsByKey.clear()
      _state.update { it.copy(detectionLogs = emptyList()) }
    }
    schedulePersist(emptyList())
  }

  private fun schedulePersist(snapshot: List<DetectionLogEntry>) {
    val storage = logStorage ?: return
    val writeTask = {
      val latest = synchronized(stateLock) { _state.value.detectionLogs.ifEmpty { snapshot } }
      synchronized(fileLock) {
        storage.save(latest)
      }
    }
    if (persistSynchronously) {
      writeTask()
    } else {
      ioScope.launch { writeTask() }
    }
  }

  fun clearDetectedDevices() {
    _state.update { it.copy(detectedDevices = emptyList()) }
  }

  /** Formats local detection logs into a plain-text report for `.txt` export. */
  fun formatLogsAsText(logs: List<DetectionLogEntry> = _state.value.detectionLogs): String {
    val sb = StringBuilder()
    sb.appendLine("# SmartGlass Scanner - Local Detection Log")
    sb.appendLine("# Generated: ${logTimeFormatter.format(Instant.now())}")
    sb.appendLine("# Entries: ${logs.size}")
    sb.appendLine()
    if (logs.isEmpty()) {
      sb.appendLine("(No detection logs recorded)")
      return sb.toString()
    }
    logs.forEach { entry ->
      val ts = logTimeFormatter.format(Instant.ofEpochMilli(entry.timestampMs))
      val alertFlag = if (entry.notified) "ALERT" else "OBSERVED"
      val matchSegment = if (entry.matchReason.isNotBlank()) "match=${entry.matchReason}, " else ""
      sb.appendLine(
        "[$ts] [$alertFlag] [${entry.confidence.name}] [${entry.category.name}] ${entry.label} " +
          "(${matchSegment}key=${entry.key}, smoothed=${"%.1f".format(entry.smoothedRssi)} dBm, raw=${entry.rawRssi} dBm)"
      )
    }
    return sb.toString()
  }
}
