
# UI Review via agy (Claude Opus 4.6) — 2026-08-27

## Files Reviewed
1. `src/main/java/com/foxo/buildaid/hud/InfoHudElement.java`
2. `src/main/java/com/foxo/buildaid/hud/ModPlayersHudElement.java`
3. `src/main/java/com/foxo/buildaid/screen/BuildAidMenuScreen.java`
4. `src/main/java/com/foxo/buildaid/screen/widget/ModButton.java`
5. `src/main/java/com/foxo/buildaid/theme/Theme.java`

## Top 5 UI Improvements

### 1. Inconsistent vertical spacing (🔴 HIGH)
- **Location:** All tabs in `BuildAidMenuScreen.java`
- **Issue:** 6 different magic-number gaps (20–30px) with no semantic pattern
- **Fix:** Replace magic numbers with semantic spacing constants:
```java
static final int GAP_SECTION = 24;
static final int GAP_ROW = 16;
static final int GAP_COMPACT = 12;
```

### 2. ModPlayersHudElement text off-center (🟡 MEDIUM)
- **Location:** `ModPlayersHudElement.java` lines ~40-55
- **Issue:** 3px top vs 4px bottom padding in pill; hardcoded `boxH=16` ignores GUI scale
- **Fix:** Calculate box height dynamically:
```java
int boxH = Math.max(18, font.fontHeight + 8);  // scales with font
int vPad = (boxH - font.fontHeight) / 2;
```

### 3. TEXT_DISABLED fails contrast (🟡 MEDIUM)
- **Location:** `Theme.java` color definitions
- **Issue:** 2.6:1 contrast ratio; unreadable in bright biomes
- **Fix:** Increase contrast to 4.5+:1
```java
// From #808080 to #A0A0A0 (or use Theme.accent().darker())
```

### 4. Button inner padding too tight (🟠 LOW-MED)
- **Location:** `ModButton.java`
- **Issue:** 4px inner padding vs 3px corner radius causes premature label truncation
- **Fix:** Increase to 6px and calculate min width:
```java
this.width = Math.max(minWidth, textWidth + padding * 2);
```

### 5. Grouped HUD box height undercount (🟠 LOW-MED)
- **Location:** `InfoHudElement.java`
- **Issue:** Section dividers drawn 1px past calculated boundary
- **Fix:** Track max Y of last section element and use that for box bounds
