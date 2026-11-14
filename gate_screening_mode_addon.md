# 시험장 입실 스크리닝 모드 (Gate Screening Mode) 추가 명세서

> **문서 상태**: 구현 완료 (현재 코드베이스 `BleModels.kt`, `BleSignalFilter.kt`, `SmartGlassesScanner.kt`, `BleScannerService.kt`, `ui/gate/*` 기준 현행화)

기존 [smart_glasses_detector_spec.md](smart_glasses_detector_spec.md) (v1.0 MVP)에 국가 시험장·보안 구역 입실 통제용 **초근접(약 1m 이내) 스크리닝 모드**를 추가한 확장 기능 명세서입니다.

---

## 1. 기능 개요

* **목적**: 감독관이 시험장 입실 대기 줄에서 스마트폰을 응시자 안경 근처(약 0.5~1m 이내)로 가까이 대어, 전원이 켜진 스마트글래스를 1~2초 내에 빠르게 스크리닝
* **원칙**: v1.0의 **BLE 전용(위치 권한 0개)** 구조를 그대로 유지하며, `GateScreeningScreen`이 포그라운드에 표시되는 동안에만 고빈도 스캔(`SCAN_MODE_LOW_LATENCY`)을 수행하고 화면 이탈 시 즉시 `BACKGROUND_GUARD` 프로파일로 복귀

---

## 2. 일반 모드 vs 입실 스크리닝 모드 동작 비교

| 설정 항목 | 일반 상시 탐지 (`BACKGROUND_GUARD`) | 시험장 입실 스크리닝 (`GATE_SCREENING`) |
| :--- | :--- | :--- |
| **스캔 모드** | 포그라운드 `SCAN_MODE_BALANCED` / 백그라운드 `SCAN_MODE_LOW_POWER` | `SCAN_MODE_LOW_LATENCY` (포그라운드 전용, [Punch Through](https://punchthrough.com/android-ble-scan-filter/)) |
| **RSSI 임계값** | 사용자 설정값 (UI 기본 `-75 dBm`, 필터 기본 `-72 dBm`, 이탈 `-8 dBm`) | `-55 dBm` 이상 고정 (`DEFAULT_GATE_RSSI_THRESHOLD_DBM`, 약 0.5~1m 초근접, [Nearby Glasses](https://github.com/yjeanrenaud/yj_nearbyglasses)) |
| **필터링 지연** | 8초 내 유연 N-of-M (`HIGH=1`, `MEDIUM=2`, `LOW=3`) + EMA(`α=0.3`) | 수신 즉시 판정 + 빠른 EMA(`GATE_EMA_ALPHA = 0.7`) |
| **알림/피드백 쿨다운** | `10초` (설정 `5~60초`) | 진동 쿨다운 `2초` (`DEFAULT_GATE_COOLDOWN_MS = 2,000ms`) + 수동 **다음 응시자(초기화)** 버튼 |
| **피드백 방식** | 상단 푸시 알림 (`ble_glasses_alerts` 채널) | 소리 없는 진동(`VibrationEffect` / `VIBRATE` 권한) + 전체 화면 컬러 인디케이터 |

---

## 3. 컴포넌트별 구현 명세

### 3.1 `BleModels.kt` & `SmartGlassesScanner.kt` (프로파일·스캔 모드 전환)

* [BleModels.kt](app/src/main/kotlin/com/windyflow/glassesdetector/ble/BleModels.kt):
  * `enum class ScanProfile { BACKGROUND_GUARD, GATE_SCREENING }`
  * `enum class BleScanMode { BALANCED, LOW_POWER, LOW_LATENCY }`
  * `enum class GateStatus { CLEAR, CAUTION, ALERT }` 및 `data class GateResult` (`status`, `device`, `matchedSignature`, `rawRssi`, `effectiveRssi`, `shouldVibrate`, `vibrationNonce`, `timestampMs`)
* [SmartGlassesScanner.kt](app/src/main/kotlin/com/windyflow/glassesdetector/ble/SmartGlassesScanner.kt):
  * `resolveScanMode(profile, isForegroundUi)`로 포그라운드 `GATE_SCREENING` 시 `BleScanMode.LOW_LATENCY`(`ScanSettings.SCAN_MODE_LOW_LATENCY`)를 선택합니다([Punch Through](https://punchthrough.com/android-ble-scan-filter/)).
  * 모드 전환 시에도 Android의 30초당 `startScan()` 5회 제한을 넘기지 않도록 `ScanThrottleGate`의 **최소 6.5초 디바운스** 가드를 동일하게 적용합니다([Punch Through](https://punchthrough.com/android-ble-scan-errors/)).

### 3.2 `BleSignalFilter.kt` (초근접 즉시 판정)

* `processGateScreening(...)`는 `rawRssi >= -55` 조건을 만족하고 화이트리스트에 없는 시그니처가 포착되면 8초 슬라이딩 윈도우를 기다리지 않고 즉시 `GateResult`를 방출합니다(표시용 RSSI는 `GATE_EMA_ALPHA = 0.7` 적용).
* 오탐(보청기 등 의료용 BLE 기기, [Reddit](https://www.reddit.com/r/Professors/comments/1rdv88h/theres_an_android_app_that_detects_smart_glasses/))과 스마트글래스를 감독관이 직관적으로 구분할 수 있도록 결과를 3단계로 나눕니다.
  * `CLEAR` (녹색): `-55 dBm` 이상 시그니처 미탐지 (기본 대기 상태)
  * `CAUTION` (황색): `ConfidenceLevel.LOW` (`0x01AB`, `0x05D6`, 커스텀 Hex ID 등) 탐지 — 2차 육안 확인 권장
  * `ALERT` (적색): `ConfidenceLevel.HIGH` / `MEDIUM` (`0x0D53`, `0x058E + META_RB_GLASS`, `Snap Spectacles`, `TCL / RayNeo`, `Meizu MYVU`, `HeyCyan` UUID 등) 탐지([Hackster.io](https://www.hackster.io/kevin-loeffler/spectacle-a-smart-glasses-detecting-keychain-576576))
* **경보 유지 및 하향 방지**: 이미 `ALERT`가 표시된 상태에서 수동 초기화 전에 `CAUTION` 패킷이 들어와도 `ALERT` 상태를 유지하며, `CAUTION`/`ALERT` 이벤트는 `[Gate:STATUS]` 접두사로 로컬 로그에도 기록됩니다.
* `resetGateScreening()` 호출 시 누적된 빠른 EMA·관측 카운트·2초 진동 쿨다운을 비우고 `GateStatus.CLEAR`로 복귀합니다.

### 3.3 `GateScreeningScreen.kt` & `GateScreeningViewModel.kt` (감독관 전용 Compose UI)

* [MainScreen.kt](app/src/main/kotlin/com/windyflow/glassesdetector/ui/main/MainScreen.kt) 상단 앱바 액션 아이콘을 통해 `GateScreening` 화면으로 진입하며, 진입 시 자동으로 `ScanProfile.GATE_SCREENING` 전환 및 서비스 자동 시작(권한 보유 시)을 수행합니다.
* **무음 진동 알림 (`VIBRATE`)**: `vibrationNonce` 증가 시 소리 대신 `VibrationEffect`(`ALERT`: 2회 파형 `[0, 180, 90, 260] ms`, `CAUTION`: 단일 진동 `150ms`)와 애니메이션 배경색 전환으로만 피드백합니다.
* **탐지 카드**: 탐지된 제조사 라벨, 실시간/빠른 스무딩 RSSI(`dBm`), 신뢰도 등급을 크게 표시합니다.
* **다음 응시자(초기화) 버튼**: `DetectionStateHolder.requestGateReset()` → `gateResetEvents`를 통해 서비스 필터 상태와 화면 경보를 즉시 비워 다음 응시자를 지체 없이 검사합니다.
* **고정 운영 안내 문구**: 이미 스마트폰과 페어링/연결된 기기는 BLE 광고 패킷이 송출되지 않을 수 있으므로 안경다리 두께·카메라 렌즈 육안 점검을 병행하도록 안내합니다([CNET](https://www.cnet.com/tech/mobile/if-you-dont-want-smart-glasses-to-spy-try-a-bluetooth-detector-app/), [PCMag](https://uk.pcmag.com/smart-glasses/164898/is-someone-recording-you-with-smart-glasses-heres-how-to-spot-it)).

---

## 4. 구현 및 검증 완료 현황

* [x] **Step 1**: `ScanProfile`, `GateStatus`, `GateResult` 모델 및 `SmartGlassesScanner.resolveScanMode()` `LOW_LATENCY` 전환·6.5s 디바운스 연동
* [x] **Step 2**: `BleSignalFilter.processGateScreening()` (`-55 dBm` 즉시 판정, `CLEAR / CAUTION / ALERT`, `ALERT` 하향 방지, `2s` 진동 쿨다운, `resetGateScreening()`) 구현 및 `BleSignalFilterTest` 단위 테스트 검증
* [x] **Step 3**: `GateScreeningScreen.kt` / `GateScreeningViewModel.kt` 작성, `AndroidManifest.xml` `VIBRATE` 권한 추가, Navigation 3 라우팅 연결 및 `GateScreeningScreenTest` 계측 UI 테스트 작성
* [x] **Step 4**: Windows 테스트 비콘(`tools/run_ble_beacon.bat` / `tools/ble_beacon.py`) 및 `nRF Connect` / `btmgmt`를 활용한 `-55 dBm` 경계 및 3단계 상태 전이 검증
