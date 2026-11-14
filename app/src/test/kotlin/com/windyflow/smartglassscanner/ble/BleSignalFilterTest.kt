package com.windyflow.smartglassscanner.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleSignalFilterTest {
  private val signatures =
    listOf(
      BleSignature(
        id = "meta_rayban_tech",
        companyId = "0x058E",
        label = "Ray-Ban Meta (Meta Platforms Tech)",
        payloadAscii = "META_RB_GLASS",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "luxottica_rayban",
        companyId = "0x0D53",
        label = "Ray-Ban Meta Frame (Luxottica)",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "snap_spectacles",
        companyId = "0x03C2",
        label = "Snap Spectacles",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "tcl_rayneo",
        companyId = "0x0BC6",
        label = "TCL / RayNeo Smart Glasses",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "meizu_myvu",
        companyId = "0x03AB",
        label = "Meizu MYVU AR Glasses",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "meta_platforms_general",
        companyId = "0x01AB",
        label = "Meta Platforms Device",
        baseConfidence = ConfidenceLevel.LOW
      ),
      BleSignature(
        id = "heycyan_sdk",
        companyId = null,
        label = "HeyCyan SDK Smart Glasses",
        serviceUuid = "7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "rokid_ai_glasses",
        companyId = null,
        label = "Rokid AI Glasses",
        serviceUuid = "00009100-0000-1000-8000-00805F9B34FB",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "da_echo_glasses",
        companyId = null,
        label = "Da Echo Smart Glasses",
        serviceUuid = "0000FEA8-0000-1000-8000-00805F9B34FB",
        serviceDataHex = "413149010000",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "heycyan_name_prefix",
        companyId = null,
        label = "HeyCyan Smart Glasses (Name Prefix)",
        deviceNamePrefix = "HeyCyan-",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "vuzix_corp",
        companyId = "0x060C",
        label = "Vuzix Smart Glasses",
        baseConfidence = ConfidenceLevel.HIGH
      ),
      BleSignature(
        id = "meta_ble_service",
        companyId = null,
        label = "Meta BLE Service (Ray-Ban / Quest)",
        serviceUuid = "0000FD5F-0000-1000-8000-00805F9B34FB",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "even_realities_name",
        companyId = null,
        label = "Even Realities Smart Glasses (Name)",
        deviceNamePrefix = "Even G",
        baseConfidence = ConfidenceLevel.MEDIUM
      ),
      BleSignature(
        id = "rayban_name",
        companyId = null,
        label = "Ray-Ban Meta (Device Name)",
        deviceNamePatterns = listOf("Ray-Ban", "RayBan", "Ray Ban", "META_RB"),
        baseConfidence = ConfidenceLevel.MEDIUM
      )
    )

  @Test
  fun highConfidenceRayBanPayload_matchesHexFromNrfConnectAndConfirmsOnFirstPacket() {
    val filter = BleSignalFilter()
    // Hex from spec Section 6: 4D4554415F52425F474C415353 == "META_RB_GLASS"
    val nrfPayload = "4D4554415F52425F474C415353".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val adv =
      BleAdvertisement(
        address = "AA:BB:CC:DD:EE:01",
        rssi = -65,
        manufacturerData = mapOf(0x058E to nrfPayload),
        timestampMs = 1_000L
      )

    val decision = filter.process(adv, signatures, entryThresholdDbm = -72)
    assertEquals("meta_rayban_tech", decision.matchedSignature?.id)
    assertNotNull(decision.device)
    assertTrue(decision.device!!.isAlertActive)
    assertTrue(decision.shouldNotify)
  }

  @Test
  fun metaQuestOn0x058EWithoutGlassesPayload_isRejectedToAvoidFalsePositive() {
    val filter = BleSignalFilter()
    val questPayload = "META_QUEST_3".encodeToByteArray()
    val adv =
      BleAdvertisement(
        address = "AA:BB:CC:DD:EE:02",
        rssi = -55,
        manufacturerData = mapOf(0x058E to questPayload),
        timestampMs = 1_000L
      )

    val decision = filter.process(adv, signatures)
    assertNull(decision.matchedSignature)
    assertNull(decision.device)
    assertFalse(decision.shouldNotify)
  }

  @Test
  fun mediumConfidenceSnapSpectacles_requiresTwoObservationsWithinWindow() {
    val filter = BleSignalFilter()
    val adv1 =
      BleAdvertisement(
        address = "11:22:33:44:55:66",
        rssi = -68,
        manufacturerData = mapOf(0x03C2 to byteArrayOf(0x01)),
        timestampMs = 1_000L
      )
    val adv2 = adv1.copy(timestampMs = 4_000L)

    val d1 = filter.process(adv1, signatures, entryThresholdDbm = -72)
    assertFalse(d1.device!!.isAlertActive)
    assertFalse(d1.shouldNotify)

    val d2 = filter.process(adv2, signatures, entryThresholdDbm = -72)
    assertTrue(d2.device!!.isAlertActive)
    assertTrue(d2.shouldNotify)
  }

  @Test
  fun mediumConfidenceRayNeoAndMeizu_confirmOnTwoObservationsAndAlertInGateMode() {
    val filter = BleSignalFilter()
    val rayNeo1 =
      BleAdvertisement(
        address = "66:77:88:99:AA:01",
        rssi = -66,
        manufacturerData = mapOf(0x0BC6 to byteArrayOf(0x01)),
        timestampMs = 1_000L
      )
    val d1 = filter.process(rayNeo1, signatures, entryThresholdDbm = -72)
    assertEquals("tcl_rayneo", d1.matchedSignature?.id)
    assertFalse(d1.shouldNotify)

    val d2 = filter.process(rayNeo1.copy(timestampMs = 2_500L), signatures, entryThresholdDbm = -72)
    assertTrue(d2.device!!.isAlertActive)
    assertTrue(d2.shouldNotify)

    val meizuGate =
      filter.processGateScreening(
        BleAdvertisement(
          address = "66:77:88:99:AA:02",
          rssi = -52,
          manufacturerData = mapOf(0x03AB to byteArrayOf(0x01)),
          timestampMs = 3_000L
        ),
        signatures
      )
    assertEquals("meizu_myvu", meizuGate.matchedSignature?.id)
    assertEquals(GateStatus.ALERT, meizuGate.status)
    assertTrue(meizuGate.shouldVibrate)
  }

  @Test
  fun lowConfidenceCompanyId_requiresThreeObservationsInside8SecondWindow() {
    val filter = BleSignalFilter(windowMs = 8_000L)
    val base =
      BleAdvertisement(
        address = "22:33:44:55:66:77",
        rssi = -65,
        manufacturerData = mapOf(0x01AB to byteArrayOf(0x02)),
        timestampMs = 1_000L
      )

    // Observation 1 at t=1s
    assertFalse(filter.process(base, signatures).shouldNotify)
    // Observation 2 at t=10s -> t=1s falls outside 8s window, count becomes 1
    assertFalse(filter.process(base.copy(timestampMs = 10_000L), signatures).shouldNotify)
    // Observation 3 at t=12s -> count=2
    assertFalse(filter.process(base.copy(timestampMs = 12_000L), signatures).shouldNotify)
    // Observation 4 at t=14s -> count=3 within [6s..14s] -> confirmed!
    val confirmed = filter.process(base.copy(timestampMs = 14_000L), signatures)
    assertTrue(confirmed.device!!.isAlertActive)
    assertTrue(confirmed.shouldNotify)
  }

  @Test
  fun emaSmoothingAndHysteresis_preventsBoundaryChatter() {
    val filter = BleSignalFilter()
    // Luxottica 0x0D53 (HIGH -> 1 observation). Entry = -72, Exit = -80.
    val adv1 =
      BleAdvertisement(
        address = "33:44:55:66:77:88",
        rssi = -70,
        manufacturerData = mapOf(0x0D53 to byteArrayOf(0x01)),
        timestampMs = 1_000L
      )
    val d1 = filter.process(adv1, signatures, entryThresholdDbm = -72, cooldownMs = 10_000L)
    assertTrue(d1.device!!.isAlertActive)
    assertEquals(-70.0, d1.device.smoothedRssi, 0.001)

    // Raw -90 -> smoothed = 0.3 * (-90) + 0.7 * (-70) = -27 - 49 = -76.0 (>= -80 exit)
    val d2 = filter.process(adv1.copy(rssi = -90, timestampMs = 2_000L), signatures, entryThresholdDbm = -72)
    assertEquals(-76.0, d2.device!!.smoothedRssi, 0.001)
    assertTrue(d2.device.isAlertActive)
    // Still inside 10s cooldown -> shouldNotify false
    assertFalse(d2.shouldNotify)

    // Drop further: raw -95 -> smoothed = 0.3 * (-95) + 0.7 * (-76) = -28.5 - 53.2 = -81.7 (< -80 exit)
    val d3 = filter.process(adv1.copy(rssi = -95, timestampMs = 3_000L), signatures, entryThresholdDbm = -72)
    assertEquals(-81.7, d3.device!!.smoothedRssi, 0.001)
    assertFalse(d3.device.isAlertActive)
  }

  @Test
  fun whitelistAndServiceUuidAndCooldown_behaveAsExpected() {
    val filter = BleSignalFilter()
    val heyCyan =
      BleAdvertisement(
        address = "44:55:66:77:88:99",
        rssi = -60,
        serviceUuids = listOf("7905fff0-b5ce-4e99-a40f-4b1e122d00d0"),
        timestampMs = 1_000L
      )

    // First confirmed alert at t=1s
    val first = filter.process(heyCyan, signatures, cooldownMs = 10_000L)
    assertTrue(first.shouldNotify)

    // Within cooldown at t=6s
    val withinCooldown = filter.process(heyCyan.copy(timestampMs = 6_000L), signatures, cooldownMs = 10_000L)
    assertFalse(withinCooldown.shouldNotify)

    // Past cooldown at t=11s
    val pastCooldown = filter.process(heyCyan.copy(timestampMs = 11_000L), signatures, cooldownMs = 10_000L)
    assertTrue(pastCooldown.shouldNotify)

    // Whitelisted MAC is suppressed and removed from active devices
    val whitelisted =
      filter.process(
        heyCyan.copy(timestampMs = 12_000L),
        signatures,
        whitelistedKeys = setOf("44:55:66:77:88:99")
      )
    assertTrue(whitelisted.isWhitelisted)
    assertNull(whitelisted.device)
    assertFalse(whitelisted.shouldNotify)
  }

  @Test
  fun gateScreening_enforcesMinus55DbmBoundaryImmediatelyWithoutWindowDelay() {
    val filter = BleSignalFilter()
    val payload = "META_RB_GLASS".encodeToByteArray()

    // 3m distance (-70 dBm) -> stays CLEAR
    val farAdv =
      BleAdvertisement(
        address = "AA:BB:CC:DD:EE:10",
        rssi = -70,
        manufacturerData = mapOf(0x058E to payload),
        timestampMs = 1_000L
      )
    val farResult = filter.processGateScreening(farAdv, signatures)
    assertEquals(GateStatus.CLEAR, farResult.status)
    assertFalse(farResult.shouldVibrate)
    assertNotNull(farResult.observedDevice)
    assertEquals("meta_rayban_tech", farResult.observedDevice?.signature?.id)

    // Boundary (-56 dBm) -> still CLEAR
    val edgeResult = filter.processGateScreening(farAdv.copy(rssi = -56, timestampMs = 1_200L), signatures)
    assertEquals(GateStatus.CLEAR, edgeResult.status)
    assertFalse(edgeResult.shouldVibrate)

    // 1m / 30cm (-55 dBm / -45 dBm) -> immediate ALERT on first packet!
    val nearResult = filter.processGateScreening(farAdv.copy(rssi = -55, timestampMs = 1_500L), signatures)
    assertEquals(GateStatus.ALERT, nearResult.status)
    assertTrue(nearResult.shouldVibrate)
    assertEquals(1L, nearResult.vibrationNonce)
  }

  @Test
  fun gateScreening_distinguishesCautionFromAlertAndRespects2sCooldownAndManualReset() {
    val filter = BleSignalFilter()

    // Generic company ID 0x01AB at -48 dBm -> CAUTION
    val genericAdv =
      BleAdvertisement(
        address = "AA:BB:CC:DD:EE:20",
        rssi = -48,
        manufacturerData = mapOf(0x01AB to byteArrayOf(0x01)),
        timestampMs = 1_000L
      )
    val caution = filter.processGateScreening(genericAdv, signatures)
    assertEquals(GateStatus.CAUTION, caution.status)
    assertTrue(caution.shouldVibrate)

    // Within 2s cooldown -> status holds, shouldVibrate false
    val cooldownCheck = filter.processGateScreening(genericAdv.copy(timestampMs = 2_000L), signatures)
    assertEquals(GateStatus.CAUTION, cooldownCheck.status)
    assertFalse(cooldownCheck.shouldVibrate)

    // High-confidence 0x0D53 upgrades CAUTION -> ALERT after 2s cooldown
    val rayBanAdv =
      BleAdvertisement(
        address = "AA:BB:CC:DD:EE:21",
        rssi = -46,
        manufacturerData = mapOf(0x0D53 to byteArrayOf(0x01)),
        timestampMs = 3_500L
      )
    val alert = filter.processGateScreening(rayBanAdv, signatures)
    assertEquals(GateStatus.ALERT, alert.status)
    assertTrue(alert.shouldVibrate)

    // Subsequent generic 0x01AB packet must NOT downgrade ALERT to CAUTION
    val downgradeAttempt =
      filter.processGateScreening(genericAdv.copy(timestampMs = 6_000L), signatures)
    assertEquals(GateStatus.ALERT, downgradeAttempt.status)
    assertFalse(downgradeAttempt.shouldVibrate)

    // Manual "Next Examinee (Reset)" clears state and cooldown immediately
    val reset = filter.resetGateScreening(nowMs = 6_100L)
    assertEquals(GateStatus.CLEAR, reset.status)

    // Next examinee at t=6.2s vibrates immediately without waiting 2s
    val nextExaminee = filter.processGateScreening(genericAdv.copy(timestampMs = 6_200L), signatures)
    assertEquals(GateStatus.CAUTION, nextExaminee.status)
    assertTrue(nextExaminee.shouldVibrate)
  }

  @Test
  fun rokidServiceUuid_confirmsOnFirstObservationRegardlessOfDeviceName() {
    val filter = BleSignalFilter()
    val rokidAdv =
      BleAdvertisement(
        address = "55:66:77:88:99:01",
        deviceName = null,
        rssi = -62,
        serviceUuids = listOf("00009100-0000-1000-8000-00805f9b34fb"),
        timestampMs = 1_000L
      )

    val decision = filter.process(rokidAdv, signatures, entryThresholdDbm = -72)
    assertEquals("rokid_ai_glasses", decision.matchedSignature?.id)
    assertTrue(decision.device!!.isAlertActive)
    assertTrue(decision.shouldNotify)
  }

  @Test
  fun daEchoServiceData_matchesExactBytesAndRejectsGenericFea8WithoutMatchingData() {
    val filter = BleSignalFilter()
    val uuid = "0000fea8-0000-1000-8000-00805f9b34fb"

    val unrelatedFea8 =
      BleAdvertisement(
        address = "55:66:77:88:99:02",
        rssi = -58,
        serviceUuids = listOf(uuid),
        serviceData = mapOf(uuid to byteArrayOf(0x00, 0x01, 0x02)),
        timestampMs = 1_000L
      )
    val rejected = filter.process(unrelatedFea8, signatures)
    assertNull(rejected.matchedSignature)
    assertFalse(rejected.shouldNotify)

    val daEchoAdv =
      BleAdvertisement(
        address = "55:66:77:88:99:03",
        rssi = -58,
        serviceData = mapOf(uuid to byteArrayOf(0x41, 0x31, 0x49, 0x01, 0x00, 0x00)),
        timestampMs = 2_000L
      )
    val accepted = filter.process(daEchoAdv, signatures, entryThresholdDbm = -72)
    assertEquals("da_echo_glasses", accepted.matchedSignature?.id)
    assertTrue(accepted.device!!.isAlertActive)
    assertTrue(accepted.shouldNotify)
  }

  @Test
  fun heyCyanNamePrefix_confirmsAfterTwoHitsAndYieldsPriorityToServiceUuid() {
    val filter = BleSignalFilter()
    val nameOnlyAdv1 =
      BleAdvertisement(
        address = "55:66:77:88:99:04",
        deviceName = "HeyCyan-WXYZ",
        rssi = -64,
        timestampMs = 1_000L
      )

    val d1 = filter.process(nameOnlyAdv1, signatures, entryThresholdDbm = -72)
    assertEquals("heycyan_name_prefix", d1.matchedSignature?.id)
    assertFalse(d1.shouldNotify)

    val d2 = filter.process(nameOnlyAdv1.copy(timestampMs = 2_500L), signatures, entryThresholdDbm = -72)
    assertTrue(d2.device!!.isAlertActive)
    assertTrue(d2.shouldNotify)

    val uuidAndNameAdv =
      BleAdvertisement(
        address = "55:66:77:88:99:05",
        deviceName = "HeyCyan-WXYZ",
        rssi = -64,
        serviceUuids = listOf("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0"),
        timestampMs = 3_000L
      )
    val prioritized = filter.process(uuidAndNameAdv, signatures, entryThresholdDbm = -72)
    assertEquals("heycyan_sdk", prioritized.matchedSignature?.id)
    assertTrue(prioritized.shouldNotify)
  }

  @Test
  fun payloadHex_matchesBinaryCompanyPayloadAndRejectsNonMatchingBytes() {
    val filter = BleSignalFilter()
    val hexSignatures =
      listOf(
        BleSignature(
          id = "xiaomi_glasses_future",
          companyId = "0x038F",
          label = "Xiaomi AI Glasses (Sample)",
          payloadHex = "A1B2C304",
          baseConfidence = ConfidenceLevel.HIGH
        )
      )

    val genericPhone =
      BleAdvertisement(
        address = "55:66:77:88:99:06",
        rssi = -50,
        manufacturerData = mapOf(0x038F to byteArrayOf(0x10, 0x20, 0x30, 0x40)),
        timestampMs = 1_000L
      )
    assertNull(filter.process(genericPhone, hexSignatures).matchedSignature)

    val matchedBinary =
      BleAdvertisement(
        address = "55:66:77:88:99:07",
        rssi = -50,
        manufacturerData =
          mapOf(0x038F to byteArrayOf(0x01, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0x04)),
        timestampMs = 2_000L
      )
    val matched = filter.process(matchedBinary, hexSignatures, entryThresholdDbm = -72)
    assertEquals("xiaomi_glasses_future", matched.matchedSignature?.id)
    assertTrue(matched.shouldNotify)
  }

  @Test
  fun vettedEspGlassholeSignatures_matchWithoutFalsePositives() {
    val filter = BleSignalFilter()

    // Vuzix 0x060C confirms immediately (HIGH)
    val vuzix =
      filter.process(
        BleAdvertisement(
          address = "66:77:88:99:AA:10",
          rssi = -60,
          manufacturerData = mapOf(0x060C to byteArrayOf(0x01)),
          timestampMs = 1_000L
        ),
        signatures,
        entryThresholdDbm = -72
      )
    assertEquals("vuzix_corp", vuzix.matchedSignature?.id)
    assertTrue(vuzix.shouldNotify)

    // Meta 0xFD5F requires 2 hits (MEDIUM)
    val metaFd5f =
      BleAdvertisement(
        address = "66:77:88:99:AA:11",
        rssi = -60,
        serviceUuids = listOf("0000fd5f-0000-1000-8000-00805f9b34fb"),
        timestampMs = 2_000L
      )
    assertFalse(filter.process(metaFd5f, signatures, entryThresholdDbm = -72).shouldNotify)
    val metaFd5fConfirmed = filter.process(metaFd5f.copy(timestampMs = 3_000L), signatures, entryThresholdDbm = -72)
    assertEquals("meta_ble_service", metaFd5fConfirmed.matchedSignature?.id)
    assertTrue(metaFd5fConfirmed.shouldNotify)

    // "Even G*" prefix matches "Even G1" without matching substring false positives like "Steven Gold"
    assertNull(
      filter.process(
        BleAdvertisement(address = "66:77:88:99:AA:12", deviceName = "Steven Gold", rssi = -55, timestampMs = 4_000L),
        signatures
      ).matchedSignature
    )
    val evenG1 =
      BleAdvertisement(
        address = "66:77:88:99:AA:13",
        deviceName = "Even G1_9F2A",
        rssi = -60,
        timestampMs = 5_000L
      )
    assertFalse(filter.process(evenG1, signatures, entryThresholdDbm = -72).shouldNotify)
    val evenG1Confirmed = filter.process(evenG1.copy(timestampMs = 6_000L), signatures, entryThresholdDbm = -72)
    assertEquals("even_realities_name", evenG1Confirmed.matchedSignature?.id)
    assertTrue(evenG1Confirmed.shouldNotify)

    // deviceNamePatterns matches substring tokens like "Ray-Ban"
    val rayBanName =
      BleAdvertisement(
        address = "66:77:88:99:AA:14",
        deviceName = "My Ray-Ban Stories",
        rssi = -60,
        timestampMs = 7_000L
      )
    assertFalse(filter.process(rayBanName, signatures, entryThresholdDbm = -72).shouldNotify)
    val rayBanNameConfirmed = filter.process(rayBanName.copy(timestampMs = 8_000L), signatures, entryThresholdDbm = -72)
    assertEquals("rayban_name", rayBanNameConfirmed.matchedSignature?.id)
    assertTrue(rayBanNameConfirmed.shouldNotify)
  }

  @Test
  fun spectacleConfigSignatures_prioritizeSpecificPayloadAndMatchEvenAndMonocle() {
    val filter = BleSignalFilter()
    val expanded =
      signatures +
        listOf(
          BleSignature(
            id = "meta_rayban_inc_confirmed",
            companyId = "0x01AB",
            label = "Ray-Ban Meta (Meta Platforms Inc)",
            payloadAscii = "META_RB_GLASS",
            baseConfidence = ConfidenceLevel.HIGH
          ),
          BleSignature(
            id = "even_realities_company",
            companyId = "0x10F9",
            label = "Even Realities G1/G2",
            category = GlassesCategory.DISPLAY_AR,
            baseConfidence = ConfidenceLevel.MEDIUM
          ),
          BleSignature(
            id = "brilliant_monocle",
            companyId = null,
            label = "Brilliant Labs (Monocle / Frame)",
            deviceNamePrefix = "Monocle",
            baseConfidence = ConfidenceLevel.MEDIUM
          )
        )

    // 0x01AB + META_RB_GLASS promotes over generic 0x01AB LOW and confirms on 1st hit
    val metaInc =
      filter.process(
        BleAdvertisement(
          address = "66:77:88:99:AA:20",
          rssi = -58,
          manufacturerData = mapOf(0x01AB to "META_RB_GLASS".encodeToByteArray()),
          timestampMs = 1_000L
        ),
        expanded,
        entryThresholdDbm = -72
      )
    assertEquals("meta_rayban_inc_confirmed", metaInc.matchedSignature?.id)
    assertTrue(metaInc.shouldNotify)

    // Even Realities 0x10F9 classifies as DISPLAY_AR (2 obs)
    val evenCompanyAdv =
      BleAdvertisement(
        address = "66:77:88:99:AA:21",
        rssi = -58,
        manufacturerData = mapOf(0x10F9 to byteArrayOf(0x01)),
        timestampMs = 2_000L
      )
    assertFalse(filter.process(evenCompanyAdv, expanded, entryThresholdDbm = -72).shouldNotify)
    val evenCompanyConfirmed =
      filter.process(evenCompanyAdv.copy(timestampMs = 3_000L), expanded, entryThresholdDbm = -72)
    assertEquals("even_realities_company", evenCompanyConfirmed.matchedSignature?.id)
    assertEquals(GlassesCategory.DISPLAY_AR, evenCompanyConfirmed.matchedSignature?.category)
    assertTrue(evenCompanyConfirmed.shouldNotify)

    // Brilliant Labs Monocle prefix classifies as CAMERA (2 obs)
    val monocleAdv =
      BleAdvertisement(
        address = "66:77:88:99:AA:22",
        deviceName = "Monocle-AB12",
        rssi = -58,
        timestampMs = 4_000L
      )
    assertFalse(filter.process(monocleAdv, expanded, entryThresholdDbm = -72).shouldNotify)
    val monocleConfirmed =
      filter.process(monocleAdv.copy(timestampMs = 5_000L), expanded, entryThresholdDbm = -72)
    assertEquals("brilliant_monocle", monocleConfirmed.matchedSignature?.id)
    assertEquals(GlassesCategory.CAMERA, monocleConfirmed.matchedSignature?.category)
    assertTrue(monocleConfirmed.shouldNotify)
  }
}
