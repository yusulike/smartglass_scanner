package com.windyflow.glassesdetector.ui.gate

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.windyflow.glassesdetector.ble.GateResult
import com.windyflow.glassesdetector.ble.GateStatus
import com.windyflow.glassesdetector.ble.GlassesCategory
import com.windyflow.glassesdetector.ble.PermissionHelper
import com.windyflow.glassesdetector.ui.theme.SmartGlassesDetectorTheme

@Composable
fun GateScreeningScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: GateScreeningViewModel = viewModel()
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
      viewModel.enterGateMode()
    }

  DisposableEffect(lifecycleOwner) {
    viewModel.enterGateMode()
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> viewModel.enterGateMode()
        Lifecycle.Event.ON_PAUSE -> viewModel.onForegroundChanged(false)
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.exitGateMode()
    }
  }

  LaunchedEffect(state.gateResult.vibrationNonce) {
    val nonce = state.gateResult.vibrationNonce
    if (nonce > 0L && state.gateResult.status != GateStatus.CLEAR) {
      triggerSilentVibration(context, state.gateResult.status)
    }
  }

  GateScreeningScreen(
    state = state,
    onBack = {
      viewModel.exitGateMode()
      onBack()
    },
    onResetNextExaminee = viewModel::resetForNextExaminee,
    onRequestPermissions = {
      permissionLauncher.launch(PermissionHelper.requiredRuntimePermissions().toTypedArray())
    },
    modifier = modifier
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GateScreeningScreen(
  state: GateScreeningUiState,
  onBack: () -> Unit,
  onResetNextExaminee: () -> Unit,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier
) {
  val targetBgColor =
    when (state.gateResult.status) {
      GateStatus.CLEAR -> Color(0xFF1B5E20)
      GateStatus.CAUTION -> Color(0xFFE65100)
      GateStatus.ALERT -> Color(0xFFB71C1C)
    }
  val animatedBgColor by animateColorAsState(targetValue = targetBgColor, label = "gate_bg_color")

  val (statusIcon, statusTitle, statusSubtitle) =
    when (state.gateResult.status) {
      GateStatus.CLEAR ->
        Triple(
          Icons.Default.CheckCircle,
          stringResource(R.string.gate_clear_title),
          stringResource(R.string.gate_clear_subtitle)
        )
      GateStatus.CAUTION ->
        Triple(
          Icons.Default.Warning,
          stringResource(R.string.gate_caution_title),
          stringResource(R.string.gate_caution_subtitle)
        )
      GateStatus.ALERT ->
        Triple(
          Icons.Default.Error,
          stringResource(R.string.gate_alert_title),
          stringResource(R.string.gate_alert_subtitle)
        )
    }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = animatedBgColor,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.gate_title), color = Color.White) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back_to_main),
              tint = Color.White
            )
          }
        },
        actions = {
          Surface(
            color = Color.White.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(end = 12.dp)
          ) {
            val modeLabel =
              when {
                !state.hasScanPermission -> stringResource(R.string.gate_mode_permission_needed)
                !state.isBluetoothEnabled -> stringResource(R.string.gate_mode_bt_off)
                state.isScanning && state.scanMode == BleScanMode.LOW_LATENCY -> "LOW_LATENCY"
                state.isScanning -> state.scanMode.name
                else -> stringResource(R.string.gate_mode_waiting)
              }
            Text(
              text = modeLabel,
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        color = Color.Black.copy(alpha = 0.28f),
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
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = stringResource(R.string.gate_banner_notice),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = statusIcon,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = statusTitle,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = statusSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.9f),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!state.hasScanPermission) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = stringResource(R.string.gate_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
              )
              Button(onClick = onRequestPermissions) {
                Text(stringResource(R.string.action_grant_permission))
              }
            }
          }
        } else {
          GateDetectionDetailCard(gateResult = state.gateResult)
        }
      }

      Button(
        onClick = onResetNextExaminee,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = animatedBgColor
          )
      ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.action_next_examinee),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
private fun GateDetectionDetailCard(gateResult: GateResult) {
  val device = gateResult.device
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f))
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (device == null || gateResult.status == GateStatus.CLEAR) {
        Text(
          text = stringResource(R.string.gate_prompt_hold_near),
          style = MaterialTheme.typography.titleSmall,
          color = Color.White,
          textAlign = TextAlign.Center
        )
        Text(
          text = stringResource(R.string.gate_threshold_hint),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.8f)
        )
      } else {
        Text(
          text = device.signature.label,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center
        )
        Text(
          text = "${gateResult.rawRssi ?: device.rawRssi} dBm",
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White
        )
        val identifier = device.signature.displayIdentifier()
        val categoryLabel =
          stringResource(
            if (device.signature.category == GlassesCategory.DISPLAY_AR) {
              R.string.category_display_ar
            } else {
              R.string.category_camera
            }
          )
        Text(
          text = categoryLabel,
          style = MaterialTheme.typography.labelLarge,
          color = Color.White.copy(alpha = 0.92f),
          textAlign = TextAlign.Center
        )
        Text(
          text =
            stringResource(
              R.string.gate_device_meta_format,
              device.confidence.name,
              identifier,
              device.key
            ),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.85f),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}

private fun triggerSilentVibration(context: Context, status: GateStatus) {
  val vibrator: Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Vibrator::class.java)
    }
  if (vibrator == null || !vibrator.hasVibrator()) return

  val effect =
    when (status) {
      GateStatus.ALERT -> VibrationEffect.createWaveform(longArrayOf(0L, 180L, 90L, 260L), -1)
      GateStatus.CAUTION -> VibrationEffect.createOneShot(150L, VibrationEffect.DEFAULT_AMPLITUDE)
      GateStatus.CLEAR -> return
    }
  runCatching { vibrator.vibrate(effect) }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun GateScreeningAlertPreview() {
  SmartGlassesDetectorTheme {
    GateScreeningScreen(
      state =
        GateScreeningUiState(
          isServiceRunning = true,
          isScanning = true,
          scanMode = BleScanMode.LOW_LATENCY,
          hasScanPermission = true,
          isBluetoothEnabled = true,
          gateResult =
            GateResult(
              status = GateStatus.ALERT,
              rawRssi = -48,
              effectiveRssi = -48.0,
              device =
                DetectedDevice(
                  key = "AA:BB:CC:DD:EE:FF",
                  address = "AA:BB:CC:DD:EE:FF",
                  deviceName = "RB Meta",
                  signature =
                    BleSignature(
                      id = "meta_rayban_tech",
                      companyId = "0x058E",
                      label = "Ray-Ban Meta (Meta Platforms Tech)",
                      payloadAscii = "META_RB_GLASS",
                      baseConfidence = ConfidenceLevel.HIGH
                    ),
                  confidence = ConfidenceLevel.HIGH,
                  smoothedRssi = -48.0,
                  rawRssi = -48,
                  observationCount = 1,
                  firstSeenMs = 1_000L,
                  lastSeenMs = 1_000L,
                  isAlertActive = true
                )
            )
        ),
      onBack = {},
      onResetNextExaminee = {},
      onRequestPermissions = {}
    )
  }
}
