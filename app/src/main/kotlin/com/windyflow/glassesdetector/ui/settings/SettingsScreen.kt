package com.windyflow.glassesdetector.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windyflow.glassesdetector.R
import com.windyflow.glassesdetector.ble.BleSignature
import com.windyflow.glassesdetector.ble.ConfidenceLevel
import com.windyflow.glassesdetector.ble.BleSignalFilter
import com.windyflow.glassesdetector.ble.GlassesCategory
import com.windyflow.glassesdetector.data.AppSkin
import com.windyflow.glassesdetector.data.DetectorSettings
import com.windyflow.glassesdetector.data.ThemeMode
import com.windyflow.glassesdetector.data.WhitelistEntry
import com.windyflow.glassesdetector.ui.theme.SmartGlassesDetectorTheme
import com.windyflow.glassesdetector.ui.theme.resolveDarkTheme
import com.windyflow.glassesdetector.ui.theme.skinSwatchColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel()
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  val createLogLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
      if (uri != null) {
        viewModel.exportLogsToUri(uri)
      }
    }

  LaunchedEffect(state.feedbackMessage) {
    val msg = state.feedbackMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
  }

  SettingsScreen(
    state = state,
    snackbarHostState = snackbarHostState,
    onBack = onBack,
    onThemeModeChange = viewModel::onThemeModeChange,
    onAppSkinChange = viewModel::onAppSkinChange,
    onRssiThresholdChange = viewModel::onRssiThresholdChange,
    onCooldownSecondsChange = viewModel::onCooldownSecondsChange,
    onCustomHexInputChange = viewModel::onCustomHexInputChange,
    onAddCustomHexId = viewModel::addCustomHexId,
    onRemoveCustomHexId = viewModel::removeCustomHexId,
    onRemoveWhitelistEntry = viewModel::removeWhitelistEntry,
    onExportLogs = {
      val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
      createLogLauncher.launch("smart_glasses_log_$stamp.txt")
    },
    onClearLogs = viewModel::clearLogs,
    modifier = modifier
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
  state: SettingsUiState,
  snackbarHostState: SnackbarHostState,
  onBack: () -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit = {},
  onAppSkinChange: (AppSkin) -> Unit = {},
  onRssiThresholdChange: (Int) -> Unit,
  onCooldownSecondsChange: (Int) -> Unit,
  onCustomHexInputChange: (String) -> Unit,
  onAddCustomHexId: () -> Unit,
  onRemoveCustomHexId: (String) -> Unit,
  onRemoveWhitelistEntry: (String) -> Unit,
  onExportLogs: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item(key = "appearance_and_skin") {
        AppearanceSkinCard(
          themeMode = state.themeMode,
          appSkin = state.appSkin,
          onThemeModeChange = onThemeModeChange,
          onAppSkinChange = onAppSkinChange
        )
      }

      item(key = "detection_thresholds") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = stringResource(R.string.settings_section_sensitivity),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = stringResource(R.string.settings_rssi_entry_label),
                  style = MaterialTheme.typography.bodyMedium
                )
                Text(
                  "${state.rssiThresholdDbm} dBm",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold
                )
              }
              Slider(
                value = state.rssiThresholdDbm.toFloat(),
                onValueChange = { onRssiThresholdChange(it.roundToInt()) },
                valueRange =
                  DetectorSettings.MIN_RSSI_THRESHOLD_DBM.toFloat()..
                    DetectorSettings.MAX_RSSI_THRESHOLD_DBM.toFloat()
              )
              Text(
                text =
                  stringResource(
                    R.string.settings_rssi_hysteresis_desc,
                    state.rssiThresholdDbm - BleSignalFilter.DEFAULT_HYSTERESIS_GAP_DBM,
                    BleSignalFilter.DEFAULT_HYSTERESIS_GAP_DBM
                  ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            HorizontalDivider()

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = stringResource(R.string.settings_cooldown_label),
                  style = MaterialTheme.typography.bodyMedium
                )
                Text(
                  text = stringResource(R.string.settings_cooldown_value, state.cooldownSeconds),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold
                )
              }
              Slider(
                value = state.cooldownSeconds.toFloat(),
                onValueChange = { onCooldownSecondsChange(it.roundToInt()) },
                valueRange =
                  DetectorSettings.MIN_COOLDOWN_SECONDS.toFloat()..
                    DetectorSettings.MAX_COOLDOWN_SECONDS.toFloat()
              )
            }
          }
        }
      }

      item(key = "custom_hex_ids") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = stringResource(R.string.settings_section_custom_hex),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = stringResource(R.string.settings_custom_hex_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedTextField(
                value = state.customHexInput,
                onValueChange = onCustomHexInputChange,
                label = { Text(stringResource(R.string.settings_custom_hex_input_label)) },
                singleLine = true,
                isError = state.customHexInput.isNotBlank() && !state.isCustomHexValid,
                supportingText = {
                  when {
                    state.customHexInput.isBlank() ->
                      Text(stringResource(R.string.settings_custom_hex_hint))
                    state.isCustomHexValid ->
                      Text(
                        stringResource(
                          R.string.settings_custom_hex_preview,
                          state.normalizedHexPreview.orEmpty()
                        )
                      )
                    else ->
                      Text(stringResource(R.string.settings_custom_hex_error))
                  }
                },
                modifier = Modifier.weight(1f)
              )
              Button(
                onClick = onAddCustomHexId,
                enabled = state.isCustomHexValid,
                modifier = Modifier.padding(top = 6.dp)
              ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_add))
              }
            }

            if (state.customHexIds.isEmpty()) {
              Text(
                text = stringResource(R.string.settings_custom_hex_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                state.customHexIds.forEach { hex ->
                  InputChip(
                    selected = true,
                    onClick = { onRemoveCustomHexId(hex) },
                    label = { Text(hex) },
                    trailingIcon = {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_remove_hex, hex),
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  )
                }
              }
            }
          }
        }
      }

      item(key = "bundled_signatures") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.settings_section_bundled, state.bundledSignatures.size),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            state.bundledSignatures.forEach { sig ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                val categoryLabel =
                  stringResource(
                    if (sig.category == GlassesCategory.DISPLAY_AR) {
                      R.string.category_display_ar
                    } else {
                      R.string.category_camera
                    }
                  )
                Column(modifier = Modifier.weight(1f)) {
                  Text(text = sig.label, style = MaterialTheme.typography.bodyMedium)
                  val identifier = sig.displayIdentifier()
                  val payloadNote = sig.displayPatternSummary()
                  val requiredHits = sig.requiredObservations()
                  Text(
                    text =
                      pluralStringResource(
                        R.plurals.settings_sig_detail_format,
                        requiredHits,
                        identifier,
                        "$payloadNote · $categoryLabel",
                        requiredHits
                      ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = sig.baseConfidence.name,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      }

      item(key = "whitelist") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.settings_section_whitelist, state.whitelist.size),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            if (state.whitelist.isEmpty()) {
              Text(
                text = stringResource(R.string.settings_whitelist_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              state.whitelist.forEach { entry ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                      text = entry.key,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  IconButton(onClick = { onRemoveWhitelistEntry(entry.key) }) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = stringResource(R.string.cd_remove_whitelist, entry.key)
                    )
                  }
                }
              }
            }
          }
        }
      }

      item(key = "logs_and_privacy") {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = stringResource(R.string.settings_section_logs),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = stringResource(R.string.settings_logs_desc, state.logCount),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = onExportLogs) {
                Icon(
                  imageVector = Icons.Default.Download,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_export_logs))
              }
              OutlinedButton(
                onClick = onClearLogs,
                enabled = state.logCount > 0
              ) {
                Text(stringResource(R.string.action_clear))
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSkinCard(
  themeMode: ThemeMode,
  appSkin: AppSkin,
  onThemeModeChange: (ThemeMode) -> Unit,
  onAppSkinChange: (AppSkin) -> Unit
) {
  val isDarkPreview = resolveDarkTheme(themeMode)
  val availableSkins =
    remember {
      AppSkin.entries.filter { it != AppSkin.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = stringResource(R.string.settings_section_appearance),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_theme_mode_label),
          style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = themeMode == mode,
              onClick = { onThemeModeChange(mode) },
              label = { Text(stringResource(themeModeLabelRes(mode))) }
            )
          }
        }
      }

      HorizontalDivider()

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.settings_skin_label),
          style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          availableSkins.forEach { skin ->
            val swatch = skinSwatchColor(skin, isDarkPreview)
            FilterChip(
              selected = appSkin == skin,
              onClick = { onAppSkinChange(skin) },
              leadingIcon = {
                Box(
                  modifier =
                    Modifier
                      .size(14.dp)
                      .clip(CircleShape)
                      .background(swatch)
                      .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                      )
                )
              },
              label = { Text(stringResource(appSkinLabelRes(skin))) }
            )
          }
        }
      }
    }
  }
}

private fun themeModeLabelRes(mode: ThemeMode): Int =
  when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
  }

private fun appSkinLabelRes(skin: AppSkin): Int =
  when (skin) {
    AppSkin.OCEAN_BLUE -> R.string.skin_ocean_blue
    AppSkin.EMERALD -> R.string.skin_emerald
    AppSkin.AMETHYST -> R.string.skin_amethyst
    AppSkin.AMBER_GUARD -> R.string.skin_amber_guard
    AppSkin.MONOCHROME -> R.string.skin_monochrome
    AppSkin.DYNAMIC -> R.string.skin_dynamic
  }

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
  SmartGlassesDetectorTheme {
    SettingsScreen(
      state =
        SettingsUiState(
          rssiThresholdDbm = -75,
          cooldownSeconds = 10,
          customHexInput = "0x12AB",
          isCustomHexValid = true,
          normalizedHexPreview = "0x12AB",
          customHexIds = listOf("0x004C", "0x12AB"),
          bundledSignatures =
            listOf(
              BleSignature(
                id = "meta_rayban_tech",
                companyId = "0x058E",
                label = "Ray-Ban Meta (Meta Platforms Tech)",
                payloadAscii = "META_RB_GLASS",
                baseConfidence = ConfidenceLevel.HIGH
              )
            ),
          whitelist = listOf(WhitelistEntry(key = "AA:BB:CC:DD:EE:FF", label = "My Glasses")),
          logCount = 4
        ),
      snackbarHostState = SnackbarHostState(),
      onBack = {},
      onRssiThresholdChange = {},
      onCooldownSecondsChange = {},
      onCustomHexInputChange = {},
      onAddCustomHexId = {},
      onRemoveCustomHexId = {},
      onRemoveWhitelistEntry = {},
      onExportLogs = {},
      onClearLogs = {}
    )
  }
}
