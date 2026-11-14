package com.windyflow.smartglassscanner.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * SDK-aware runtime permission helper ensuring API 31+ devices NEVER request
 * `ACCESS_FINE_LOCATION`, strictly adhering to the BLE-only Play policy.
 */
object PermissionHelper {
  /**
   * Returns all runtime permissions to request for the given [sdkInt].
   * - API 33+: `BLUETOOTH_SCAN`, `POST_NOTIFICATIONS`
   * - API 31..32: `BLUETOOTH_SCAN`
   * - API <= 30: `ACCESS_FINE_LOCATION`
   */
  fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
    when {
      sdkInt >= Build.VERSION_CODES.TIRAMISU ->
        listOf(
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.POST_NOTIFICATIONS
        )
      sdkInt >= Build.VERSION_CODES.S ->
        listOf(Manifest.permission.BLUETOOTH_SCAN)
      else ->
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  /**
   * Returns the mandatory BLE scanning permission required to run `BluetoothLeScanner`
   * and `connectedDevice` foreground service for the given [sdkInt].
   */
  fun requiredScanPermission(sdkInt: Int = Build.VERSION.SDK_INT): String =
    if (sdkInt >= Build.VERSION_CODES.S) {
      Manifest.permission.BLUETOOTH_SCAN
    } else {
      Manifest.permission.ACCESS_FINE_LOCATION
    }

  fun hasScanPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    val perm = requiredScanPermission(sdkInt)
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
  }

  fun hasNotificationPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun missingRuntimePermissions(
    context: Context,
    sdkInt: Int = Build.VERSION.SDK_INT
  ): List<String> =
    requiredRuntimePermissions(sdkInt).filter { perm ->
      ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
    }
}
