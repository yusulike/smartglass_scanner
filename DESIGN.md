---
name: SmartGlass Scanner — Daylight Signal Bench
description: Crisp matte mineral paper inspection bench pairing calibrated 1px grids with tactile RSSI distance telemetry.
colors:
  ground: "#f5f6f3"
  bench: "#eceee8"
  plate: "#ffffff"
  ink: "#14191e"
  muted: "#475259"
  line: "#d4d8cf"
  line-strong: "#9ca598"
  emerald: "#0b6e4f"
  emerald-tint: "#e7f4ee"
  amber: "#964a00"
  amber-tint: "#fbf0df"
  red: "#a6231a"
  red-tint: "#fceceb"
  cobalt: "#154c9b"
typography:
  display:
    fontFamily: "Noto Sans KR, system-ui, sans-serif"
    fontSize: "3rem"
    fontWeight: 800
    lineHeight: 1.18
    letterSpacing: "-0.03em"
  heading:
    fontFamily: "Noto Sans KR, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 800
    lineHeight: 1.3
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Noto Sans KR, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.65
    letterSpacing: "0em"
  mono-label:
    fontFamily: "JetBrains Mono, ui-monospace, monospace"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "0em"
rounded:
  none: "0px"
  bezel: "22px"
spacing:
  xs: "8px"
  sm: "16px"
  md: "28px"
  lg: "40px"
components:
  button-solid:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.ground}"
    rounded: "{rounded.none}"
    padding: "13px 22px"
  button-outline:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "13px 20px"
  lang-switch:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "6px 11px"
  rssi-bench:
    backgroundColor: "{colors.plate}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "16px 18px"
  gate-badge:
    backgroundColor: "{colors.red-tint}"
    textColor: "{colors.red}"
    rounded: "{rounded.none}"
    padding: "3px 8px"
  callout-notice:
    backgroundColor: "{colors.amber-tint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.none}"
    padding: "14px 16px"
---

# Design System: Daylight Signal Bench

## Overview

**Creative North Star: "Daylight Signal Bench"**

The SmartGlass Scanner web presence (`docs/index.html` and `docs/privacy.html`) replaces dark-glow software clichés with a bright, unhurried hardware inspection sheet (`#f5f6f3` mineral paper over crisp `#ffffff` plates and `#eceee8` calibrated workspace bays). Every claim is anchored to verifiable on-device facts—`0 B` network transmission, Android 12+ `neverForLocation` operation, interactive `-90` to `-30 dBm` Gate proximity simulation (`-55 dBm` trigger), and actual Android screenshots displayed as numbered archival plates.

**Key Characteristics:**
- High-legibility daylight mineral ground (`#f5f6f3`) paired with carbon ink (`#14191e`) and strict `1px` perimeter hairlines (`#d4d8cf`).
- Clean typographic separation between Korean/English reading prose (`Noto Sans KR`) and tabular telemetry, hex signatures, and Android permission constants (`JetBrains Mono`).
- Flat elevation hierarchy using surface tonal shifts (`#ffffff` / `#f5f6f3` / `#eceee8`) and hairline compartments instead of drop shadows or blur glass.

**The Flat Ledger Rule.** Boundaries, status bays, and data matrices are always drawn with crisp `1px` structural borders and muted mineral surface shifts rather than soft shadows, blur halos, or multi-pixel left stripe accents.

## Colors

Roles map directly to real physical inspection states and RF threshold signals:

- **Primary & Surface Neutrals:** `ground` (`#f5f6f3`) acts as the continuous studio field; `bench` (`#eceee8`) separates instrumentation and interactive bays; `plate` (`#ffffff`) carries focused reading columns and screenshot compartments. Primary typography reads in carbon `ink` (`#14191e`), with secondary annotations in cool slate `muted` (`#475259`).
- **Structural Rules:** `line` (`#d4d8cf`) divides columns, ledgers, and table cells; `line-strong` (`#9ca598`) frames interactive controls and icon outlines.
- **RF State & Semantic Accents:** `emerald` (`#0b6e4f`) / `emerald-tint` (`#e7f4ee`) denotes safe passive scanning and verified zero data collection; `amber` (`#964a00`) / `amber-tint` (`#fbf0df`) signals approaching threshold range and honest physical limitation callouts; `red` (`#a6231a`) / `red-tint` (`#fceceb`) marks active `>= -55 dBm` Gate Mode triggers; `cobalt` (`#154c9b`) anchors plate numbering (`FIG. 01`–`04`) and keyboard `:focus-visible` outlines.

**The Signal Semantic Rule.** Never apply `emerald`, `amber`, or `red` purely for decorative branding; reserve those hues strictly for RF proximity tiers (`GUARD`, `AMBER`, `GATE RED`), privacy verification highlights, and high-visibility limit callouts.

## Typography

Two typefaces split narrative comprehension from machine telemetry cleanly:

- **Reading & Display Face (`Noto Sans KR`):** Handles all Korean and English display headlines (`display`, `heading`), body copy (`body`), table headers, and UI labels with `word-break: keep-all` so Hangul headlines never split awkwardly mid-word.
- **Technical Monospace (`JetBrains Mono`):** Strictly reserved for tabular numbers (`tabular-nums`), RF measurements (`-55 dBm`, `8 dBm`), hex Company IDs (`0x058E`, `0x0000`–`0xFFFF`), figure codes (`FIG. 01`), package identifiers (`com.windyflow.smartglassscanner`), and Android permission constants (`BLUETOOTH_SCAN`).

**The Script Integrity Rule.** Never set Korean headings, table headers, or buttons in monospace fallback fonts; keep every Hangul phrase in `Noto Sans KR` and restrict `JetBrains Mono` strictly to identifiers, hex literals, and English telemetry labels.

## Layout

Both `docs/index.html` and `docs/privacy.html` lock to disciplined hairline grids:

- **Landing Bench (`docs/index.html`):** Centered `1320px` outer hairline frame divided at the top into a `1.12fr / 0.88fr` asymmetric daylight bench so desktop viewports (`1280x800` and `1440x900`) hold headline, CTAs, verification ledger, live RSSI simulator, and `FIG. 01` above the fold without empty vertical voids. Below the fold transitions into an asymmetric dual-mode band, a 4-column specimen contact strip, and a ruled hardware signature matrix.
- **Documentation Sheet (`docs/privacy.html`):** `1200px` outer shell pairing a `268px` sticky desktop contents rail with a calm `66ch` reading plate and ruled Android permissions table.
- **Responsive Reflow:** Below `1040px`, the bench stacks vertically and the screenshot contact strip collapses to 2 columns; below `640px` (`900px` on privacy docs), sticky navigation unsticks cleanly, `KR | EN` stays pinned beside the brand on row 1, ledgers stack cleanly without overflow, and the privacy permissions table flattens into readable per-permission blocks.

## Elevation & Depth

Surfaces sit on one planar inspection table without ambient shadows or z-axis floating effects. Hierarchy comes purely from contrast between `ground` (`#f5f6f3`), `bench` (`#eceee8`), `plate` (`#ffffff`), and `1px` perimeter hairlines. Modal screenshot inspection (`<dialog>`) separates from the background via a flat `rgba(20, 25, 30, 0.72)` backdrop and crisp `1px solid #14191e` border.

## Shapes

Sharp rectangular geometry (`0px` radius) governs every button, notice frame, toggle, ledger, and screenshot compartment across desktop and mobile. The sole intentional exception (`22px` `bezel` radius) wraps only the captured real Android device hardware frames (`screenshots/*.png`) so actual phone corners read accurately without adding artificial rounded containers around prose.

## Components

### Buttons (`button-solid`, `button-outline`)
- **Solid Primary:** Carbon ink fill (`#14191e`), mineral paper text (`#f5f6f3`), sharp `0px` corners, `13px 22px` padding, `#273038` hover shift.
- **Outline Secondary:** White plate fill (`#ffffff`), `#9ca598` `1px` border darkening to `#14191e` on hover.

### Language Switch & Top Navigation (`lang-switch`)
- Inline `KR | EN` binary toggle framed in `1px solid #14191e`; active selection fills solid `#14191e` / `#f5f6f3` while sticky `60px` desktop bar unsticks below `640px` keeping brand and toggle aligned on row 1.

### RSSI Proximity Bench (`rssi-bench`, `gate-badge`)
- Interactive calibrated `-90` to `-30 dBm` track dividing passive Guard (`50%` emerald tint), approaching buffer (`8.3%` amber tint), and Gate trigger (`41.7%` red tint at exact `58.33%` `-55 dBm` line), synchronized with preset buttons and an `aria-live` explanation bar.

### Callout Notices (`callout-notice`)
- Full `1px` perimeter borders (`#964a00` on `#fbf0df` for detection boundaries in `index.html`; `#0b6e4f` on `#e7f4ee` for developer contact in `privacy.html`).

## Do's and Don'ts

### Do:
- **Do** keep `word-break: keep-all` on Korean headings and body text so multi-syllable words never split awkwardly mid-line.
- **Do** place figure codes (`FIG. 01`–`04`) strictly below headings as plate sub-labels rather than floating kickers above headlines.
- **Do** align simulated threshold markers and percentage track boundaries directly to exact dBm values (`58.33%` for `-55 dBm` inside `-90..-30 dBm`).

### Don't:
- **Don't** place mini uppercase kickers or eyebrows directly above `<h1>`, `<h2>`, or `<h3>` headings.
- **Don't** use drop shadows, glassmorphism blur, neon-on-dark gradients, or `2px–4px` left accent border stripes (`border-left`) on callouts or navigation items.
- **Don't** organize features or screenshots into padded `2x2` equal cards with leftover blank vertical voids inside each cell.
