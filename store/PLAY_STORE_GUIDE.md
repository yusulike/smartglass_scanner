# Google Play Store 출시 준비 가이드 (SmartGlass Scanner)

이 문서는 **스마트글래스 스캐너 (`com.windyflow.smartglassscanner`)**를 Google Play Console에 등록·심사 제출할 때 바로 복사해 사용할 수 있는 스토어 문구, 에셋 위치, 정책 설문 답변 및 주의사항을 정리한 문서입니다.

---

## 1. 업로드 산출물 및 그래픽 에셋

| 항목 | 규격 | 파일 경로 | 비고 |
| :--- | :--- | :--- | :--- |
| **릴리스 앱 번들 (AAB)** | R8 난독화·리소스 축소·릴리스 서명 적용 | `app/build/outputs/bundle/release/app-release.aab` | `.gitignore`로 Git 추적 제외됨 (`./gradlew bundleRelease`로 생성) |
| **앱 아이콘** | 512 × 512 px PNG (32-bit) | `store/icon_512x512.png` | Play Console 앱 아이콘 슬롯 업로드용 |
| **그래픽 이미지 (Feature Graphic)** | 1024 × 500 px PNG | `store/feature_graphic_1024x500.png` | Play Console 필수 배너 슬롯 |
| **휴대전화 스크린샷** | 1080 × 1920 px (9:16) 24-bit RGB PNG (4장) | `store/screenshots/01_dashboard.png` ~ `04_settings_signatures.png` | 대시보드 / 입실 스크리닝 / 알림 / 설정 화면 (타사 알림·상태바 제거, 알파 채널 없음) |

> **중요 (서명 키 보관)**: 릴리스 서명 키(`windyflow.jks`)와 `keystore.properties`는 보안상 Git에서 제외되어 있습니다. 반드시 안전한 클라우드/오프라인 백업(예: Google Drive / 암호화 보관소)에 사본을 보관하세요. Play Console 업로드 시 **Google Play 앱 서명(Play App Signing)** 사용을 권장합니다.

---

## 2. 스토어 등록정보 문구 (복사/붙여넣기용)

### 한국어 (`ko-KR` — 기본 언어 권장)

- **앱 이름 (최대 30자)**
  ```text
  스마트글래스 스캐너
  ```
- **간단한 설명 (최대 80자)**
  ```text
  주변 카메라·AI 스마트글래스 BLE 신호를 실시간 감지하고, 시험장·보안 구역 입실 시 무음 진동으로 확인합니다.
  ```
  *(공백 포함 61자 — 80자 제한 충족)*

- **자세한 설명 (최대 4,000자)**
  ```text
  스마트글래스 스캐너 (SmartGlass Scanner)는 주변에서 송출되는 Bluetooth Low Energy(BLE) 광고 신호를 100% 기기 내부에서 실시간으로 분석하여, 카메라·디스플레이가 탑재된 스마트글래스의 근접 여부를 알려주는 온디바이스 보안·프라이버시 유틸리티입니다.

  ■ 주요 기능
  • 25종 내장 BLE 시그니처 매칭 + 커스텀 16-bit Hex Company ID 등록 지원
    - Ray-Ban Meta (0x058E, 0x01AB, 0x0D53 / FD5F / 기기명)
    - Snap Spectacles, TCL/RayNeo, Vuzix, Solos/Kopin, Zhuhai Jieli, Meizu MYVU
    - HeyCyan SDK, Rokid AI Glasses, Da Echo Glasses, North Focals
    - Even Realities G1/G2, INMO AR, XREAL/Nreal, Echo Frames, Brilliant Monocle 등
  • 백그라운드 가드 모드 (포그라운드 서비스)
    - 화면이 꺼져 있거나 다른 앱을 사용 중에도 상시 알림(즉시 중지 버튼 포함)과 함께 주변 신호를 감지합니다.
    - 신호 세기(RSSI) 임계값(-40 ~ -95 dBm), 알림 재울림 방지 쿨다운(5 ~ 120초), 오탐 기기 무시 목록(화이트리스트)을 지원합니다.
  • 시험장·보안 구역 입실 스크리닝 모드 (Gate Screening Mode)
    - Low-Latency 고속 스캔과 슬라이딩 윈도우 피크 RSSI 필터로 약 1m 이내 초근접 기기만 정밀 판정합니다.
    - CLEAR(초록) / CAUTION(주황) / ALERT(빨강) 3단계 풀스크린 상태 표시 및 이어폰·음성 없이 동작하는 무음 햅틱 진동 피드백을 제공합니다.
  • 완전 로컬 저장 및 .txt 로그 내보내기
    - 최근 500건의 탐지 및 스로틀 관측 로그를 앱 내부 저장소에만 보관하며, 설정에서 사용자가 원할 때 직접 .txt 파일로 내보낼 수 있습니다.

  ■ 100% 온디바이스 프라이버시 원칙
  • 계정 가입, 광고 SDK, 분석 트래커, 외부 서버 통신 및 클라우드 자동 백업을 일체 사용하지 않습니다(INTERNET 권한 없음).
  • Android 12(API 31) 이상에서는 BLUETOOTH_SCAN(neverForLocation) 권한만 사용하여 위치 권한을 요구하지 않습니다.

  ■ 정확도 및 테스트 범위 안내 (중요)
  • BLE 광고 패킷(제조사 Company ID, Service UUID, Service Data, 기기명 패턴) 기반의 확률적·참고용 탐지 도구입니다. 블루투스 전원이 꺼진 기기, 비활성 슬립 상태, 제조사 펌웨어 변경 또는 비표준 MAC 무작위화 환경에서는 탐지되지 않거나 일반 BLE 기기가 오탐지될 수 있습니다.
  • 현재 앱·시그니처는 연결된 단일 Android 실기기(Galaxy S26 Ultra / Android 17) 환경에서 BLE 비콘 시뮬레이션과 공식 공개 문서·오픈소스 시그니처 자료를 바탕으로 검증되었으며, 모든 실제 스마트글래스 단말기에서 일일이 실기 테스트를 거치지 못했습니다.
  • 법적·물리적 보안 검사 또는 보안 감사를 대체할 수 없으며, 참고용 보조 도구로만 사용하세요.
  ```

---

### English (`en-US`)

- **App name (max 30 chars)**
  ```text
  SmartGlass Scanner
  ```
- **Short description (max 80 chars)**
  ```text
  On-device BLE scanner for camera/AI smart glasses & silent gate screening.
  ```
  *(74 chars — within 80-char limit)*

- **Full description (max 4,000 chars)**
  ```text
  SmartGlass Scanner is a 100% offline, on-device Bluetooth Low Energy (BLE) utility that detects nearby camera-equipped and AI smart glasses by analyzing public BLE advertising packets.

  ■ Key Features
  • 25 Built-in BLE Signatures + Custom 16-bit Hex Company IDs
    - Covers documented Company IDs, Service UUIDs, Service Data prefixes, and advertised device name patterns including Ray-Ban Meta, Snap Spectacles, TCL/RayNeo, Vuzix, Solos/Kopin, Rokid AI Glasses, HeyCyan SDK, Da Echo, Meizu MYVU, North Focals, Even Realities G1/G2, INMO AR, XREAL/Nreal, Echo Frames, and Brilliant Labs Monocle.
  • Background Guard Mode (Foreground Service)
    - Monitors BLE advertisements in the background with an ongoing notification and instant Stop button.
    - Configurable RSSI proximity threshold (-40 to -95 dBm), per-device alert cooldown (5 to 120s), and one-tap MAC/label ignore whitelist.
  • Exam & Security Gate Screening Mode
    - Switches to foreground-only Low-Latency scanning with sliding-window peak RSSI filtering (~1 meter screening zone).
    - Full-screen CLEAR / CAUTION / ALERT verdict banners paired with silent haptic vibration alerts for quiet proctoring or security checkpoints.
  • Local Detection Log & .txt Export
    - Retains up to 500 recent detections and throttled observation events strictly on-device, with Storage Access Framework .txt export initiated solely by the user.

  ■ Privacy-First Architecture
  • Zero network access (no INTERNET permission, no analytics, no ad SDKs, no cloud backup).
  • Uses BLUETOOTH_SCAN with neverForLocation on Android 12+ (API 31+) so no location permission is requested on modern Android devices.

  ■ Accuracy & Testing Disclaimer (Important)
  • BLE advertising detection is probabilistic and intended strictly as a screening aid. Devices powered off, with Bluetooth disabled, in deep sleep, or using altered firmware may not be detected, and unrelated BLE devices sharing a chipset or vendor ID may cause false positives.
  • Verified on a single physical Android device using simulated BLE beacons alongside published vendor documentation and open-source BLE signature research; exhaustive testing across all physical smart glasses hardware models has not been performed.
  • Not a substitute for physical inspection, compliance audits, or legal enforcement.
  ```

---

## 3. Google Play Console 필수 체크리스트 & 심사 대응

### A. 앱 액세스 권한 / 카테고리 / 연락처
- **앱 또는 게임**: 앱 (App)
- **무료 / 유료**: 무료 (광고 없음, 인앱 구매 없음)
- **카테고리**: 도구 (`Tools`)
- **개발자 연락처 이메일**: `yusu.kang@gmail.com`

### B. 개인정보처리방침 URL (필수 — 공개 URL 확인!)
- 현재 개인정보처리방침 파일이 [docs/privacy.html](../docs/privacy.html)에 준비되어 있습니다.
- **주의**: GitHub 저장소를 **Private**으로 만든 경우, 무료(GitHub Free) 계정에서는 Private 저장소의 GitHub Pages 공개 배포가 제한될 수 있습니다.
- Play Console의 개인정보처리방침 URL에는 **로그인 없이 누구나 브라우저로 열람 가능한 공개 URL**을 입력해야 하므로, 아래 중 하나로 호스팅한 뒤 그 URL을 입력하세요:
  1. 문서 전용 Public GitHub 저장소(예: `yusulike/smartglass-scanner-docs`)에 `docs/` 내용만 올려 GitHub Pages 활성화
  2. Cloudflare Pages / Netlify / Vercel 정적 호스팅으로 `docs/` 폴더 배포
  3. 현재 저장소를 Public 오픈소스로 전환 후 GitHub Pages (`Settings → Pages`) 활성화

### C. 앱 콘텐츠 → 포그라운드 서비스 권한 (`FOREGROUND_SERVICE_CONNECTED_DEVICE`)
> Google Play는 `FOREGROUND_SERVICE_CONNECTED_DEVICE` 사용 목적을 매우 엄격하게 심사합니다.
- **선언 용도 선택**: 주변 블루투스 기기 탐지/연결 지속 작업 (사용자 시작 백그라운드 BLE 스캔)
- **심사 설명 예시 (영문 제출 권장)**:
  > The app operates an on-device Bluetooth Low Energy (BLE) proximity scanner for detecting nearby smart glasses. When the user explicitly starts the scanner, a foreground service (`connectedDevice`) runs with a persistent notification (including an immediate Stop button) so proximity alerts continue working while the user switches apps or locks the screen. No data leaves the device.
- **필수 데모 영상 (YouTube 비공개 링크 등 업로드용)**:
  1. 앱 실행 → BLE 스캔 권한 허용 → `스캔 시작` 버튼 탭
  2. 홈 화면/잠금 화면 또는 다른 앱으로 전환해도 상단 상태바에 `스마트글래스 스캐너` 상시 알림이 유지되는 모습
  3. 알림창의 `중지` 버튼(또는 앱 내 스위치)으로 스캔을 즉시 종료하면 포그라운드 서비스 알림이 사라지는 모습

### D. 데이터 보안 (Data Safety) 작성 기준
- 앱이 사용자 데이터를 수집하거나 공유하나요? → **아니오**
- 광고 ID (`AD_ID`) 사용 여부: **아니오** (`AndroidManifest.xml`에 `INTERNET` 및 `AD_ID` 권한 없음)
- 로컬 DataStore 및 `detection_logs.json` 보호를 위해 `android:allowBackup="false"` 및 클라우드 백업 제외 규칙이 이미 적용되어 있습니다.

### E. 신규 개인 개발자 계정 비공개 테스트 요건 (해당 시)
- 2023년 11월 이후 개설된 개인 Play Console 계정은 프로덕션(정식) 출시 전 **비공개 테스트(Closed Testing) 트랙에서 최소 12명의 테스터가 14일 연속 옵트인(Opt-in)** 상태를 유지해야 프로덕션 신청이 열립니다.
- 우선 **내부 테스트(Internal Testing)** 또는 **비공개 테스트(Closed Testing)** 트랙에 `app-release.aab`를 먼저 업로드해 Pre-launch report(자동 크래시/접근성/보안 스캔)를 확인하세요.
