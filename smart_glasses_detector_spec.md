# Smart Glasses Detector (Android Native) 기술 명세서

> **문서 상태**: v1.0 MVP + 튜닝 포인트 + 입실 스크리닝 모드 + 앱 사이닝 + 커스텀 앱 아이콘 + Windows 테스트 비콘 구현 완료 (현재 코드베이스 기준 현행화)

Native Kotlin(Jetpack Compose) 기반 스마트글래스 근접 탐지 Android 앱을 구현하고, **위치 권한 없이(BLE 전용)** Google Play v1.0 정식 출시 심사를 통과하기 위한 개발 명세서입니다.

---

## 1. 프로젝트 목표 및 기술 스택

* **v1.0 MVP 목표**: 주변 스마트글래스(Ray-Ban Meta, Snap Spectacles 등)의 BLE 광고 패킷을 백그라운드에서 상시 탐지하고, 오탐 필터링을 거쳐 사용자에게 알림을 제공하는 **BLE 전용 Google Play 출시용** 앱 개발
* **빌드 및 SDK 구성 (`com.windyflow.glassesdetector`, v1.0 / `versionCode = 1`)**:
  * `minSdk = 26` (Android 8.0), `compileSdk = 37`, `targetSdk = 37`, Java `17`
  * AGP `9.0.1`, Kotlin `2.3.20`
* **언어 및 UI**: Kotlin, Jetpack Compose (BOM `2026.03.01`, Material 3, `material-icons-extended`), AndroidX Navigation 3 (`1.0.1`)
* **비동기 및 상태 관리**: Kotlin Coroutines (`1.10.2`), `StateFlow` / `SharedFlow`, `kotlinx.serialization` (`1.13.0`), Jetpack DataStore Preferences (`1.3.0`)
* **백그라운드 실행**: `ForegroundService` (`foregroundServiceType="connectedDevice"`)

### 실제 패키지 구조 (현행)

```text
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── signatures.json                 # 기본 스마트글래스 BLE 시그니처 DB (22종, CAMERA / DISPLAY_AR)
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_background.xml  # Adaptive 배경 (네이비 그라데이션 + 레이더 링)
│   │   ├── ic_launcher_foreground.xml  # Adaptive 전경 (스마트글래스 + BLE 아크 + LED 도트)
│   │   ├── ic_launcher_monochrome.xml  # Android 13+ 테마 아이콘용 단색 전경
│   │   └── ic_stat_glasses_scan.xml    # 24dp 알림(Status Bar) 전용 단색 아이콘
│   └── mipmap-*/                       # anydpi-v26 XML 및 mdpi~xxxhdpi WebP 아이콘
└── kotlin/com/windyflow/glassesdetector/
    ├── MainActivity.kt                 # Edge-to-edge 호스트 액티비티
    ├── Navigation.kt                   # Navigation 3 NavDisplay 라우팅
    ├── NavigationKeys.kt               # Main / GateScreening / Settings NavKey
    ├── ble/
    │   ├── BleModels.kt                # ScanProfile, BleScanMode, GateStatus/Result, BleSignature
    │   ├── BleSignalFilter.kt          # EMA 스무딩, 히스테리시스, 유연 N-of-M, Gate 즉시 판정
    │   ├── PermissionHelper.kt         # API 레벨별 BLE-only 런타임 권한 헬퍼
    │   ├── SignatureRepository.kt      # assets/signatures.json 로드 + 커스텀 Hex ID 병합
    │   └── SmartGlassesScanner.kt      # BluetoothLeScanner 래퍼, 동적 ScanFilter, 6.5s 스로틀 가드
    ├── service/
    │   └── BleScannerService.kt        # Android 14+ connectedDevice ForegroundService & 알림
    ├── data/
    │   ├── AppPreferences.kt           # 공용 DataStore("glasses_detector_prefs")
    │   ├── DetectionStateHolder.kt     # 서비스↔UI 상태 브리지, detection_logs.json(500건) 영속 저장 & .txt 포맷터
    │   └── WhitelistRepository.kt      # DataStore 기반 RSSI/쿨다운/화이트리스트 저장
    └── ui/
        ├── main/
        │   ├── MainScreen.kt           # 스캔 토글, 모드 배지, 탐지 카드, 화이트리스트 추가
        │   └── MainScreenViewModel.kt
        ├── gate/
        │   ├── GateScreeningScreen.kt  # 시험장 입실 스크리닝 모드(초근접·진동·컬러 인디케이터)
        │   └── GateScreeningViewModel.kt
        ├── settings/
        │   ├── SettingsScreen.kt       # RSSI/쿨다운 슬라이더, 커스텀 Hex ID, 화이트리스트, .txt 내보내기
        │   └── SettingsViewModel.kt
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
```

---

## 2. 권한 및 Google Play 심사 준수 (`AndroidManifest.xml`)

v1.0에서는 Wi-Fi 스캔을 제외하여 API 31+ 환경에서 `ACCESS_FINE_LOCATION` 요청을 완전히 없앱니다. Android 12+에서는 `BLUETOOTH_SCAN`에 `neverForLocation` 플래그를 적용하고([Android Developers](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)), 하위 버전용 위치 권한은 `maxSdkVersion="30"`으로 제한합니다([Offline Protocol](https://www.offlineprotocol.com/learning/bluetooth-le/bluetooth-permissions-on-android-explained)). [PermissionHelper.kt](file:///e:/myWork/projects/smartglass_scanner/app/src/main/kotlin/com/windyflow/glassesdetector/ble/PermissionHelper.kt)도 SDK 버전별로 API 31+에서는 `ACCESS_FINE_LOCATION`을 절대 요청하지 않도록 분기합니다.

```xml
<uses-feature
    android:name="android.hardware.bluetooth_le"
    android:required="false" />

<!-- Android 11 이하 호환용 (API 31+에서는 미요청) -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />

<!-- Android 12+ (API 31+) -->
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />

<!-- Android 13+ (API 33+) 알림 표시 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 입실 스크리닝 모드 무음 진동 피드백 (일반 권한) -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Android 14+ (API 34+) BLE 백그라운드 스캔용 포그라운드 서비스 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<application ...>
    <service
        android:name=".service.BleScannerService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="connectedDevice" />
</application>
```

> **참고**: `neverForLocation` 선언 시 OS가 일부 비콘 패킷을 필터링할 수 있으므로([Android Developers](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)), 실기기 테스트에서 타깃 기기가 누락될 경우 플래그 제거 여부를 검토해야 합니다.

### Google Play Console 필수 제출 항목

* **포그라운드 서비스 선언 (`connectedDevice`)**: Android 14+ 타깃 앱은 Play Console(**정책 > 앱 콘텐츠**)에서 서비스 사용 사유, 시스템 중단 시 사용자 영향, 그리고 **앱 내 스캔 시작 → 상단 고정 알림 표시 → 사용자가 즉시 중지할 수 있는 흐름이 담긴 시연 영상 링크**를 제출해야 합니다([Google Play 고객센터](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en), [Android Developers](https://developer.android.com/about/versions/14/changes/fgs-types-required)).
* **데이터 보안(Data Safety)**: 스캔한 MAC 주소 및 제조사 ID는 외부 서버로 전송하지 않고 기기 내부 저장소(`files/detection_logs.json`, 최대 500건) 및 사용자 선택 시 `.txt` 파일로만 저장·처리합니다.

---

## 3. JSON 기반 시그니처 DB 및 커스텀 ID 병합

신규 스마트글래스 모델 대응과 유지보수를 위해 시그니처 목록을 코드에 하드코딩하지 않고 `assets/signatures.json`으로 분리합니다. `SignatureRepository`는 앱 시작 시 JSON을 파싱하고, 사용자가 설정 화면에서 입력한 커스텀 Hex ID(`DataStore` 저장)를 `Flow`로 병합해 스캐너에 실시간 전달합니다.

### 3.1 `assets/signatures.json` 스키마

```json
{
  "version": 1,
  "signatures": [
    {
      "id": "meta_rayban_tech",
      "companyId": "0x058E",
      "label": "Ray-Ban Meta (Meta Platforms Tech)",
      "payloadAscii": "META_RB_GLASS",
      "serviceUuid": null,
      "baseConfidence": "HIGH"
    },
    {
      "id": "luxottica_rayban",
      "companyId": "0x0D53",
      "label": "Ray-Ban Meta Frame (Luxottica)",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "HIGH"
    },
    {
      "id": "meta_platforms_general",
      "companyId": "0x01AB",
      "label": "Meta Platforms Device",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "LOW"
    },
    {
      "id": "snap_spectacles",
      "companyId": "0x03C2",
      "label": "Snap Spectacles",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "MEDIUM"
    },
    {
      "id": "tcl_rayneo",
      "companyId": "0x0BC6",
      "label": "TCL / RayNeo Smart Glasses",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "MEDIUM"
    },
    {
      "id": "meizu_myvu",
      "companyId": "0x03AB",
      "label": "Meizu MYVU AR Glasses",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "MEDIUM"
    },
    {
      "id": "zhuhai_jieli",
      "companyId": "0x05D6",
      "label": "Zhuhai Jieli Chipset Glasses",
      "payloadAscii": null,
      "serviceUuid": null,
      "baseConfidence": "LOW"
    },
    {
      "id": "heycyan_sdk",
      "companyId": null,
      "label": "HeyCyan SDK Smart Glasses",
      "payloadAscii": null,
      "serviceUuid": "7905FFF0-B5CE-4E99-A40F-4B1E122D00D0",
      "baseConfidence": "HIGH"
    }
  ]
}
```

* `0x058E`는 `META_RB_GLASS` 페이로드 일치 시 Quest VR 헤드셋과의 오탐을 배제할 수 있고([Hackster.io](https://www.hackster.io/kevin-loeffler/spectacle-a-smart-glasses-detecting-keychain-576576)), `0x0D53` 및 `0x060C`(Vuzix)는 안경 전문 제조사 ID이므로 추가 페이로드 없이도 높은 신뢰도로 판정합니다([Nearby Glasses](https://github.com/yjeanrenaud/yj_nearbyglasses)).
* 중국계 및 AR 스마트글래스 시그니처로 `Rokid AI Glasses`(Service UUID `00009100-...`), `Da Echo`(`0xFEA8` + Service Data `413149010000`), `TCL / RayNeo`(`0x0BC6`), `Meizu MYVU`(`0x03AB`, `DISPLAY_AR`), `Kopin / Solos`(`0x041F`), `North Focals`(`0x0562`, `DISPLAY_AR`), `Meta BLE Service`(`0xFD5F`), 그리고 기기명 접두사/패턴(`HeyCyan-`, `Even G`, `INMO`, `XREAL`/`NREAL`, `Ray-Ban`, `Spectacles`, `Echo Frames`/`Focals`, `Rokid`/`Vuzix`/`RayNeo`/`Moverio`/`Solos`)까지 총 22종을 내장했습니다([ESP-GlassHole](https://github.com/surveillancewatch/ESP-GlassHole), [Nearby Glasses](https://github.com/yjeanrenaud/yj_nearbyglasses)).
* `Xiaomi AI Glasses`(`0x038F`)는 샤오미 스마트폰·밴드·IoT 전 제품이 ID를 공유해 단독 매칭 시 오탐이 다발하므로 실기기 패킷(`Xiaomi` 전용 페이로드/서비스) 확보 후 추가 반영할 예정입니다([Bluetooth Assigned Numbers](https://www.bluetooth.com/wp-content/uploads/Files/Specification/Assigned_Numbers.pdf)).

### 3.2 `SignatureRepository.kt` 구현 명세

* `BleSignature` 데이터 모델에 `companyIdInt(): Int?` 헬퍼를 두어 `"0x058E"` 문자열을 16진수 정수로 안전하게 변환합니다.
* `bundledSignatures`는 `context.assets.open("signatures.json")`으로 1회 지연 로드(`lazy`)합니다.
* `activeSignatures: Flow<List<BleSignature>>`는 `DataStore`의 `stringSetPreferencesKey("custom_company_ids")`를 구독해 `bundledSignatures + customList`를 방출합니다.
* `addCustomHexId(hex)` 호출 시 `SignatureJsonParser.normalizeHexCompanyId`로 `0xXXXX` 4자리 대문자 형식으로 정규화하고, `0000`~`FFFF`(1~4자리 Hex) 범위만 허용합니다.

---

## 4. 핵심 BLE 탐지 엔진 및 오탐 방지 명세

### 4.1 Android BLE 스캔 제약 방어 (`SmartGlassesScanner.kt`)

1. **동적 `ScanFilter` 및 포그라운드 비필터 분리 (`resolveActiveScanFilters`)**: 화면이 꺼진 `LOW_POWER` 백그라운드 모드에서는 OS 중단 방지를 위해 `companyId`, `serviceUuid`, `serviceDataHex`(`setServiceData`), 그리고 Android 13+(API 33+)의 `deviceNamePrefix`(`0x09` `setAdvertisingDataTypeWithData`) 조건을 중복 제거한 하드웨어 `ScanFilter` 목록으로 전달합니다([Android Developers](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner)). 반면 앱 화면 활성(`BALANCED` / `LOW_LATENCY`) 상태에서 기기명 규칙이 활성화된 경우에는 대소문자 무시 부분 일치(`deviceNamePatterns`)와 Scan Response 기기명을 누락하지 않도록 빈 필터(`emptyList()`) 스캔을 수행합니다.
2. **포그라운드/백그라운드 스캔 모드 분리 (`resolveScanMode`)**:
   * `BACKGROUND_GUARD`에서 앱 화면 포그라운드 활성 시 `SCAN_MODE_BALANCED`로 스캔 주기를 높여 재연결 직후 짧은 광고 패킷 누락을 줄이고, 백그라운드 대기 시에만 `SCAN_MODE_LOW_POWER`로 전환합니다([Punch Through](https://punchthrough.com/android-ble-scan-filter/)).
   * `GATE_SCREENING` 프로파일에서는 포그라운드 활성 중 `SCAN_MODE_LOW_LATENCY`로 동작합니다(상세 내용은 [gate_screening_mode_addon.md](file:///e:/myWork/projects/smartglass_scanner/gate_screening_mode_addon.md) 참조).
3. **시그니처/모드 변경 시 자동 재시작**: `BleScannerService`가 `combine(signatureRepository.activeSignatures, scanModeFlow)`를 `distinctUntilChanged()` + `collectLatest`로 구독해, 커스텀 ID 추가·삭제나 화면 전환 시 최소 6.5초 스로틀링 가드를 준수해 `startScan()`을 재적용합니다.
4. **스로틀링 방어 (30초당 5회 제한, `ScanThrottleGate`)**: Android는 30초 내 `startScan()`을 5회 초과 호출하면 스캔을 조용히 차단하므로([Punch Through](https://punchthrough.com/android-ble-scan-errors/)), 재시작 간 최소 **6.5초(`6,500ms`) 디바운스**를 강제하고 지연 재스케줄링(`ScannerStartResult.Scheduled`) 및 `onScanFailed()` 에러 매핑을 수행합니다.
5. **장기 스캔 갱신**: 30분 이상 연속 스캔 시 시스템이 Opportunistic 스캔으로 강제 전환할 수 있으므로([ProAndroidDev](https://proandroiddev.com/android-restrictions-you-may-encounter-during-development-process-c39ede513813)), 20분(`REFRESH_INTERVAL_MS`) 주기로 `stopScan()` 후 `startScan()`을 재호출합니다.

### 4.2 패킷 시그니처 매칭 및 오탐 최소화 필터링 (`BleSignalFilter.kt`)

* **패킷 시그니처 매칭 규칙 (`matchSignature` / `matchesSingleSignature`)**:
  * **식별 대상**: `companyId`(Manufacturer Specific Data 키), `payloadAscii`/`payloadHex`(제조사 데이터 바이트 포함 `containsBytes`), `serviceUuid`(GATT Service UUID 대소문자 무시 일치), `serviceDataHex`(서비스 데이터 바이트 포함 일치), `deviceNamePrefix`(대소문자 무시 접두사), `deviceNamePatterns`(대소문자 무시 부분 문자열)를 검사합니다.
  * **AND/단독 결합 및 카테고리**: 하나의 시그니처에 여러 필드가 함께 지정되면 지정된 모든 조건이 일치(AND)해야 매칭되며, 카메라 탑재형(`CAMERA`)과 디스플레이 중심 기기(`DISPLAY_AR`)를 `category` 필드로 구분해 알림 문구와 UI 배지를 분기합니다.
  * **다중 매칭 우선순위**: 한 패킷에 복수 시그니처가 일치할 경우 ① 높은 `baseConfidence`(`HIGH > MEDIUM > LOW`) → ② 구체적 조건 가중치(`payload*` +2, `serviceDataHex` +2, `serviceUuid` +1, `deviceName*` +1) → ③ 내장 시그니처(`!isCustom`) 순으로 최우선 1건을 선택합니다.
  * **기기명(`deviceName`) 처리**: 광고 패킷 및 Scan Response의 기기명을 수집해 UI 카드·로그 표시에 활용하며, `deviceNamePrefix` / `deviceNamePatterns` 기반 매칭 규칙도 콜백에서 대소문자 무시로 평가합니다.
  * **기기 식별 키 (`buildDeviceKey`)**: 송신 MAC 주소가 있으면 대문자 주소를, 비어 있으면 `${signature.id}:$payloadHash`를 키로 사용해 화이트리스트와 슬라이딩 윈도우를 추적합니다.
* **EMA RSSI 스무딩**: `smoothed = 0.3 * rawRssi + 0.7 * prevSmoothed`를 적용해 실내 다중경로 페이딩 노이즈를 완화합니다.
* **히스테리시스**: 설정된 진입 임계값(설정 UI 기본 `-75 dBm`, 범위 `-95 ~ -50 dBm`; 필터 단독 기본값 `-72 dBm`)과 이탈 임계값(`entryThresholdDbm - 8 dBm`)을 분리해 경계선 떨림을 방지합니다.
* **슬라이딩 윈도우 & 유연 N-of-M (`requiredObservations()`)**: 최근 **8초(`8,000ms`)** 슬라이딩 윈도우 내에서 시그니처 신뢰도에 따라 필요 연속 관측 횟수를 차등 적용합니다.
  * `유력 (HIGH)` (`0x058E + META_RB_GLASS`, `0x0D53`, `0x060C` Vuzix, `0xFEA8` Da Echo, `HeyCyan` / `Rokid` UUID): **1회** 관측으로 즉시 확정해 짧은 재연결 버스트 패킷을 놓치지 않습니다.
  * `중간 (MEDIUM)` (`Snap Spectacles`, `TCL / RayNeo`, `Meizu MYVU`, `Kopin`, `North`, `0xFD5F`, 기기명 패턴): **2회** 관측 시 확정합니다.
  * `주의 (LOW)` (`0x01AB`, `0x05D6`, 사용자 커스텀 Hex ID): **3회** 이상 관측 시 확정해 일반 BLE 기기 오탐을 억제합니다([Glasses Radar](https://play.google.com/store/apps/details?id=com.glassesradar.app)).
* **오래된 기기 정리**: `120초` 이상 미관측(`DEFAULT_STALE_DEVICE_MS`) 또는 화이트리스트 등록 기기는 목록에서 자동 제거합니다.

---

## 5. UI / UX, 아이콘 및 릴리스 사이닝 명세

1. **메인 대시보드 (`ui/main/MainScreen.kt`)**:
   * 상단 앱바 내 **입실 스크리닝(`GateScreening`)** 및 **설정(`Settings`)** 이동 버튼
   * 스캔 On/Off 스위치 및 현재 스캔 모드(`BALANCED` / `LOW_POWER` / `LOW_LATENCY`) 상태 배지, 누락 권한/Bluetooth OFF 배너
   * 탐지 기기 카드 리스트(기기명/제조사 라벨, 신뢰도 배지, 스무딩 RSSI 및 관측 횟수, 마지막 관측 시각, **무시(화이트리스트) 추가 버튼**)
   * 고정 안내 배너: 주변에 해당 제조사 기기가 감지되었음을 알리며, 실제 녹화 여부나 착용자를 단정할 수 없음을 명시
2. **시험장 입실 스크리닝 (`ui/gate/GateScreeningScreen.kt`)**:
   * 초근접(`-55 dBm`) 스크리닝, `CLEAR / CAUTION / ALERT` 컬러 인디케이터, 무음 진동 피드백, **다음 응시자(초기화)** 버튼 제공
3. **알림 및 포그라운드 서비스 (`BleScannerService.kt`)**:
   * 두 개의 알림 채널 운영: `ble_scan_ongoing` (`IMPORTANCE_LOW`), `ble_glasses_alerts` (`IMPORTANCE_HIGH`)
   * 동일 기기 중복 알림 방지 쿨다운 (기본 `10초`, 설정 범위 `5~60초`)
   * 상단 고정 알림에 **스캔 중지(Stop)** 액션 버튼 포함, Status Bar 전용 단색 아이콘([ic_stat_glasses_scan.xml](file:///e:/myWork/projects/smartglass_scanner/app/src/main/res/drawable/ic_stat_glasses_scan.xml)) 사용
4. **설정 및 커스텀 ID 관리 (`ui/settings/SettingsScreen.kt`)**:
   * RSSI 임계값 슬라이더 (`-95 ~ -50 dBm`, 기본 `-75 dBm`), 알림 쿨다운 슬라이더 (`5 ~ 60초`, 기본 `10초`)
   * 커스텀 Hex Company ID 입력 UI: `^[0-9A-Fa-f]{1,4}$`(`0x` 접두사 허용) 검증 및 `InputChip` 개별 삭제
   * 화이트리스트 개별 해제, 최대 500건 로컬 탐지·관측 로그(`files/detection_logs.json` 영속 보관, 1회/임계치 미만 패킷 `[OBSERVED]` 스로틀 기록) 조회/초기화 및 Android SAF(`ActivityResultContracts.CreateDocument("text/plain")`) 기반 `.txt` 파일 내보내기
5. **앱 아이콘 (`Adaptive Icon` + `Monochrome`)**:
   * [ic_launcher_background.xml](file:///e:/myWork/projects/smartglass_scanner/app/src/main/res/drawable/ic_launcher_background.xml), [ic_launcher_foreground.xml](file:///e:/myWork/projects/smartglass_scanner/app/src/main/res/drawable/ic_launcher_foreground.xml), [ic_launcher_monochrome.xml](file:///e:/myWork/projects/smartglass_scanner/app/src/main/res/drawable/ic_launcher_monochrome.xml) 및 `mipmap-mdpi~xxxhdpi` WebP 아이콘 구성
6. **릴리스 앱 사이닝 (`app/build.gradle.kts`)**:
   * 루트의 `keystore.properties`([keystore.properties.example](file:///e:/myWork/projects/smartglass_scanner/keystore.properties.example) 참고)와 `windyflow.jks`를 읽어 `release` `signingConfig`에 연결 (Git 추적 제외)

---

## 6. 테스트 및 검증 방법

### 6.1 자동화 테스트 (현행)
* **JVM 유닛 테스트 (`./gradlew testDebugUnitTest`)**: 26건
  * `BleSignalFilterTest` (14): 시그니처 매칭, Quest VR(`META_QUEST_3`) 배제, `Rokid`/`Vuzix`/`Da Echo`(`serviceDataHex`)/`HeyCyan-`·`Even G`·`XREAL` 기기명 매칭, `CAMERA`/`DISPLAY_AR` 카테고리 구분, EMA 스무딩 + 유연 N-of-M(HIGH 1 / MEDIUM 2 / LOW 3), 히스테리시스 이탈, 화이트리스트/쿨다운, Gate 스크리닝(`-55 dBm` / `CAUTION` / `ALERT` / `ALERT` 유지·리셋)
  * `ScanThrottleAndPermissionTest` (4): 6.5초 스로틀 가드, `BALANCED`/`LOW_POWER`/`LOW_LATENCY` 모드 분기, API 31+ 위치 권한 미요청, 포그라운드 이름 비필터 vs 백그라운드 `ScanFilter` 전환 검증
  * `SignatureRepositoryTest` (4): 22종 JSON 파싱, `serviceDataHex`/`deviceName*`/`DISPLAY_AR` 파싱, Hex 정규화, DataStore 반응형 병합
  * `DetectionLogPersistenceTest` (2): `files/detection_logs.json` 재시작 영속 복구 및 `[OBSERVED]`/`[ALERT]` 스로틀 로깅 검증
  * `WhitelistRepositoryTest` (2): RSSI·쿨다운·화이트리스트 영속화 및 커스텀 Hex ID 관리
* **계측 UI 테스트 (`./gradlew connectedDebugAndroidTest`)**: 5건
  * `MainScreenTest` (3), `GateScreeningScreenTest` (1), `SettingsScreenTest` (1)

### 6.2 실기기 E2E BLE 비콘 에뮬레이션
* **Windows 10/11 PC (내장 테스트 비콘)**:
  * `tools\run_ble_beacon.bat` (C# + WinRT, 별도 패키지 불필요) 또는 `python tools/ble_beacon.py`
  * `1~14` 시나리오 송출 지원 (`meta_rayban`, `meta_quest`, `luxottica`, `snap`, `meta_general`, `jieli`, `heycyan`, `rayneo`, `meizu`, `rokid`, `vuzix`, `da_echo`, `even_g1`, `heycyan_name`; 단 `13~14`번 `LocalName` 변조는 Windows WinRT API OS 제한으로 `nRF Connect` 등 보조 단말 권장)
* **보조 Android 기기 (`nRF Connect`)**: `ADVERTISER` 탭 → `Manufacturer Data` → Company ID `0x058E`, Data `4D4554415F52425F474C415353` 송출([Hackster.io](https://www.hackster.io/kevin-loeffler/spectacle-a-smart-glasses-detecting-keychain-576576))
* **Linux BlueZ CLI**:
  ```bash
  sudo btmgmt advertising off
  sudo btmgmt add-adv -g -d 10ff8e054d4554415f52425f474c415353 1
  ```

---

## 7. 구현 완료 현황 (v1.0)

* [x] **Step 1**: Gradle 프로젝트 구성 (Jetpack Compose, Material 3, Navigation 3, DataStore, `kotlinx.serialization`, Coroutines) 및 BLE 전용 `AndroidManifest.xml`
* [x] **Step 2**: `assets/signatures.json`, `BleModels.kt`, `SignatureRepository.kt`, `BleSignalFilter.kt`(유연 N-of-M 포함), `SmartGlassesScanner.kt`(포그라운드/백그라운드 모드 분리 포함) 구현 및 유닛 테스트
* [x] **Step 3**: `BleScannerService.kt` (`connectedDevice`, `activeSignatures`·모드 구독 재시작, 2종 알림 채널, Stop 액션) 구현
* [x] **Step 4**: `WhitelistRepository.kt`, `DetectionStateHolder.kt` 및 `MainScreen` / `GateScreeningScreen` / `SettingsScreen` 구현·UI 계측 테스트
* [x] **Step 5**: 릴리스 앱 사이닝(`keystore.properties`), 커스텀 Adaptive/Monochrome/알림 아이콘 적용, Windows BLE 테스트 비콘(`tools/`) 구축 및 실기기 E2E 검증

---

## 8. v1.1 로드맵 (출시 후 고도화)

* **Wi-Fi Direct 보조 탐지 (`WifiOffloadMonitor.kt`)**: BLE 유력 탐지 직후 `WifiManager.SCAN_RESULTS_AVAILABLE_ACTION`을 수동 리스닝해 `DIRECT-meta` 등 영상 오프로딩 SSID 포착 시 `전송 활성` 단계로 상향 표시 (Android 13+ `ACCESS_FINE_LOCATION` 선택적 옵트인 UX 포함, [Android Developers](https://developer.android.com/develop/connectivity/wifi/wifi-permissions))
* **초저전력 `PendingIntent` 대기 모드**: 상단 고정 알림 없이 시스템 도즈(Doze) 모드에서도 저빈도로 스캔하는 옵션 추가([Android Developers](https://developer.android.com/develop/connectivity/bluetooth/ble/background))
* **원격 시그니처 업데이트**: 앱 업데이트 없이 `signatures.json` 최신화 지원
* **시그니처 확장 (Xiaomi 등)**: `deviceName`/페이로드 복합 필터 기반 `Xiaomi`(`0x038F`) 실기기 패킷 캡처 반영 및 기타 제조사 확장
