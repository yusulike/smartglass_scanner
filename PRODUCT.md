# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Static HTML/CSS and minimal vanilla JS served directly from `docs/` on GitHub Pages (zero build pipeline or external CDN runtime dependencies required).

## Users

- **Primary users:** Everyday Android users (Korean and English-speaking) looking to detect nearby smart glasses or BLE-advertising camera wearables and verify whether the scanner respects their own privacy.
- **Secondary evaluators:** Google Play policy and security reviewers inspecting `docs/privacy.html` (including direct `#ko` and `#en` section links) to verify zero data collection, Android 12+ `neverForLocation` BLE permission scope, and developer contact details.

## Product Purpose

`Smart Glasses Detector` (`com.windyflow.glassesdetector`) passively scans nearby Bluetooth Low Energy (BLE) advertising packets locally on Android (Android 8.0 / API 26+) to alert users when known smart glasses hardware signatures appear nearby—without internet access, servers, tracking, or ads.

`docs/index.html` introduces the app's capabilities, screenshots, supported hardware signatures, and honest detection boundaries to visitors; `docs/privacy.html` publishes the official Korean/English Privacy Policy effective `2026-11-14`.

## Positioning

A 100% on-device Android BLE utility that refuses network permissions (`INTERNET` not requested), avoids location permission on Android 12+ (`API 31+`) via `neverForLocation`, and states technical/physical detection limits upfront rather than exaggerating stealth capabilities.

## Operating Context

- Hosted statically via GitHub Pages under `docs/` (`index.html`, `privacy.html`, `screenshots/`).
- Accessed from Google Play Console policy links (`privacy.html#ko`, `privacy.html#en`), README/repository visitors, and prospective app users checking authenticity and real screenshots before installing.
- `docs/index.html` supports toggling seamlessly between Korean and English; `docs/privacy.html` preserves `#ko` and `#en` section anchors as self-contained bilingual legal documentation.

## Capabilities and Constraints

- **Documented Core Capabilities (locked to existing `index.html` feature set per user confirmation):**
  1. **Background Guard:** Foreground-service monitoring with `BALANCED` / `LOW_POWER` power presets, persistent notification feedback, EMA signal smoothing, `8 dBm` release hysteresis, and configurable consecutive observation count (`1`–`3` detections).
  2. **Gate Mode (`<= 1m` / `-55 dBm`):** Silent tactile vibration + full-screen `Green` / `Amber` / `Red` proximity status readout for noisy or quiet spaces.
  3. **Custom Signatures & Whitelist:** Add custom Bluetooth Company IDs (`0x0000`–`0xFFFF`) and exclude trusted MAC addresses or device names.
  4. **Detection Limits Notice:** Clearly discloses that smart glasses already paired to a smartphone that have stopped BLE advertising, non-advertising devices, or randomized MAC behavior may not be detected.
- **Built-in Hardware Signatures (22 Signatures across `CAMERA` & `DISPLAY_AR`):** Ray-Ban Meta (`0x058E` + payload, `0xFD5F`), Luxottica Frame (`0x0D53`), Snap Spectacles (`0x03C2`), TCL / RayNeo (`0x0BC6`), Meizu MYVU (`0x03AB`), Rokid AI (`0x9100`), Da Echo (`0xFEA8` + Service Data `413149010000`), HeyCyan SDK (`7905FFF0-...` & name prefix), Vuzix (`0x060C`), Kopin / Solos (`0x041F`), North Focals (`0x0562`), Meta / Jieli shared IDs (`0x01AB`, `0x05D6`), and device-name rules (`Even G`, `INMO`, `XREAL`, `Ray-Ban`, `Spectacles`, `Echo Frames`, `Rokid`/`Vuzix`/`RayNeo`/`Moverio`/`Solos`).
- **Privacy Policy Facts (`docs/privacy.html`):**
  - Package: `com.windyflow.glassesdetector` | Effective Date: `2026-11-14` | Contact: `yusu.kang@gmail.com`
  - Collects or transmits **zero** personal information, location logs, analytics, or advertising identifiers; processes BLE data on-device; retains up to 500 local detection/observation logs on-device (`files/detection_logs.json`, `.txt` export only on explicit Storage Access Framework save); stores local user preferences (RSSI threshold, cooldown, whitelist, custom Hex IDs) in Android Jetpack DataStore, deleted upon uninstall or settings clear.
  - Android permissions listed in `privacy.html`: `BLUETOOTH_SCAN` (`neverForLocation` on Android 12+), `ACCESS_FINE_LOCATION` (Android 11 / API 30 and below only, compatibility requirement), `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` (Android 13+), `VIBRATE`.
- **Undecided Facts:** Public Google Play Store URL is not yet confirmed/published; no live store badge or download URL should be fabricated.

## Brand Commitments

- **Product Name:** `Smart Glasses Detector`
- **Package ID & Contact:** `com.windyflow.glassesdetector`, `yusu.kang@gmail.com`
- **Existing Assets:** 4 portrait Android screenshots under `docs/screenshots/` and the app's optical/radar identity (`index.html`).
- **Anchor Compatibility:** `docs/privacy.html` must retain `#ko` and `#en` section anchors cleanly.

## Evidence on Hand

- `docs/screenshots/main_dashboard.png` (720x1560 Android portrait screenshot)
- `docs/screenshots/notification_alert.png` (720x1560 Android portrait screenshot)
- `docs/screenshots/gate_alert.png` (720x1560 Android portrait screenshot)
- `docs/screenshots/settings.png` (720x1560 Android portrait screenshot)
- **Absences not to fabricate:** No Play Store ratings, download metrics, press quotes, testimonials, or confirmed Play Store download URL.

## Product Principles

- **Honesty Over Hype:** State physical BLE boundaries and false-negative conditions just as plainly as detection features.
- **Zero-Transmission Proof:** Make "no server, no internet permission, no API 31+ location tracking" immediately verifiable across both pages.
- **Real Screen Evidence:** Let genuine Android screenshots demonstrate how the scanner looks and behaves in daily use.
- **Dual-Audience Clarity:** Serve everyday Korean/English users at a glance while keeping Google Play reviewers' policy & permission checklist effortless to audit.
