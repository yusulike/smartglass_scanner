---
version: 1
slug: "docs-index-html"
primary_target: "docs/index.html"
related_targets: ["docs/privacy.html"]
---

## Direction contract

- FORM: Daylight Signal Bench (`seed:d37902f7a04a00bb`, assigned direction confirmed by user)
- GROUND & PALETTE: Mineral paper ground `#f5f6f3`, specimen plates `#ffffff`, calibrated bench `#eceee8`, 1px hairline borders `#d4d8cf` / `#c6ccc1`, carbon ink `#14191e`, secondary ink `#475259`, Emerald Signal `#0b6e4f`, Amber Gate Caution `#b85d00`, Optical Cobalt `#154c9b`, Alert Red `#b82e24`.
- TYPOGRAPHY: `Noto Sans KR` (400/500/700/800) + `JetBrains Mono` (400/500/600) with tabular numerals (`tabular-nums`) for `-55 dBm`, hex IDs (`0x058E` etc.), plate numbers, and permission constants. Zero banned fonts.
- FIRST VIEWPORT (`docs/index.html`): Single daylight bench split (`1.15fr` / `0.85fr`), top hairline navigation bar with pinned `KR | EN` switch, clean `<h1>` (no kickers/eyebrows), 3-cell zero-transmission & `-55 dBm` & `8 IDs` pledge ledger on the left, interactive BLE RSSI Distance/Gate Gauge alongside `screenshots/main_dashboard.png` Specimen `Fig. 01` on the right.
- SIGNATURE INTERACTION: Live interactive BLE RSSI Proximity & Gate Mode Simulator (`-90 dBm` to `-30 dBm` slider + 3 preset distance buttons updating Green/Amber/Red Gate state, EMA/hysteresis behavior explanation, and haptic cue readout) plus clean stateful `KR / EN` bilingual switch across all sections and specimen modal view for the 4 phone screenshots.
- COMPANION SURFACE (`docs/privacy.html`): Read mode using the identical daylight mineral frame (`#f5f6f3` ground, `#14191e` ink, `#d4d8cf` hairline rules, `JetBrains Mono` permission identifiers, same top bar with `#ko` / `#en` jumps) wrapping a calm `66ch` reading column and structured Android permission matrix table.
- ANTI-AI DISCIPLINE: Zero eyebrows above headings, zero equal 2x2 numbered cards, zero uniform icon+heading+text boxes, zero dark-navy glow/blur halos.
