package com.windyflow.glassesdetector.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windyflow.glassesdetector.R
import com.windyflow.glassesdetector.ble.BleScanMode
import com.windyflow.glassesdetector.ble.BleSignature
import com.windyflow.glassesdetector.ble.ConfidenceLevel
import com.windyflow.glassesdetector.ble.DetectedDevice
import com.windyflow.glassesdetector.ble.GlassesCategory
import com.windyflow.glassesdetector.ble.PermissionHelper
import com.windyflow.glassesdetector.ui.theme.SmartGlassesDetectorTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
  onNavigateToSettings: () -> Unit,
  onNavigateToGateScreening: () -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel()
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingStartAfterPermission by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      viewModel.refreshEnvironment()
      if (pendingStartAfterPermission && PermissionHelper.hasScanPermission(context)) {
        pendingStartAfterPermission = false
        viewModel.toggleScanning(true)
      } else {
        pendingStartAfterPermission = false
      }
    }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          viewModel.refreshEnvironment()
          viewModel.onForegroundChanged(true)
        }
        Lifecycle.Event.ON_PAUSE -> {
          viewModel.onForegroundChanged(false)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      viewModel.onForegroundChanged(false)
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  MainScreen(
    state = state,
    onToggleScan = { enable ->
      if (enable && !PermissionHelper.hasScanPermission(context)) {
        pendingStartAfterPermission = true
        permissionLauncher.launch(PermissionHelper.requiredRuntimePermissions().toTypedArray())
      } else {
        viewModel.toggleScanning(enable)
      }
    },
    onRequestPermissions = {
      pendingStartAfterPermission = false
      permissionLauncher.launch(PermissionHelper.requiredRuntimePermissions().toTypedArray())
    },
    onOpenAppSettings = {
      val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    },
    onOpenBluetoothSettings = {
      val intent =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    },
    onIgnoreDevice = viewModel::addToWhitelist,
    onNavigateToSettings = onNavigateToSettings,
    onNavigateToGateScreening = onNavigateToGateScreening,
    modifier = modifier
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
  state: MainDashboardState,
  onToggleScan: (Boolean) -> Unit,
  onRequestPermissions: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onOpenBluetoothSettings: () -> Unit,
  onIgnoreDevice: (DetectedDevice) -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToGateScreening: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.app_name),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
          ),
        actions = {
          FilledTonalButton(
            onClick = onNavigateToGateScreening,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.action_gate_screening),
              style = MaterialTheme.typography.labelMedium
            )
          }
          IconButton(onClick = onNavigateToSettings) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = stringResource(R.string.cd_open_settings)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item(key = "disclaimer") {
        DisclaimerBanner()
      }

      item(key = "scan_control") {
        ScanControlCard(
          state = state,
          onToggleScan = onToggleScan
        )
      }

      if (!state.hasBleHardware) {
        item(key = "no_ble") {
          WarningCard(
            icon = Icons.Default.BluetoothDisabled,
            title = stringResource(R.string.warning_no_ble_title),
            body = stringResource(R.string.warning_no_ble_body)
          )
        }
      } else if (!state.hasScanPermission) {
        item(key = "scan_permission_missing") {
          PermissionMissingCard(
            onRequestPermissions = onRequestPermissions,
            onOpenAppSettings = onOpenAppSettings
          )
        }
      } else if (!state.hasNotificationPermission) {
        item(key = "notification_permission_missing") {
          NotificationPermissionCard(onRequestPermissions = onRequestPermissions)
        }
      }

      if (state.hasBleHardware && !state.isBluetoothEnabled) {
        item(key = "bluetooth_disabled") {
          BluetoothDisabledCard(onOpenBluetoothSettings = onOpenBluetoothSettings)
        }
      }

      if (!state.errorMessage.isNullOrBlank()) {
        item(key = "error_banner") {
          WarningCard(
            icon = Icons.Default.Warning,
            title = stringResource(R.string.warning_scan_status_title),
            body = state.errorMessage
          )
        }
      }

      item(key = "list_header") {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.detected_list_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = stringResource(R.string.detected_device_count, state.detectedDevices.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      if (state.detectedDevices.isEmpty()) {
        item(key = "empty_state") {
          EmptyDetectionCard(isScanning = state.isServiceRunning && state.isScanning)
        }
      } else {
        items(items = state.detectedDevices, key = { it.key }) { device ->
          DetectedDeviceCard(
            device = device,
            onIgnoreClick = { onIgnoreDevice(device) }
          )
        }
      }
    }
  }
}

@Composable
private fun DisclaimerBanner() {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = stringResource(R.string.banner_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}

@Composable
private fun ScanControlCard(
  state: MainDashboardState,
  onToggleScan: (Boolean) -> Unit
) {
  val statusText =
    when {
      !state.isServiceRunning -> stringResource(R.string.status_stopped)
      state.isScanning && state.scanMode == BleScanMode.BALANCED ->
        stringResource(R.string.status_foreground_balanced)
      state.isScanning && state.scanMode == BleScanMode.LOW_POWER ->
        stringResource(R.string.status_background_low_power)
      else -> state.statusMessage ?: stringResource(R.string.status_waiting)
    }

  val badgeContainer =
    if (state.isServiceRunning && state.isScanning) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceVariant
    }

  val badgeContent =
    if (state.isServiceRunning && state.isScanning) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.scan_card_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(4.dp))
          Surface(color = badgeContainer, shape = MaterialTheme.shapes.small) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                tint = badgeContent,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = badgeContent
              )
            }
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
          checked = state.isServiceRunning,
          onCheckedChange = onToggleScan,
          enabled = state.hasBleHardware
        )
      }
      Text(
        text = stringResource(R.string.scan_card_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun PermissionMissingCard(
  onRequestPermissions: () -> Unit,
  onOpenAppSettings: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = stringResource(R.string.permission_missing_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
        text = stringResource(R.string.permission_missing_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRequestPermissions) {
          Text(stringResource(R.string.action_request_permission))
        }
        OutlinedButton(onClick = onOpenAppSettings) {
          Text(stringResource(R.string.action_app_settings))
        }
      }
    }
  }
}

@Composable
private fun NotificationPermissionCard(onRequestPermissions: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Default.NotificationsOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onTertiaryContainer
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.notification_permission_title),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
          text = stringResource(R.string.notification_permission_body),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer
        )
      }
      FilledTonalButton(onClick = onRequestPermissions) {
        Text(stringResource(R.string.action_allow))
      }
    }
  }
}

@Composable
private fun BluetoothDisabledCard(onOpenBluetoothSettings: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Default.BluetoothDisabled,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.bluetooth_disabled_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
          text = stringResource(R.string.bluetooth_disabled_body),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }
      FilledTonalButton(onClick = onOpenBluetoothSettings) {
        Text(stringResource(R.string.action_turn_on))
      }
    }
  }
}

@Composable
private fun WarningCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(imageVector = icon, contentDescription = null)
      Column {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(text = body, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun EmptyDetectionCard(isScanning: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text =
            stringResource(
              if (isScanning) R.string.empty_scanning_title else R.string.empty_stopped_title
            ),
          style = MaterialTheme.typography.titleSmall
        )
        Text(
          text =
            stringResource(
              if (isScanning) R.string.empty_scanning_body else R.string.empty_stopped_body
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun DetectedDeviceCard(
  device: DetectedDevice,
  onIgnoreClick: () -> Unit
) {
  val (badgeLabel, badgeContainer, badgeContent) =
    when (device.confidence) {
      ConfidenceLevel.HIGH ->
        Triple(
          stringResource(R.string.confidence_badge_high),
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer
        )
      ConfidenceLevel.MEDIUM ->
        Triple(
          stringResource(R.string.confidence_badge_medium),
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer
        )
      ConfidenceLevel.LOW ->
        Triple(
          stringResource(R.string.confidence_badge_low),
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

  val formattedTime =
    remember(device.lastSeenMs) {
      deviceTimeFormatter.format(Instant.ofEpochMilli(device.lastSeenMs))
    }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(color = badgeContainer, shape = MaterialTheme.shapes.small) {
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = badgeContent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
        AssistChip(
          onClick = {},
          enabled = false,
          label = {
            Text(
              stringResource(
                if (device.isAlertActive) R.string.chip_alert_active else R.string.chip_weak_signal
              )
            )
          },
          colors =
            AssistChipDefaults.assistChipColors(
              disabledLabelColor =
                if (device.isAlertActive) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        )
      }

      Text(
        text = device.signature.label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )

      val categoryText =
        stringResource(
          if (device.signature.category == GlassesCategory.DISPLAY_AR) {
            R.string.category_display_ar
          } else {
            R.string.category_camera
          }
        )
      val nameText =
        device.deviceName?.takeIf { it.isNotBlank() }?.let {
          stringResource(R.string.device_name_label, it)
        }
      val idText = stringResource(R.string.device_id_label, device.key)
      val companyText =
        device.signature.companyId?.let { stringResource(R.string.device_company_id_label, it) }
      val subtitleParts = listOfNotNull(categoryText, nameText, idText, companyText)
      Text(
        text = subtitleParts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.device_rssi_format, device.smoothedRssi, device.rawRssi),
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text =
              pluralStringResource(
                R.plurals.device_observations_format,
                device.observationCount,
                device.observationCount,
                formattedTime
              ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        OutlinedButton(onClick = onIgnoreClick) {
          Icon(
            imageVector = Icons.Default.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_ignore))
        }
      }
    }
  }
}

private val deviceTimeFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
  SmartGlassesDetectorTheme {
    MainScreen(
      state =
        MainDashboardState(
          isServiceRunning = true,
          isScanning = true,
          scanMode = BleScanMode.BALANCED,
          hasScanPermission = true,
          hasNotificationPermission = true,
          isBluetoothEnabled = true,
          detectedDevices =
            listOf(
              DetectedDevice(
                key = "AA:BB:CC:DD:EE:FF",
                address = "AA:BB:CC:DD:EE:FF",
                deviceName = "RB Meta 01",
                signature =
                  BleSignature(
                    id = "meta_rayban_tech",
                    companyId = "0x058E",
                    label = "Ray-Ban Meta (Meta Platforms Tech)",
                    payloadAscii = "META_RB_GLASS",
                    baseConfidence = ConfidenceLevel.HIGH
                  ),
                confidence = ConfidenceLevel.HIGH,
                smoothedRssi = -64.2,
                rawRssi = -63,
                observationCount = 3,
                firstSeenMs = 1_700_000_000_000L,
                lastSeenMs = 1_700_000_005_000L,
                isAlertActive = true
              )
            )
        ),
      onToggleScan = {},
      onRequestPermissions = {},
      onOpenAppSettings = {},
      onOpenBluetoothSettings = {},
      onIgnoreDevice = {},
      onNavigateToSettings = {}
    )
  }
}
