# Smart Glasses Detector (Android Native)

**Smart Glasses Detector**(`com.windyflow.glassesdetector`)는 주변 스마트글래스(Ray-Ban Meta, Snap Spectacles, HeyCyan SDK 등)의 BLE 광고 패킷을 백그라운드에서 상시 탐지하고, 오탐 최소화 필터링 및 시험장·보안 구역 입실 스크리닝 모드를 제공하는 **위치 권한 없는(BLE 전용)** Android 네이티브 앱입니다.

---

## 주요 기능

1. **상시 근접 탐지 모드 (`BACKGROUND_GUARD`)**
   * **포그라운드/백그라운드 스캔 모드 분리**: 앱 화면 활성 시 기기명 패턴·Scan Response까지 폭넓게 잡는 비필터 스캔(`SCAN_MODE_BALANCED`)으로 동작하고, 백그라운드 대기 시에는 하드웨어 `ScanFilter`(Company ID / Service UUID / Service Data / Android 13+ 기기명 접두사)를 적용한 `SCAN_MODE_LOW_POWER`로 자동 전환합니다.
   * **오탐 방지 신호 필터링 (`BleSignalFilter`)**: EMA RSSI 스무딩(`α = 0.3`), 히스테리시스(`8 dBm` 간격), 8초 슬라이딩 윈도우 기반 **유연 N-of-M**(`HIGH = 1회`, `MEDIUM = 2회`, `LOW = 3회`) 판정을 적용하고, 카메라 탑재형(`CAMERA`)과 디스플레이 AR형(`DISPLAY_AR`) 알림 문구를 구분합니다.
   * **화이트리스트 & 쿨다운**: 무시(화이트리스트) 기기 영구 제외, 동일 기기 중복 알림 쿨다운(`5~60초`, 기본 `10초`), 120초 미관측 기기 자동 정리.
   * **로컬 로그 영속 보관 및 내보내기**: 확정 경보뿐 아니라 1회/임계치 미만 관측 패킷도 `[OBSERVED]`(기기별 스로틀 적용)로 앱 내부 `files/detection_logs.json`에 최대 500건 영속 보관(재시작 후 유지)하며, Android SAF(`CreateDocument`)를 통해 기기 내 `.txt` 리포트로 내보낼 수 있습니다(외부 서버 전송 없음).
2. **시험장 입실 스크리닝 모드 (`GATE_SCREENING`)**
   * 감독관용 포그라운드 전용 `SCAN_MODE_LOW_LATENCY` 초근접(`-55 dBm` 이상, 약 0.5~1m) 즉시 판정 모드.
   * 빠른 EMA(`α = 0.7`) 및 즉시 3단계 판정(`CLEAR` 녹색 / `CAUTION` 황색 / `ALERT` 적색), `2초` 쿨다운 무음 진동(`VIBRATE`), **다음 응시자(초기화)** 버튼 제공.
3. **시그니처 DB + 사용자 커스텀 Hex ID**
   * [signatures.json](file:///e:/myWork/projects/smartglass_scanner/app/src/main/assets/signatures.json) 기반 내장 시그니처와 DataStore에 저장되는 사용자 커스텀 16-bit Company ID(`0x0000`~`0xFFFF`)를 실시간 `Flow`로 병합해 동적 `ScanFilter`를 재구성합니다.
4. **Google Play v1.0 BLE-Only 정책 준수**
   * Android 12+(API 31+)에서 `BLUETOOTH_SCAN`에 `neverForLocation` 플래그를 선언하여 `ACCESS_FINE_LOCATION` 런타임 요청 없이 동작합니다(`maxSdkVersion="30"` 하위 호환만 유지).
   * Android 14+ `connectedDevice` 포그라운드 서비스 상단 고정 알림 및 즉시 **스캔 중지(Stop)** 액션 제공.

---

## 기술 스택 및 환경

| 항목 | 구성 |
| :--- | :--- |
| **패키지 / 버전** | `com.windyflow.glassesdetector` / `versionCode = 1`, `versionName = "1.0"` |
| **SDK** | `minSdk = 26` (Android 8.0), `compileSdk = 37`, `targetSdk = 37` |
| **빌드 도구** | AGP `9.0.1`, Kotlin `2.3.20`, Java `17` Toolchain |
| **UI / 내비게이션** | Jetpack Compose (BOM `2026.03.01`), Material 3, AndroidX Navigation 3 (`1.0.1`) |
| **비동기 / 저장소** | Kotlin Coroutines (`1.10.2`), `StateFlow`, `kotlinx.serialization` (`1.13.0`), Preferences DataStore (`1.3.0`) |

---

## 프로젝트 구조

```text
.
├── README.md                                  # 프로젝트 개요·빌드·테스트·사이닝 가이드
├── docs/
│   ├── index.html                             # GitHub Pages 앱 소개 랜딩 페이지
│   ├── privacy.html                           # Play Store용 개인정보처리방침 (한/영)
│   └── screenshots/                           # 소개 페이지용 앱 화면 스크린샷 4종
├── smart_glasses_detector_spec.md             # v1.0 MVP 기술 명세서 (현행화 완료)
├── gate_screening_mode_addon.md               # 시험장 입실 스크리닝 모드 명세서 (현행화 완료)
├── keystore.properties.example                # 릴리스 앱 사이닝 설정 템플릿
├── app/
│   ├── build.gradle.kts                       # Gradle & release signingConfig
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            # BLE 전용 권한, VIBRATE, connectedDevice 서비스
│       │   ├── assets/signatures.json         # 내장 스마트글래스 BLE 시그니처 22종 (CAMERA / DISPLAY_AR)
│       │   ├── res/
│       │   │   ├── drawable/                  # Adaptive/Monochrome 런처 및 알림 small icon
│       │   │   └── mipmap-*/                  # Adaptive Icon v26 XML & 레거시 WebP 아이콘
│       │   └── kotlin/com/windyflow/glassesdetector/
│       │       ├── MainActivity.kt
│       │       ├── Navigation.kt / NavigationKeys.kt
│       │       ├── ble/
│       │       │   ├── BleModels.kt           # ScanProfile, BleScanMode, GateStatus/Result, BleSignature
│       │       │   ├── BleSignalFilter.kt     # EMA, 히스테리시스, 유연 N-of-M, Gate 즉시 판정
│       │       │   ├── PermissionHelper.kt    # API 레벨별 BLE-only 권한 헬퍼
│       │       │   ├── SignatureRepository.kt # assets JSON + DataStore 커스텀 ID 병합
│       │       │   └── SmartGlassesScanner.kt # ScanFilter 생성, 6.5s 스로틀 가드, 20m 갱신
│       │       ├── data/
│       │       │   ├── AppPreferences.kt      # 공용 DataStore 인스턴스
│       │       │   ├── DetectionStateHolder.kt# 서비스-UI 상태 브리지, detection_logs.json 영속 저장 & .txt 포맷터
│       │       │   └── WhitelistRepository.kt # RSSI/쿨다운/화이트리스트 DataStore 저장
│       │       ├── service/
│       │       │   └── BleScannerService.kt   # connectedDevice ForegroundService & 알림 관리
│       │       └── ui/
│       │           ├── main/                  # MainScreen, MainScreenViewModel
│       │           ├── gate/                  # GateScreeningScreen, GateScreeningViewModel
│       │           ├── settings/              # SettingsScreen, SettingsViewModel
│       │           └── theme/                 # Color, Theme, Type
│       ├── test/kotlin/...                    # JVM 유닛 테스트 26건 (5개 테스트 클래스)
│       └── androidTest/kotlin/...             # Compose 계측 UI 테스트 5건 (3개 테스트 클래스)
└── tools/
    ├── ble_beacon.py                          # Python WinRT BLE 테스트 비콘 송출기
    ├── BleTestBeacon.cs                       # C# (.NET Framework 4 + WinRT) 테스트 비콘 소스
    └── run_ble_beacon.bat                     # C# 비콘 자동 컴파일 및 실행 배치
```

---

## 빌드 및 테스트

### Debug 빌드 · 유닛 테스트 · Lint
```bash
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```
* **단위 테스트 (`app/src/test`)**: `BleSignalFilterTest`(14), `ScanThrottleAndPermissionTest`(4), `SignatureRepositoryTest`(4), `DetectionLogPersistenceTest`(2), `WhitelistRepositoryTest`(2) — 총 26건
* **UI 계측 테스트 (`app/src/androidTest`)**: `MainScreenTest`(3), `GateScreeningScreenTest`(1), `SettingsScreenTest`(1) — 총 5건 (`./gradlew connectedDebugAndroidTest`)

### 릴리스 앱 사이닝 & 빌드
`keystore.properties`와 `.jks` 파일은 `.gitignore`로 추적에서 제외됩니다.
1. [keystore.properties.example](file:///e:/myWork/projects/smartglass_scanner/keystore.properties.example)을 루트에 `keystore.properties`로 복사하고 키스토어 비밀번호·별칭을 입력합니다.
2. 프로젝트 루트에 키스토어 파일(기본 `windyflow.jks`)을 배치합니다.
3. 릴리스 APK / AAB 생성:
   ```bash
   ./gradlew assembleRelease bundleRelease --no-daemon
   ```

---

## 실기기 BLE 탐지 테스트 (Windows PC / Android / Linux)

실제 스마트글래스 없이도 주변 BLE 광고 송출기로 탐지·필터링·입실 스크리닝을 검증할 수 있습니다.

1. **Windows 10/11 PC (권장 - 별도 설치 없음)**
   ```cmd
   tools\run_ble_beacon.bat
   ```
   또는 Python(`winrt-Windows.Devices.Bluetooth*` 설치 시):
   ```bash
   python tools/ble_beacon.py meta_rayban
   ```
   시나리오 `1~14` 선택 지원 (`tools/run_ble_beacon.bat <scenario> --duration <sec>` 가능):
   * `1` (`meta_rayban`): `0x058E` + `META_RB_GLASS` → `HIGH` (1회 관측 확정 / `CAMERA` `ALERT`)
   * `2` (`meta_quest`): `0x058E` + `META_QUEST_3` → Quest VR 오탐 배제 확인 (미탐지)
   * `3` (`luxottica`): `0x0D53` → `HIGH` (`CAMERA` `ALERT`)
   * `4` (`snap`): `0x03C2` → `MEDIUM` (2회 관측 / `CAMERA` `ALERT`)
   * `5` (`meta_general`), `6` (`jieli`) → `LOW` (3회 관측 / `CAMERA` `CAUTION`)
   * `7` (`heycyan`): Service UUID `7905FFF0-B5CE-4E99-A40F-4B1E122D00D0` → `HIGH` (`CAMERA` `ALERT`)
   * `8` (`rayneo`): `0x0BC6` (TCL / RayNeo) → `MEDIUM` (2회 관측 / `CAMERA` `ALERT`)
   * `9` (`meizu`): `0x03AB` (Meizu MYVU AR) → `MEDIUM` (2회 관측 / `DISPLAY_AR` `ALERT`)
   * `10` (`rokid`): Service UUID `00009100-0000-1000-8000-00805F9B34FB` → `HIGH` (`CAMERA` `ALERT`)
   * `11` (`vuzix`): `0x060C` (Vuzix Smart Glasses) → `HIGH` (`CAMERA` `ALERT`)
   * `12` (`da_echo`): Service UUID `0xFEA8` + Service Data `41 31 49 01 00 00` → `HIGH` (`CAMERA` `ALERT`)
   * `13` (`even_g1`), `14` (`heycyan_name`): 커스텀 `LocalName` 기반 `DISPLAY_AR` / `CAMERA` 시나리오 (*Windows WinRT API가 AD `0x08`/`0x09` 커스텀 이름 변조를 OS 차원에서 차단하므로 실기기 송출은 아래 `nRF Connect` 또는 블루투스 어댑터 이름 변경 권장*)
2. **보조 Android 기기 (`nRF Connect`)**: `ADVERTISER` 탭 → Manufacturer Data Company ID `0x058E`, Data `4D4554415F52425F474C415353` 송출
3. **Linux BlueZ (`btmgmt`)**: `sudo btmgmt add-adv -g -d 10ff8e054d4554415f52425f474c415353 1`

---

## 상세 문서

* [smart_glasses_detector_spec.md](file:///e:/myWork/projects/smartglass_scanner/smart_glasses_detector_spec.md) — v1.0 MVP 아키텍처, 권한/Play 심사, 시그니처 DB, BLE 필터, 사이닝·아이콘 명세
* [gate_screening_mode_addon.md](file:///e:/myWork/projects/smartglass_scanner/gate_screening_mode_addon.md) — 시험장 입실 스크리닝(`GATE_SCREENING`) 모드 동작·컴포넌트 명세
