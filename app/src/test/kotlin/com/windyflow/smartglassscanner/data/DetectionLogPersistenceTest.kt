package com.windyflow.smartglassscanner.data

import com.windyflow.smartglassscanner.ble.ConfidenceLevel
import com.windyflow.smartglassscanner.ble.GlassesCategory
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionLogPersistenceTest {
  private val tempFiles = mutableListOf<java.io.File>()

  @After
  fun tearDown() {
    DetectionStateHolder.resetForTest(file = null, syncPersist = false)
    tempFiles.forEach { file ->
      file.delete()
      java.io.File(file.parentFile, "${file.name}.tmp").delete()
    }
  }

  @Test
  fun observationCooldownThrottlesSubThresholdPacketsWhileAlertsLogImmediately() {
    val logFile = Files.createTempFile("logs_throttle_", ".json").toFile().also(tempFiles::add)
    logFile.delete()
    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)

    val observed1 =
      DetectionLogEntry(
        timestampMs = 1_000L,
        key = "AA:BB:CC:DD:EE:01",
        label = "Snap Spectacles",
        confidence = ConfidenceLevel.MEDIUM,
        smoothedRssi = -82.0,
        rawRssi = -82,
        notified = false
      )
    assertTrue(DetectionStateHolder.appendObservationIfDue(observed1, cooldownMs = 10_000L))
    // Duplicate within cooldown is throttled
    assertFalse(
      DetectionStateHolder.appendObservationIfDue(
        observed1.copy(timestampMs = 5_000L),
        cooldownMs = 10_000L
      )
    )

    // Confirmed alert records immediately even inside observation cooldown
    val alert = observed1.copy(timestampMs = 6_000L, smoothedRssi = -64.0, rawRssi = -64, notified = true)
    DetectionStateHolder.appendLog(alert)

    // After cooldown from last log (6_000 + 10_000 = 16_000), next observation records again
    assertFalse(
      DetectionStateHolder.appendObservationIfDue(
        observed1.copy(timestampMs = 15_000L),
        cooldownMs = 10_000L
      )
    )
    assertTrue(
      DetectionStateHolder.appendObservationIfDue(
        observed1.copy(timestampMs = 16_000L),
        cooldownMs = 10_000L
      )
    )

    val logs = DetectionStateHolder.state.value.detectionLogs
    assertEquals(3, logs.size)
    val report = DetectionStateHolder.formatLogsAsText(logs)
    assertTrue(report.contains("# Entries: 3"))
    assertTrue(report.contains("[ALERT]"))
    assertTrue(report.contains("[OBSERVED]"))
  }

  @Test
  fun logsPersistAcrossRestartAndClearResetsStorage() {
    val logFile = Files.createTempFile("logs_persist_", ".json").toFile().also(tempFiles::add)
    logFile.delete()
    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)

    DetectionStateHolder.appendObservationIfDue(
      DetectionLogEntry(
        timestampMs = 2_000L,
        key = "11:22:33:44:55:66",
        label = "Even Realities Smart Glasses (Name)",
        confidence = ConfidenceLevel.MEDIUM,
        category = GlassesCategory.DISPLAY_AR,
        smoothedRssi = -78.0,
        rawRssi = -78,
        notified = false
      ),
      cooldownMs = 10_000L
    )
    DetectionStateHolder.appendLog(
      DetectionLogEntry(
        timestampMs = 4_000L,
        key = "11:22:33:44:55:77",
        label = "Ray-Ban Meta (Meta Platforms Tech)",
        confidence = ConfidenceLevel.HIGH,
        smoothedRssi = -60.0,
        rawRssi = -60,
        notified = true
      )
    )

    // Simulate process restart by resetting memory state and reloading from same file
    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)
    val reloaded = DetectionStateHolder.state.value.detectionLogs
    assertEquals(2, reloaded.size)
    assertEquals("Ray-Ban Meta (Meta Platforms Tech)", reloaded[0].label)
    assertEquals(GlassesCategory.CAMERA, reloaded[0].category)
    assertTrue(reloaded[0].notified)
    assertEquals("Even Realities Smart Glasses (Name)", reloaded[1].label)
    assertEquals(GlassesCategory.DISPLAY_AR, reloaded[1].category)
    assertFalse(reloaded[1].notified)
    assertTrue(DetectionStateHolder.formatLogsAsText(reloaded).contains("[DISPLAY_AR]"))

    // Clearing wipes both memory and disk file
    DetectionStateHolder.clearLogs()
    assertTrue(DetectionStateHolder.state.value.detectionLogs.isEmpty())

    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)
    assertTrue(DetectionStateHolder.state.value.detectionLogs.isEmpty())
  }

  @Test
  fun observationLogsPersistAndFormatMatchReason() {
    val logFile = Files.createTempFile("logs_match_reason_", ".json").toFile().also(tempFiles::add)
    logFile.delete()
    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)

    val observedWithMatch =
      DetectionLogEntry(
        timestampMs = 1_000L,
        key = "A5:FC:C5:C8:2C:99",
        label = "Zhuhai Jieli Generic Chipset (Audio / Glasses)",
        confidence = ConfidenceLevel.LOW,
        smoothedRssi = -88.0,
        rawRssi = -88,
        notified = false,
        matchReason = "companyId=0x05D6"
      )
    assertTrue(DetectionStateHolder.appendObservationIfDue(observedWithMatch, cooldownMs = 10_000L))

    val legacyWithoutMatch =
      DetectionLogEntry(
        timestampMs = 2_000L,
        key = "11:22:33:44:55:66",
        label = "Snap Spectacles",
        confidence = ConfidenceLevel.MEDIUM,
        smoothedRssi = -72.0,
        rawRssi = -72,
        notified = false
      )
    assertTrue(DetectionStateHolder.appendObservationIfDue(legacyWithoutMatch, cooldownMs = 10_000L))

    val textReport = DetectionStateHolder.formatLogsAsText()
    assertTrue(textReport.contains("(match=companyId=0x05D6, key=A5:FC:C5:C8:2C:99"))
    assertTrue(textReport.contains("(key=11:22:33:44:55:66"))

    DetectionStateHolder.resetForTest(file = logFile, syncPersist = true)
    val reloaded = DetectionStateHolder.state.value.detectionLogs
    assertEquals(2, reloaded.size)
    assertEquals("", reloaded[0].matchReason)
    assertEquals("companyId=0x05D6", reloaded[1].matchReason)
  }
}
