package com.windyflow.smartglassscanner.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windyflow.smartglassscanner.R
import com.windyflow.smartglassscanner.ble.BleSignature
import com.windyflow.smartglassscanner.ble.SignatureJsonParser
import com.windyflow.smartglassscanner.ble.SignatureRepository
import com.windyflow.smartglassscanner.data.AppSkin
import com.windyflow.smartglassscanner.data.DetectionStateHolder
import com.windyflow.smartglassscanner.data.DetectorSettings
import com.windyflow.smartglassscanner.data.ThemeMode
import com.windyflow.smartglassscanner.data.WhitelistEntry
import com.windyflow.smartglassscanner.data.WhitelistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val appSkin: AppSkin = AppSkin.OCEAN_BLUE,
  val rssiThresholdDbm: Int = DetectorSettings.DEFAULT_RSSI_THRESHOLD_DBM,
  val cooldownSeconds: Int = DetectorSettings.DEFAULT_COOLDOWN_SECONDS,
  val includeGenericLowSignatures: Boolean = SignatureRepository.DEFAULT_INCLUDE_GENERIC_LOW_SIGNATURES,
  val customHexInput: String = "",
  val isCustomHexValid: Boolean = false,
  val normalizedHexPreview: String? = null,
  val customHexIds: List<String> = emptyList(),
  val bundledSignatures: List<BleSignature> = emptyList(),
  val whitelist: List<WhitelistEntry> = emptyList(),
  val logCount: Int = 0,
  val feedbackMessage: String? = null
)

class SettingsViewModel(
  application: Application,
  private val signatureRepository: SignatureRepository,
  private val whitelistRepository: WhitelistRepository
) : AndroidViewModel(application) {

  constructor(application: Application) : this(
    application = application,
    signatureRepository = SignatureRepository(application),
    whitelistRepository = WhitelistRepository(application)
  )

  private val customHexInput = MutableStateFlow("")
  private val feedbackMessage = MutableStateFlow<String?>(null)

  init {
    DetectionStateHolder.initialize(application)
  }

  private val signatureConfigFlow =
    combine(
      signatureRepository.customHexIds,
      signatureRepository.includeGenericLowSignatures
    ) { customIds, includeGenericLow -> customIds to includeGenericLow }

  val uiState: StateFlow<SettingsUiState> =
    combine(
      whitelistRepository.settings,
      signatureConfigFlow,
      DetectionStateHolder.state,
      customHexInput,
      feedbackMessage
    ) { settings, (customIds, includeGenericLow), runtimeState, hexInput, feedback ->
      val normalized = SignatureJsonParser.normalizeHexCompanyId(hexInput)
      SettingsUiState(
        themeMode = settings.themeMode,
        appSkin = settings.appSkin,
        rssiThresholdDbm = settings.rssiThresholdDbm,
        cooldownSeconds = settings.cooldownSeconds,
        includeGenericLowSignatures = includeGenericLow,
        customHexInput = hexInput,
        isCustomHexValid = normalized != null,
        normalizedHexPreview = normalized,
        customHexIds = customIds,
        bundledSignatures = signatureRepository.bundledSignatures,
        whitelist = settings.whitelist,
        logCount = runtimeState.detectionLogs.size,
        feedbackMessage = feedback
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = SettingsUiState(bundledSignatures = signatureRepository.bundledSignatures)
    )

  fun onThemeModeChange(mode: ThemeMode) {
    viewModelScope.launch {
      whitelistRepository.setThemeMode(mode)
    }
  }

  fun onAppSkinChange(skin: AppSkin) {
    viewModelScope.launch {
      whitelistRepository.setAppSkin(skin)
    }
  }

  fun onRssiThresholdChange(value: Int) {
    viewModelScope.launch {
      whitelistRepository.setRssiThresholdDbm(value)
    }
  }

  fun onCooldownSecondsChange(seconds: Int) {
    viewModelScope.launch {
      whitelistRepository.setCooldownSeconds(seconds)
    }
  }

  fun onIncludeGenericLowSignaturesChange(enabled: Boolean) {
    viewModelScope.launch {
      signatureRepository.setIncludeGenericLowSignatures(enabled)
    }
  }

  fun onCustomHexInputChange(value: String) {
    customHexInput.value = value
    feedbackMessage.value = null
  }

  fun addCustomHexId() {
    val raw = customHexInput.value
    viewModelScope.launch {
      signatureRepository
        .addCustomHexId(raw)
        .onSuccess { normalized ->
          customHexInput.value = ""
          feedbackMessage.value =
            getApplication<Application>().getString(R.string.feedback_custom_hex_added, normalized)
        }
        .onFailure { error ->
          feedbackMessage.value =
            if (error is SignatureRepository.InvalidHexCompanyIdException) {
              getApplication<Application>().getString(R.string.error_invalid_hex_company_id)
            } else {
              error.message ?: getApplication<Application>().getString(R.string.unknown_error)
            }
        }
    }
  }

  fun removeCustomHexId(hex: String) {
    viewModelScope.launch {
      signatureRepository.removeCustomHexId(hex)
      feedbackMessage.value =
        getApplication<Application>().getString(R.string.feedback_custom_hex_removed, hex)
    }
  }

  fun removeWhitelistEntry(key: String) {
    viewModelScope.launch {
      whitelistRepository.removeFromWhitelist(key)
    }
  }

  fun buildExportLogText(): String = DetectionStateHolder.formatLogsAsText()

  fun exportLogsToUri(uri: Uri) {
    val app = getApplication<Application>()
    runCatching {
      val text = buildExportLogText()
      app.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
      } ?: error(app.getString(R.string.error_open_file))
    }.onSuccess {
      feedbackMessage.value = app.getString(R.string.feedback_export_success)
    }.onFailure { err ->
      feedbackMessage.value =
        app.getString(
          R.string.feedback_export_failed,
          err.message ?: app.getString(R.string.unknown_error)
        )
    }
  }

  fun clearLogs() {
    DetectionStateHolder.clearLogs()
    feedbackMessage.value =
      getApplication<Application>().getString(R.string.feedback_logs_cleared)
  }

  fun setFeedback(message: String) {
    feedbackMessage.value = message
  }
}
