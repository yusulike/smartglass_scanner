package com.windyflow.glassesdetector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.windyflow.glassesdetector.MainActivity
import com.windyflow.glassesdetector.R
import com.windyflow.glassesdetector.ble.BleAdvertisement
import com.windyflow.glassesdetector.ble.BleScanMode
import com.windyflow.glassesdetector.ble.BleSignalFilter
import com.windyflow.glassesdetector.ble.BleSignature
import com.windyflow.glassesdetector.ble.ConfidenceLevel
import com.windyflow.glassesdetector.ble.DetectedDevice
import com.windyflow.glassesdetector.ble.GateStatus
import com.windyflow.glassesdetector.ble.GlassesCategory
import com.windyflow.glassesdetector.ble.PermissionHelper
import com.windyflow.glassesdetector.ble.ScanProfile
import com.windyflow.glassesdetector.ble.ScannerStartResult
import com.windyflow.glassesdetector.ble.SignatureRepository
import com.windyflow.glassesdetector.ble.SmartGlassesScanner
import com.windyflow.glassesdetector.data.DetectionLogEntry
import com.windyflow.glassesdetector.data.DetectionStateHolder
import com.windyflow.glassesdetector.data.DetectorSettings
import com.windyflow.glassesdetector.data.WhitelistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android 14+ `connectedDevice` ForegroundService managing continuous BLE proximity detection.
 *
 * Subscribes to [SignatureRepository.activeSignatures] and foreground/background UI state:
 * - Foreground UI -> `SCAN_MODE_BALANCED`
 * - Background -> `SCAN_MODE_LOW_POWER`
 * - Custom Hex ID changes -> debounced scan restart via [SmartGlassesScanner]
 */
class BleScannerService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var signatureRepository: SignatureRepository
  private lateinit var whitelistRepository: WhitelistRepository
  private lateinit var scanner: SmartGlassesScanner
  private val signalFilter = BleSignalFilter()

  private var observationJob: Job? = null
  private var pruneJob: Job? = null
  private var bluetoothReceiverRegistered = false

  @Volatile private var latestSignatures: List<BleSignature> = emptyList()
  @Volatile private var latestSettings: DetectorSettings = DetectorSettings()

  private val bluetoothStateReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        when (state) {
          BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
            serviceScope.launch {
              scanner.stopScan()
              updateOngoingNotification(BleScanMode.LOW_POWER, isScanning = false)
              DetectionStateHolder.setServiceState(
                isServiceRunning = true,
                isScanning = false,
                scanMode = currentRequestedMode(),
                statusMessage = getString(R.string.service_status_bt_off),
                errorMessage = getString(R.string.service_error_bt_off)
              )
            }
          }
          BluetoothAdapter.STATE_ON -> {
            serviceScope.launch {
              restartScanWithCurrentConfig()
            }
          }
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    DetectionStateHolder.initialize(this)
    signatureRepository = SignatureRepository(this)
    whitelistRepository = WhitelistRepository(this)
    scanner =
      SmartGlassesScanner(
        context = this,
        scope = serviceScope,
        onAdvertisement = ::handleAdvertisement
      )
    ensureNotificationChannels()
    registerBluetoothReceiver()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopScanningAndSelf()
        return START_NOT_STICKY
      }
      ACTION_START, null -> {
        startForegroundScanning()
        return START_STICKY
      }
      else -> return START_NOT_STICKY
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    unregisterBluetoothReceiver()
    observationJob?.cancel()
    pruneJob?.cancel()
    serviceScope.launch { scanner.stopScan() }
    serviceScope.cancel()
    DetectionStateHolder.setServiceState(
      isServiceRunning = false,
      isScanning = false,
      scanMode = currentRequestedMode()
    )
    super.onDestroy()
  }

  private fun startForegroundScanning() {
    if (!PermissionHelper.hasScanPermission(this)) {
      val msg = getString(R.string.service_error_permission_denied)
      Log.w(TAG, msg)
      DetectionStateHolder.setServiceState(
        isServiceRunning = false,
        isScanning = false,
        scanMode = currentRequestedMode(),
        errorMessage = msg
      )
      stopSelf()
      return
    }

    val initialMode = currentRequestedMode()
    try {
      val foregroundType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
          0
        }
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID_ONGOING,
        buildOngoingNotification(initialMode, isScanning = true),
        foregroundType
      )
    } catch (sec: SecurityException) {
      Log.e(TAG, "Failed to enter connectedDevice foreground service", sec)
      DetectionStateHolder.setServiceState(
        isServiceRunning = false,
        isScanning = false,
        scanMode = initialMode,
        errorMessage = getString(R.string.service_error_fgs_permission)
      )
      stopSelf()
      return
    }

    if (observationJob?.isActive == true) {
      return
    }

    DetectionStateHolder.setServiceState(
      isServiceRunning = true,
      isScanning = false,
      scanMode = initialMode,
      statusMessage = getString(R.string.service_status_preparing)
    )

    // Watch settings independently so RSSI/cooldown/whitelist updates apply without restarting BLE scan.
    serviceScope.launch {
      whitelistRepository.settings.collectLatest { settings ->
        latestSettings = settings
        val filtered = signalFilter.currentDevices(System.currentTimeMillis(), settings.whitelistedKeys)
        DetectionStateHolder.updateDetectedDevices(filtered)
      }
    }

    val scanModeFlow =
      DetectionStateHolder.state
        .map { SmartGlassesScanner.resolveScanMode(it.scanProfile, it.isForegroundUi) }
        .distinctUntilChanged()

    serviceScope.launch {
      DetectionStateHolder.gateResetEvents.collect {
        signalFilter.resetGateScreening(System.currentTimeMillis())
      }
    }

    observationJob =
      serviceScope.launch {
        combine(signatureRepository.activeSignatures, scanModeFlow) { sigs, mode -> sigs to mode }
          .distinctUntilChanged()
          .collectLatest { (signatures, mode) ->
            latestSignatures = signatures
            applyScannerConfig(signatures, mode)
          }
      }

    serviceScope.launch {
      combine(scanner.isScanning, scanner.activeScanMode, scanner.lastError) {
        scanning,
        mode,
        error ->
        Triple(scanning, mode, error)
      }.collectLatest { (scanning, mode, error) ->
        updateOngoingNotification(mode, scanning)
        val statusMsg =
          when {
            error != null -> null
            scanning && mode == BleScanMode.LOW_LATENCY ->
              getString(R.string.service_status_gate)
            scanning && mode == BleScanMode.BALANCED ->
              getString(R.string.service_status_balanced)
            scanning && mode == BleScanMode.LOW_POWER ->
              getString(R.string.service_status_low_power)
            else -> getString(R.string.service_status_standby)
          }
        DetectionStateHolder.setServiceState(
          isServiceRunning = true,
          isScanning = scanning,
          scanMode = mode,
          statusMessage = statusMsg,
          errorMessage = error
        )
      }
    }

    pruneJob?.cancel()
    pruneJob =
      serviceScope.launch {
        while (isActive) {
          delay(PRUNE_INTERVAL_MS)
          val activeDevices =
            signalFilter.currentDevices(
              nowMs = System.currentTimeMillis(),
              whitelistedKeys = latestSettings.whitelistedKeys
            )
          DetectionStateHolder.updateDetectedDevices(activeDevices)
        }
      }
  }

  private suspend fun restartScanWithCurrentConfig() {
    if (latestSignatures.isEmpty()) return
    applyScannerConfig(latestSignatures, currentRequestedMode())
  }

  private suspend fun applyScannerConfig(signatures: List<BleSignature>, mode: BleScanMode) {
    when (val result = scanner.startOrUpdateScan(signatures, mode)) {
      ScannerStartResult.Started, ScannerStartResult.Scheduled -> Unit
      is ScannerStartResult.PermissionDenied -> {
        DetectionStateHolder.setServiceState(
          isServiceRunning = false,
          isScanning = false,
          scanMode = mode,
          errorMessage = result.reason
        )
        stopScanningAndSelf()
      }
      is ScannerStartResult.Unavailable -> {
        updateOngoingNotification(mode, isScanning = false)
        DetectionStateHolder.setServiceState(
          isServiceRunning = true,
          isScanning = false,
          scanMode = mode,
          errorMessage = result.reason
        )
      }
    }
  }

  private fun handleAdvertisement(advertisement: BleAdvertisement) {
    val settings = latestSettings
    val signatures = latestSignatures
    if (signatures.isEmpty()) return

    val runtimeSnapshot = DetectionStateHolder.state.value
    val observationCooldownMs = maxOf(settings.cooldownSeconds * 1_000L, MIN_OBSERVATION_LOG_COOLDOWN_MS)
    if (runtimeSnapshot.scanProfile == ScanProfile.GATE_SCREENING) {
      val gateResult =
        signalFilter.processGateScreening(
          advertisement = advertisement,
          signatures = signatures,
          whitelistedKeys = settings.whitelistedKeys
        )
      DetectionStateHolder.updateGateResult(gateResult)
      val gateDevice = gateResult.device
      if (gateResult.shouldVibrate && gateDevice != null && gateResult.status != GateStatus.CLEAR) {
        DetectionStateHolder.appendLog(
          buildLogEntry(
            device = gateDevice,
            timestampMs = advertisement.timestampMs,
            label = "[Gate:${gateResult.status.name}] ${gateDevice.signature.label}",
            notified = true
          )
        )
      } else {
        val observed = gateResult.observedDevice
        if (observed != null) {
          DetectionStateHolder.appendObservationIfDue(
            entry =
              buildLogEntry(
                device = observed,
                timestampMs = advertisement.timestampMs,
                label = "[Gate] ${observed.signature.label}",
                notified = false
              ),
            cooldownMs = observationCooldownMs
          )
        }
      }
      return
    }

    val decision =
      signalFilter.process(
        advertisement = advertisement,
        signatures = signatures,
        whitelistedKeys = settings.whitelistedKeys,
        entryThresholdDbm = settings.rssiThresholdDbm,
        cooldownMs = settings.cooldownSeconds * 1_000L
      )

    if (decision.matchedSignature == null || decision.isWhitelisted) {
      return
    }

    val updatedDevices =
      signalFilter.currentDevices(
        nowMs = advertisement.timestampMs,
        whitelistedKeys = settings.whitelistedKeys
      )
    DetectionStateHolder.updateDetectedDevices(updatedDevices)

    val device = decision.device ?: return
    if (decision.shouldNotify) {
      DetectionStateHolder.appendLog(
        buildLogEntry(
          device = device,
          timestampMs = advertisement.timestampMs,
          label = device.signature.label,
          notified = true
        )
      )
      postDetectionNotification(device)
    } else {
      DetectionStateHolder.appendObservationIfDue(
        entry =
          buildLogEntry(
            device = device,
            timestampMs = advertisement.timestampMs,
            label = device.signature.label,
            notified = false
          ),
        cooldownMs = observationCooldownMs
      )
    }
  }

  private fun buildLogEntry(
    device: DetectedDevice,
    timestampMs: Long,
    label: String,
    notified: Boolean
  ): DetectionLogEntry =
    DetectionLogEntry(
      timestampMs = timestampMs,
      key = device.key,
      label = label,
      confidence = device.confidence,
      category = device.signature.category,
      smoothedRssi = device.smoothedRssi,
      rawRssi = device.rawRssi,
      notified = notified
    )

  private fun stopScanningAndSelf() {
    observationJob?.cancel()
    observationJob = null
    pruneJob?.cancel()
    pruneJob = null
    serviceScope.launch {
      scanner.stopScan()
      signalFilter.clear()
      DetectionStateHolder.setServiceState(
        isServiceRunning = false,
        isScanning = false,
        scanMode = currentRequestedMode(),
        statusMessage = getString(R.string.status_stopped)
      )
      ServiceCompat.stopForeground(this@BleScannerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
    }
  }

  private fun currentRequestedMode(): BleScanMode {
    val state = DetectionStateHolder.state.value
    return SmartGlassesScanner.resolveScanMode(state.scanProfile, state.isForegroundUi)
  }

  private fun ensureNotificationChannels() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val ongoingChannel =
      NotificationChannel(
        CHANNEL_ONGOING,
        getString(R.string.channel_ongoing_name),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.channel_ongoing_desc)
        setShowBadge(false)
      }

    val alertChannel =
      NotificationChannel(
        CHANNEL_ALERTS,
        getString(R.string.channel_alerts_name),
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = getString(R.string.channel_alerts_desc)
      }

    manager.createNotificationChannel(ongoingChannel)
    manager.createNotificationChannel(alertChannel)
  }

  private fun buildOngoingNotification(mode: BleScanMode, isScanning: Boolean): Notification {
    val openAppIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent(this),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val contentText =
      when {
        !isScanning -> getString(R.string.notif_ongoing_bt_waiting)
        mode == BleScanMode.LOW_LATENCY -> getString(R.string.notif_ongoing_gate)
        mode == BleScanMode.BALANCED -> getString(R.string.notif_ongoing_balanced)
        else -> getString(R.string.notif_ongoing_low_power)
      }

    return NotificationCompat.Builder(this, CHANNEL_ONGOING)
      .setSmallIcon(R.drawable.ic_stat_glasses_scan)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(contentText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openAppIntent)
      .addAction(0, getString(R.string.action_stop_scan), stopIntent)
      .build()
  }

  private fun updateOngoingNotification(mode: BleScanMode, isScanning: Boolean) {
    if (!PermissionHelper.hasNotificationPermission(this)) return
    val manager = getSystemService(NotificationManager::class.java) ?: return
    manager.notify(NOTIFICATION_ID_ONGOING, buildOngoingNotification(mode, isScanning))
  }

  private fun postDetectionNotification(device: DetectedDevice) {
    if (!PermissionHelper.hasNotificationPermission(this)) return
    val manager = getSystemService(NotificationManager::class.java) ?: return

    val openAppIntent =
      PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val confidenceLabel =
      when (device.confidence) {
        ConfidenceLevel.HIGH -> getString(R.string.confidence_high)
        ConfidenceLevel.MEDIUM -> getString(R.string.confidence_medium)
        ConfidenceLevel.LOW -> getString(R.string.confidence_low)
      }

    val contentText =
      if (device.signature.category == GlassesCategory.DISPLAY_AR) {
        getString(R.string.notif_alert_text_display_ar, device.smoothedRssi)
      } else {
        getString(R.string.notif_alert_text, device.smoothedRssi)
      }

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_glasses_scan)
        .setContentTitle(getString(R.string.notif_alert_title, confidenceLabel, device.signature.label))
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent)
        .build()

    val notificationId = NOTIFICATION_ID_ALERT_BASE + (device.key.hashCode() and 0x0FFF)
    manager.notify(notificationId, notification)
  }

  private fun registerBluetoothReceiver() {
    if (bluetoothReceiverRegistered) return
    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    ContextCompat.registerReceiver(
      this,
      bluetoothStateReceiver,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    bluetoothReceiverRegistered = true
  }

  private fun unregisterBluetoothReceiver() {
    if (!bluetoothReceiverRegistered) return
    runCatching { unregisterReceiver(bluetoothStateReceiver) }
    bluetoothReceiverRegistered = false
  }

  companion object {
    private const val TAG = "BleScannerService"
    private const val CHANNEL_ONGOING = "ble_scanner_ongoing"
    private const val CHANNEL_ALERTS = "ble_scanner_alerts"
    private const val NOTIFICATION_ID_ONGOING = 1001
    private const val NOTIFICATION_ID_ALERT_BASE = 2000
    private const val PRUNE_INTERVAL_MS = 10_000L
    private const val MIN_OBSERVATION_LOG_COOLDOWN_MS = 10_000L

    const val ACTION_START = "com.windyflow.glassesdetector.action.START_SCAN"
    const val ACTION_STOP = "com.windyflow.glassesdetector.action.STOP_SCAN"

    fun startIntent(context: Context): Intent =
      Intent(context, BleScannerService::class.java).apply { action = ACTION_START }

    fun stopIntent(context: Context): Intent =
      Intent(context, BleScannerService::class.java).apply { action = ACTION_STOP }

    fun startService(context: Context) {
      ContextCompat.startForegroundService(context, startIntent(context))
    }

    fun stopService(context: Context) {
      context.startService(stopIntent(context))
    }
  }
}
