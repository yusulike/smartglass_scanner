package com.windyflow.smartglassscanner.ui.main

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windyflow.smartglassscanner.ble.BleScanMode
import com.windyflow.smartglassscanner.ble.DetectedDevice
import com.windyflow.smartglassscanner.ble.PermissionHelper
import com.windyflow.smartglassscanner.data.DetectionStateHolder
import com.windyflow.smartglassscanner.data.ScannerRuntimeState
import com.windyflow.smartglassscanner.data.WhitelistRepository
import com.windyflow.smartglassscanner.service.BleScannerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainDashboardState(
  val isServiceRunning: Boolean = false,
  val isScanning: Boolean = false,
  val scanMode: BleScanMode = BleScanMode.LOW_POWER,
  val hasScanPermission: Boolean = false,
  val hasNotificationPermission: Boolean = true,
  val isBluetoothEnabled: Boolean = true,
  val hasBleHardware: Boolean = true,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val detectedDevices: List<DetectedDevice> = emptyList()
)

class MainScreenViewModel(
  application: Application,
  private val whitelistRepository: WhitelistRepository,
  private val runtimeStateFlow: StateFlow<ScannerRuntimeState>
) : AndroidViewModel(application) {

  constructor(application: Application) : this(
    application = application,
    whitelistRepository = WhitelistRepository(application),
    runtimeStateFlow = DetectionStateHolder.state
  )

  private val permissionSnapshot = MutableStateFlow(readEnvironmentSnapshot())

  val uiState: StateFlow<MainDashboardState> =
    combine(runtimeStateFlow, permissionSnapshot) { runtime, env ->
      MainDashboardState(
        isServiceRunning = runtime.isServiceRunning,
        isScanning = runtime.isScanning,
        scanMode = runtime.scanMode,
        hasScanPermission = env.hasScanPermission,
        hasNotificationPermission = env.hasNotificationPermission,
        isBluetoothEnabled = env.isBluetoothEnabled,
        hasBleHardware = env.hasBleHardware,
        statusMessage = runtime.statusMessage,
        errorMessage = runtime.errorMessage,
        detectedDevices = runtime.detectedDevices
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = MainDashboardState()
    )

  fun refreshEnvironment() {
    permissionSnapshot.value = readEnvironmentSnapshot()
  }

  fun onForegroundChanged(isForeground: Boolean) {
    DetectionStateHolder.setForegroundUi(isForeground)
  }

  fun toggleScanning(enable: Boolean) {
    val ctx = getApplication<Application>()
    refreshEnvironment()
    if (enable) {
      if (!PermissionHelper.hasScanPermission(ctx)) {
        return
      }
      BleScannerService.startService(ctx)
    } else {
      BleScannerService.stopService(ctx)
    }
  }

  fun addToWhitelist(device: DetectedDevice) {
    viewModelScope.launch {
      whitelistRepository.addToWhitelist(
        key = device.key,
        label = device.signature.label
      )
    }
  }

  private fun readEnvironmentSnapshot(): EnvironmentSnapshot {
    val ctx = getApplication<Application>()
    val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    return EnvironmentSnapshot(
      hasScanPermission = PermissionHelper.hasScanPermission(ctx),
      hasNotificationPermission = PermissionHelper.hasNotificationPermission(ctx),
      isBluetoothEnabled = adapter?.isEnabled == true,
      hasBleHardware = adapter != null
    )
  }

  private data class EnvironmentSnapshot(
    val hasScanPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val isBluetoothEnabled: Boolean,
    val hasBleHardware: Boolean
  )
}
