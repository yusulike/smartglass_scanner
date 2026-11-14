package com.windyflow.smartglassscanner.ui.gate

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windyflow.smartglassscanner.ble.BleScanMode
import com.windyflow.smartglassscanner.ble.GateResult
import com.windyflow.smartglassscanner.ble.PermissionHelper
import com.windyflow.smartglassscanner.ble.ScanProfile
import com.windyflow.smartglassscanner.data.DetectionStateHolder
import com.windyflow.smartglassscanner.data.ScannerRuntimeState
import com.windyflow.smartglassscanner.service.BleScannerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GateScreeningUiState(
  val isServiceRunning: Boolean = false,
  val isScanning: Boolean = false,
  val scanMode: BleScanMode = BleScanMode.LOW_LATENCY,
  val hasScanPermission: Boolean = false,
  val isBluetoothEnabled: Boolean = true,
  val hasBleHardware: Boolean = true,
  val gateResult: GateResult = GateResult.clear(),
  val errorMessage: String? = null
)

class GateScreeningViewModel(
  application: Application,
  private val runtimeStateFlow: StateFlow<ScannerRuntimeState>
) : AndroidViewModel(application) {

  constructor(application: Application) : this(
    application = application,
    runtimeStateFlow = DetectionStateHolder.state
  )

  private val envSnapshot = MutableStateFlow(readEnvironment())

  val uiState: StateFlow<GateScreeningUiState> =
    combine(runtimeStateFlow, envSnapshot) { runtime, env ->
      GateScreeningUiState(
        isServiceRunning = runtime.isServiceRunning,
        isScanning = runtime.isScanning,
        scanMode = runtime.scanMode,
        hasScanPermission = env.hasScanPermission,
        isBluetoothEnabled = env.isBluetoothEnabled,
        hasBleHardware = env.hasBleHardware,
        gateResult = runtime.gateResult,
        errorMessage = runtime.errorMessage
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = GateScreeningUiState()
    )

  fun refreshEnvironment() {
    envSnapshot.value = readEnvironment()
  }

  fun enterGateMode() {
    val ctx = getApplication<Application>()
    refreshEnvironment()
    DetectionStateHolder.setForegroundUi(true)
    DetectionStateHolder.setScanProfile(ScanProfile.GATE_SCREENING)
    if (PermissionHelper.hasScanPermission(ctx) && !runtimeStateFlow.value.isServiceRunning) {
      BleScannerService.startService(ctx)
    }
  }

  fun onForegroundChanged(isForeground: Boolean) {
    DetectionStateHolder.setForegroundUi(isForeground)
  }

  fun exitGateMode() {
    DetectionStateHolder.setScanProfile(ScanProfile.BACKGROUND_GUARD)
  }

  fun resetForNextExaminee() {
    DetectionStateHolder.requestGateReset()
  }

  private fun readEnvironment(): EnvSnapshot {
    val ctx = getApplication<Application>()
    val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    return EnvSnapshot(
      hasScanPermission = PermissionHelper.hasScanPermission(ctx),
      isBluetoothEnabled = adapter?.isEnabled == true,
      hasBleHardware = adapter != null
    )
  }

  private data class EnvSnapshot(
    val hasScanPermission: Boolean,
    val isBluetoothEnabled: Boolean,
    val hasBleHardware: Boolean
  )
}
