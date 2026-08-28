# BuildAid 1.8 — UI Improvements (2026-08-28)

Implementation of UI fixes based on the [agy-review-ui-0900.txt](file:///C:/Users/catyo/Music/ModMincraft/docs/agy-review-ui-0900.txt) review.

---

## Changes Summary

### 1. Theme Constants — `BuildAidMenuScreen.java`

**File:** [`BuildAidMenuScreen.java`](file:///C:/Users/catyo/Music/ModMincraft/src/main/java/com/foxo/buildaid/screen/BuildAidMenuScreen.java#L67-L82)

**Problem:** Layout constants (`HEADER_HEIGHT`, `FOOTER_HEIGHT`, etc.) used inconsistent double-tab
indentation instead of single-tab like other class-level fields.

**Fix:** Normalized all constants at lines 67–82 to consistent single-tab indentation, matching the
rest of the class. Semantic spacing aliases (`GAP_SECTION`, `GAP_ROW`, etc.) now align properly.

---

### 2. ModPlayersHudElement — Dynamic Sizing

**File:** [`ModPlayersHudElement.java`](file:///C:/Users/catyo/Music/ModMincraft/src/main/java/com/foxo/buildaid/hud/ModPlayersHudElement.java#L42-L54)

**Problem:** Hardcoded `padX = 8` and fixed spacing between the mod-players chip and InfoHud.
Short text could cause the chip to look visually collapsed.

**Fixes:**
- `padX` now uses `Theme.PAD` instead of hardcoded `8`
- Added minimum box width (`minBoxW = 80`) via `Math.max()` to prevent visual collapse
- `boxH` uses `+2` (was `+1`) for proper vertical breathing room
- Minimum height guard: `Math.max(18, ...)` prevents sub-pixel chips
- InfoHud gap spacing uses `Theme.PAD / 2` instead of hardcoded `4`

---

### 3. ModButton — Padding

**Files:**
- [`Theme.java`](file:///C:/Users/catyo/Music/ModMincraft/src/main/java/com/foxo/buildaid/screen/Theme.java#L38)
- [`ModButton.java`](file:///C:/Users/catyo/Music/ModMincraft/src/main/java/com/foxo/buildaid/screen/widget/ModButton.java#L72-L73)

**Problem:** `BUTTON_LABEL_PADDING = 6` was too tight, causing text to nearly touch button edges
on narrow buttons. Dead variable `minButtonWidth` calculated but never used.

**Fixes:**
- `Theme.BUTTON_LABEL_PADDING` increased from `6` → `8` for better breathing room
- Removed unused `minButtonWidth` variable from `ModButton.extractWidgetRenderState()`
- Updated comment to reflect semantic padding usage

---

### 4. InfoHudElement — Box Height & Color Contrast

**File:** [`InfoHudElement.java`](file:///C:/Users/catyo/Music/ModMincraft/src/main/java/com/foxo/buildaid/hud/InfoHudElement.java#L86)

**Problem:**
1. Box height was too tight — bottom text line crowded the edge
2. Purple color theme (`0xFF9B59B6`) had insufficient luminance contrast against dark
   HUD backgrounds (`0x88121620` / `0xE8080C14`)

**Fixes:**
- Added `+2` px bottom breathing room to `boxHeight` formula (L86)
- Changed purple theme color from `0xFF9B59B6` → `0xFFC084FC` (higher contrast, L191)

---

## Build Verification

✅ `gradlew.bat build --console=plain -q` — **exit 0** (clean compile)

## Review Reference

All changes address items from sections §4 and §5 of the
[UI review](file:///C:/Users/catyo/Music/ModMincraft/docs/agy-review-ui-0900.txt).
