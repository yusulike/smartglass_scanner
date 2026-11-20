package com.windyflow.smartglassscanner.ble

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureRepositoryTest {
  @Test
  fun parseBundledJson_readsAllTwentyTwoSignaturesAndIdentifiers() {
    val jsonFile = File("src/main/assets/signatures.json")
    val raw =
      if (jsonFile.exists()) {
        jsonFile.readText()
      } else {
        File("app/src/main/assets/signatures.json").readText()
      }

    val db = SignatureJsonParser.parse(raw)
    assertEquals(1, db.version)
    assertEquals(25, db.signatures.size)

    val rayBan = db.signatures.first { it.id == "meta_rayban_tech" }
    assertEquals(0x058E, rayBan.companyIdInt())
    assertEquals("META_RB_GLASS", rayBan.payloadAscii)
    assertEquals(ConfidenceLevel.HIGH, rayBan.baseConfidence)
    assertEquals(1, rayBan.requiredObservations())

    val luxottica = db.signatures.first { it.id == "luxottica_rayban" }
    assertEquals(0x0D53, luxottica.companyIdInt())
    assertEquals(1, luxottica.requiredObservations())

    val snap = db.signatures.first { it.id == "snap_spectacles" }
    assertEquals(0x03C2, snap.companyIdInt())
    assertEquals(2, snap.requiredObservations())

    val rayNeo = db.signatures.first { it.id == "tcl_rayneo" }
    assertEquals(0x0BC6, rayNeo.companyIdInt())
    assertEquals(ConfidenceLevel.MEDIUM, rayNeo.baseConfidence)
    assertEquals(2, rayNeo.requiredObservations())

    val meizu = db.signatures.first { it.id == "meizu_myvu" }
    assertEquals(0x03AB, meizu.companyIdInt())
    assertEquals(ConfidenceLevel.MEDIUM, meizu.baseConfidence)
    assertEquals(2, meizu.requiredObservations())

    val metaGeneral = db.signatures.first { it.id == "meta_platforms_general" }
    assertEquals(0x01AB, metaGeneral.companyIdInt())
    assertEquals(3, metaGeneral.requiredObservations())

    val metaIncConfirmed = db.signatures.first { it.id == "meta_rayban_inc_confirmed" }
    assertEquals(0x01AB, metaIncConfirmed.companyIdInt())
    assertEquals("META_RB_GLASS", metaIncConfirmed.payloadAscii)
    assertEquals(ConfidenceLevel.HIGH, metaIncConfirmed.baseConfidence)

    val heyCyan = db.signatures.first { it.id == "heycyan_sdk" }
    assertNull(heyCyan.companyIdInt())
    assertEquals("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0", heyCyan.serviceUuid)

    val rokid = db.signatures.first { it.id == "rokid_ai_glasses" }
    assertNull(rokid.companyIdInt())
    assertEquals("00009100-0000-1000-8000-00805F9B34FB", rokid.serviceUuid)
    assertEquals(ConfidenceLevel.HIGH, rokid.baseConfidence)
    assertEquals(1, rokid.requiredObservations())

    val daEcho = db.signatures.first { it.id == "da_echo_glasses" }
    assertEquals("0000FEA8-0000-1000-8000-00805F9B34FB", daEcho.serviceUuid)
    assertEquals("413149010000", daEcho.serviceDataHex)
    assertEquals(
      listOf<Byte>(0x41, 0x31, 0x49, 0x01, 0x00, 0x00),
      daEcho.serviceDataBytes()?.toList()
    )
    assertEquals(ConfidenceLevel.HIGH, daEcho.baseConfidence)

    val heyCyanName = db.signatures.first { it.id == "heycyan_name_prefix" }
    assertEquals("HeyCyan-", heyCyanName.deviceNamePrefix)
    assertEquals(ConfidenceLevel.MEDIUM, heyCyanName.baseConfidence)
    assertEquals(2, heyCyanName.requiredObservations())

    val vuzix = db.signatures.first { it.id == "vuzix_corp" }
    assertEquals(0x060C, vuzix.companyIdInt())
    assertEquals(ConfidenceLevel.HIGH, vuzix.baseConfidence)

    val metaBleService = db.signatures.first { it.id == "meta_ble_service" }
    assertEquals("0000FD5F-0000-1000-8000-00805F9B34FB", metaBleService.serviceUuid)
    assertEquals(ConfidenceLevel.MEDIUM, metaBleService.baseConfidence)

    val brandNames = db.signatures.first { it.id == "glasses_brand_names" }
    assertTrue(brandNames.deviceNamePatterns.orEmpty().contains("Rokid"))
    assertTrue(brandNames.deviceNamePatterns.orEmpty().contains("AirGo"))
    assertEquals(GlassesCategory.CAMERA, brandNames.category)

    val evenCompany = db.signatures.first { it.id == "even_realities_company" }
    assertEquals(0x10F9, evenCompany.companyIdInt())
    assertEquals(GlassesCategory.DISPLAY_AR, evenCompany.category)

    val evenRealities = db.signatures.first { it.id == "even_realities_name" }
    assertEquals(GlassesCategory.DISPLAY_AR, evenRealities.category)

    val brilliantMonocle = db.signatures.first { it.id == "brilliant_monocle" }
    assertEquals("Monocle", brilliantMonocle.deviceNamePrefix)
    assertEquals(GlassesCategory.CAMERA, brilliantMonocle.category)

    val xreal = db.signatures.first { it.id == "xreal_name" }
    assertEquals(GlassesCategory.DISPLAY_AR, xreal.category)

    val echoFocals = db.signatures.first { it.id == "echo_frames_focals_name" }
    assertEquals(GlassesCategory.DISPLAY_AR, echoFocals.category)
    assertTrue(echoFocals.deviceNamePatterns.orEmpty().contains("Echo Frames"))

    val jieli = db.signatures.first { it.id == "zhuhai_jieli" }
    assertEquals("Zhuhai Jieli Generic Chipset (Audio / Glasses)", jieli.label)
    assertEquals(ConfidenceLevel.LOW, jieli.baseConfidence)
    assertTrue(jieli.isBuiltInGenericLow())
    assertEquals("companyId=0x05D6", jieli.matchEvidenceSummary())

    assertEquals("companyId=0x058E+payload=META_RB_GLASS", rayBan.matchEvidenceSummary())
    assertEquals(
      "serviceUuid=7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
      heyCyan.matchEvidenceSummary()
    )
    assertFalse(rayBan.isBuiltInGenericLow())
  }

  @Test
  fun parseHexBytes_handlesFormattedAndInvalidStrings() {
    assertEquals(
      listOf<Byte>(0x41, 0x31, 0x49, 0x01, 0x00, 0x00),
      BleSignature.parseHexBytes("0x41, 0x31, 0x49, 0x01, 0x00, 0x00")?.toList()
    )
    assertEquals(
      listOf<Byte>(0x12, 0xAB.toByte()),
      BleSignature.parseHexBytes("12 ab")?.toList()
    )
    assertNull(BleSignature.parseHexBytes(null))
    assertNull(BleSignature.parseHexBytes(""))
    assertNull(BleSignature.parseHexBytes("ABC"))
    assertNull(BleSignature.parseHexBytes("ZZ"))
  }

  @Test
  fun normalizeHexCompanyId_acceptsValidRangeAndRejectsInvalid() {
    assertEquals("0x058E", SignatureJsonParser.normalizeHexCompanyId("58e"))
    assertEquals("0x058E", SignatureJsonParser.normalizeHexCompanyId("0x058E"))
    assertEquals("0x00AB", SignatureJsonParser.normalizeHexCompanyId("0Xab"))
    assertEquals("0x0000", SignatureJsonParser.normalizeHexCompanyId("0"))
    assertEquals("0xFFFF", SignatureJsonParser.normalizeHexCompanyId("ffff"))

    assertNull(SignatureJsonParser.normalizeHexCompanyId(""))
    assertNull(SignatureJsonParser.normalizeHexCompanyId("0x"))
    assertNull(SignatureJsonParser.normalizeHexCompanyId("ZZZZ"))
    assertNull(SignatureJsonParser.normalizeHexCompanyId("12345"))
    assertNull(SignatureJsonParser.normalizeHexCompanyId("-01"))
  }

  @Test
  fun addAndRemoveCustomHexId_mergesReactivelyIntoActiveSignatures() = runTest {
    val dataStore = InMemoryPreferencesStore()
    val bundled =
      listOf(
        BleSignature(
          id = "luxottica_rayban",
          companyId = "0x0D53",
          label = "Ray-Ban Meta Frame (Luxottica)",
          baseConfidence = ConfidenceLevel.HIGH
        )
      )
    val repo = SignatureRepository(dataStore = dataStore, bundledLoader = { bundled })

    assertEquals(1, repo.activeSignatures.first().size)

    val invalidResult = repo.addCustomHexId("XYZ1")
    assertTrue(invalidResult.isFailure)

    val validResult = repo.addCustomHexId("12ab")
    assertEquals("0x12AB", validResult.getOrNull())

    val merged = repo.activeSignatures.first()
    assertEquals(2, merged.size)
    assertEquals("0x12AB", merged.last().companyId)
    assertTrue(merged.last().isCustom)
    assertEquals(ConfidenceLevel.LOW, merged.last().baseConfidence)

    repo.removeCustomHexId("0x12AB")
    assertEquals(1, repo.activeSignatures.first().size)
  }

  @Test
  fun genericLowSignatureToggle_filtersOnlyBuiltInLowAndPreservesCustomHexIds() = runTest {
    val dataStore = InMemoryPreferencesStore()
    val bundled =
      listOf(
        BleSignature(
          id = "meta_rayban_tech",
          companyId = "0x058E",
          label = "Ray-Ban Meta (Meta Platforms Tech)",
          payloadAscii = "META_RB_GLASS",
          baseConfidence = ConfidenceLevel.HIGH
        ),
        BleSignature(
          id = "meta_platforms_general",
          companyId = "0x01AB",
          label = "Meta Platforms Device",
          baseConfidence = ConfidenceLevel.LOW
        ),
        BleSignature(
          id = "zhuhai_jieli",
          companyId = "0x05D6",
          label = "Zhuhai Jieli Generic Chipset (Audio / Glasses)",
          baseConfidence = ConfidenceLevel.LOW
        )
      )
    val repo = SignatureRepository(dataStore = dataStore, bundledLoader = { bundled })

    assertTrue(repo.includeGenericLowSignatures.first())
    repo.addCustomHexId("1234")

    val defaultActiveIds = repo.activeSignatures.first().map { it.id }
    assertEquals(
      listOf("meta_rayban_tech", "meta_platforms_general", "zhuhai_jieli", "custom_0x1234"),
      defaultActiveIds
    )

    repo.setIncludeGenericLowSignatures(false)
    assertFalse(repo.includeGenericLowSignatures.first())
    assertEquals(listOf("meta_rayban_tech", "custom_0x1234"), repo.activeSignatures.first().map { it.id })

    repo.setIncludeGenericLowSignatures(true)
    assertTrue(repo.includeGenericLowSignatures.first())
    assertEquals(
      listOf("meta_rayban_tech", "meta_platforms_general", "zhuhai_jieli", "custom_0x1234"),
      repo.activeSignatures.first().map { it.id }
    )
  }

  private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val mutex = Mutex()
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
      mutex.withLock {
        val next = transform(state.value)
        state.value = next
        next
      }
  }
}
