# Ryzer OS 3.0 "Blades" Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Android edition the approved "Blades" look: a new Home, a slanted bold-italic style on every shared toolkit surface, and the "Strike" boot animation. Ship it as Ryzer OS 3.0.0 with a Classic fallback.

**Architecture:** A new `Skin` object holds the Blades geometry, the display font and a cached glow renderer. The existing `G` toolkit functions (`panel`, `topBar`, `hints`, `toast`, `scrollbar`, `background`) branch on `cfg.uiStyle`, so most screens restyle without being touched. `Menu`, `GameList` and `NowPlayingView` get small Blades branches. `BladesHome` is a new `Screen` built on the existing `homeTiles()` / `openTile()`. `StrikeBoot` is a new boot style inside `BootScreen`. Logic that needs no Android (tab grouping, power-bar segments, boot timeline, parallelogram math) lives in plain Kotlin and gets JVM unit tests. Everything visual is checked by the existing emulator workflow.

**Tech Stack:** Kotlin 2.0.21, Android Canvas (no AndroidX), compileSdk 35 / minSdk 23, Gradle 8.14.3 / AGP 8.7.3, JUnit 4.13.2 (new, test-only), Python 3 + adb emulator harness in `tools/screenshots`.

**Spec:** `docs/superpowers/specs/2026-10-29-ryzer-3-blades-redesign-design.md` (mockups: `docs/superpowers/mockups/blades-home.png`, `blades-lists.png`)

## Global Constraints

- No AndroidX or runtime dependencies. JUnit is `testImplementation` only.
- The display font is `assets/fonts/TitilliumWeb-BoldItalic.ttf` (70 KB, from google/fonts `ofl/titilliumweb/`), with `assets/fonts/OFL.txt`.
- Config: `uiStyle` is `"blades"` | `"classic"`, default `"blades"`. Migration runs when `uiVersion < 3`: set `uiStyle="blades"`, `homeLayout="blades"`, `bootStyle="strike"`, then save `uiVersion = 3`.
- One-time notice text, exactly: "New look! Switch back in Settings → Look & feel."
- New theme `ryzer/themes/ryzer-strike.json`, id `ryzer-strike`, name "Ryzer Strike":
  - colours: bg_top `#0A0A10`, bg_bottom `#120A12`, panel `#1A1A24`, accent `#FF2A3D`, accent2 `#00E5FF`, text `#F5F5FA`
  - the remaining keys follow `ryzer-red.json`
  - it is the default theme for **new installs only** (`Config.theme` default)
- Blades geometry:
  - Unselected blade: 92×236, art darkened 45%. Selected blade: 150×290. Skew: 26 px.
  - Rows: 44 px high, 8 px gap; the selected row shifts 8 px.
  - Panel and row skew: 8–18 px depending on height.
  - Power bars: 16 segments. Battery: 4 slanted segments.
- Strike boot timeline, in seconds:
  1. blades 0–0.5
  2. mark 0.4–0.9 (scale 140%→100%)
  3. title 0.8–1.4
  4. hold
  5. blades off 1.6–2.0
  - Total 2.0 s, and any button skips.
- Caches: blurred top backgrounds max 8; glow bitmaps max 24. No per-frame `BlurMaskFilter`.
- When animations are off or battery saver is on: no pulse, glint or slide.
- Version `3.0.0` in `ryzer/__init__.py`.
- Releases follow the existing flow: push with `[skip ci]`, run the screenshots workflow, then dispatch `build.yml`.

## Review Focus

1. **Empty library** (no games on a fresh install): Blades Home must open on a tab that has items (Tools always has Settings), show "No games yet" on empty tabs, and never index an empty list. Pinned by `BladeTabsTest.emptyGamesKeepsToolsTab` (Task 4) and the emulator `blades-empty` shot (Task 6).
2. **Tabs emptied by the tile editor or kid mode** (all of Collections hidden): skip empty tabs when cycling with L/R. Pinned by `BladeTabsTest.cycleSkipsEmptyTabs` (Task 4).
3. **Light themes** (Snow, Arctic): glow, chips and selected text must stay readable. The selected text uses `highlight_text` on accent fills, and `text` elsewhere. Pinned by the emulator `blades-snow` shot (Task 6).
4. **Single-screen devices** (top screen not visible): the bottom screen shows a compact hero strip, as HomeShell's `compactBanner` does now. Pinned by the emulator `land-home` shot in Blades (Task 6).
5. **Very long titles / system names**: truncate with `maxW` everywhere, and allow 2 title lines max on the top screen. Pinned by the emulator `blades-games` shot, using the sample "Castlevania - Aria of Sorrow" (Task 6).

---

### Task 1: Skin foundation (font, geometry, glow, config, theme)

**Files:**
- Create: `android/app/src/main/java/com/ryzer/os/Skin.kt`
- Create: `android/app/src/main/assets/fonts/TitilliumWeb-BoldItalic.ttf`, `android/app/src/main/assets/fonts/OFL.txt`
- Create: `ryzer/themes/ryzer-strike.json`
- Create: `android/app/src/test/java/com/ryzer/os/SkinGeometryTest.kt`
- Modify: `android/app/build.gradle.kts` (add `testImplementation("junit:junit:4.13.2")`)
- Modify: `android/app/src/main/java/com/ryzer/os/Data.kt` (Config: `uiStyle`, migration to `uiVersion = 3`, default theme `ryzer-strike`)
- Modify: `.github/workflows/build.yml` and `.github/workflows/screenshots.yml`: before the "Build APK" step, run `./gradlew --no-daemon testReleaseUnitTest`

**Interfaces:**
- Produces:
  - `object SkinMath` (pure, in `Skin.kt`):
    - `fun para(x: Float, y: Float, w: Float, h: Float, skew: Float): FloatArray`: 8 floats, clockwise from the top-left, with the top edge shifted right by `skew`.
    - `fun skewFor(h: Float): Float` returns `(h * 0.3f).coerceIn(8f, 18f)`.
    - `fun segmentsOn(value: Double, max: Double, count: Int = 16): Int`: rounds down; returns 0 if `max <= 0`, `count` if `value >= max`.
  - `object Skin`:
    - `val blades: Boolean` (reads `Ryzer.instance?.cfg?.uiStyle == "blades"`)
    - `fun display(ctx: Context): Typeface`, cached from assets
    - `fun parallelogram(c: Canvas, r: RectF, color: Int, skew: Float = SkinMath.skewFor(r.height()))`
    - `fun outlinePara(c: Canvas, r: RectF, color: Int, width: Float, skew: Float = SkinMath.skewFor(r.height()))`
    - `fun glow(c: Canvas, r: RectF, color: Int, skew: Float)`: draws a cached blurred bitmap, LRU max 24, keyed by (w, h, skew, color)
    - `fun segments(c: Canvas, x: Float, y: Float, w: Float, h: Float, on: Int, count: Int, onColor: Int, offColor: Int)`
    - `fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Char = 'l', maxW: Float = 0f)`: display font, same top-edge convention as `G.text`
  - `Config.uiStyle: String`

- [ ] **Step 1: Write the failing tests** in `SkinGeometryTest.kt`:
  - `paraShiftsTopEdge`: `SkinMath.para(10f, 20f, 100f, 40f, 12f)` equals `floatArrayOf(22f,20f, 122f,20f, 110f,60f, 10f,60f)`
  - `skewClamps`: `skewFor(10f) == 8f`, `skewFor(40f) == 12f`, `skewFor(200f) == 18f`
  - `segmentsRounding`: `segmentsOn(7.0, 14.0) == 8`, `segmentsOn(0.0, 0.0) == 0`, `segmentsOn(30.0, 14.0) == 16`, `segmentsOn(1.0, 16.0, 16) == 1`
- [ ] **Step 2: Run** `cd android && ./gradlew --no-daemon testReleaseUnitTest` in CI (dispatch `screenshots.yml`; there's no Android SDK locally). Expected: compile failure, because `SkinMath` is not defined.
- [ ] **Step 3: Implement** `SkinMath`, `Skin`, the font asset (`curl -o .../TitilliumWeb-BoldItalic.ttf https://raw.githubusercontent.com/google/fonts/main/ofl/titilliumweb/TitilliumWeb-BoldItalic.ttf`, plus `OFL.txt` from the same folder), the JUnit dependency, the Config field and migration, the Strike theme JSON, and the CI test steps.
- [ ] **Step 4: Verify.** Run `python3 -m pytest -q` locally (the Linux edition renders every theme, including the new one). Expected: all pass. Then push `[skip ci]`, dispatch `screenshots.yml`, and expect the "testReleaseUnitTest" and "Build APK" steps to succeed.
- [ ] **Step 5: Commit** with the message "3.0 Task 1: Skin foundation, display font, Ryzer Strike theme [skip ci]".

### Task 2: Restyle the shared toolkit and menus

**Files:**
- Modify: `android/app/src/main/java/com/ryzer/os/Ui.kt`:
  - `G.panel`, `G.topBar`, `G.hints`, `G.toast`, `G.scrollbar`: Blades branches
  - `G.background`: diagonal stripes baked into the cached bitmap when `Skin.blades`, adding `uiStyle` to the cache key
- Modify: `android/app/src/main/java/com/ryzer/os/Screens.kt`:
  - `Item`: add `var stripe: Int? = null`
  - `Menu.draw`: Blades rows
  - `Message`, `Confirm`, `Keyboard`: inherit through `G.panel`
- Modify: `android/app/src/main/java/com/ryzer/os/Extra.kt`:
  - stripes on the top-level Settings items: Personalize red, Notifications orange, Emulators purple, Library green, Display cyan, Goals gold, Updates grey
  - *Look & feel*: add "Interface style" (Blades / Classic)

**Interfaces:**
- Consumes: `Skin.parallelogram`, `Skin.outlinePara`, `Skin.glow`, `Skin.segments`, `Skin.text`, `Skin.blades`, `SkinMath.skewFor` (Task 1)
- Produces: `Item.stripe: Int?`. `G.*` signatures are unchanged.

- [ ] **Step 1: Add emulator shots** to `tools/screenshots/capture.py` `SHOTS`:
  - `("blades-settings", "home", "settings", "down", 0)`
  - `("classic-settings", "classic", "settings", "", 0)`
  - Add config `"classic": {"uiStyle": "classic"}` in `make_demo.py` `CONFIGS`, and `"uiStyle": "blades"` in `BASE_CONFIG`.
- [ ] **Step 2: Implement** the Blades branches as the spec's §2 describes:
  - header: slanted accent title tab, 4 battery segments, clock
  - hints: slanted accent key chips plus bold italic labels
  - menu rows: 44 px high, 8 px gap; the selected row shifts and glows; stripe at the left edge
- [ ] **Step 3: Verify.** Push `[skip ci]` and dispatch `screenshots.yml`, then pull and view `docs/screenshots/raw/blades-settings.png` and `classic-settings.png`.
  - Expected: Blades matches `blades-lists.png` (right half).
  - Expected: Classic is identical to the 2.4 look.
  - Expected: no `crash.txt` output in the log.
- [ ] **Step 4: Commit** with the message "3.0 Task 2: Blades toolkit and menu rows [skip ci]".

### Task 3: Game list and Now Playing in Blades style

**Files:**
- Modify: `android/app/src/main/java/com/ryzer/os/Home.kt`: `GameList.drawList` rows and `details()` (slanted cover via `Skin.glow` + `Skin.parallelogram` clip; year · developer · genre; a 16-segment play-time bar against the longest-played game in the list)
- Modify: `android/app/src/main/java/com/ryzer/os/DualPlay.kt`: `NowPlayingView` header, buttons (`Skin.parallelogram`), and segment bars for session time (against the daily goal or 60 min) and battery
- Test: the emulator `games` shot (existing) and the swap check's main-display capture (existing)

**Interfaces:**
- Consumes: Task 1 `Skin`, `SkinMath.segmentsOn`
- Produces: nothing new

- [ ] **Step 1: Implement** the Blades branches, leaving the Classic code paths untouched.
- [ ] **Step 2: Verify.** Dispatch `screenshots.yml` and view `games.png` and `swapcheck/after-0.png` (Now Playing).
  - Expected: slanted rows, the selected row glowing, and "2003 · Intelligent Systems · Tactical RPG" under the cover.
  - Expected: long titles are truncated, not overlapping.
  - Expected: the Now Playing buttons are slanted.
- [ ] **Step 3: Commit** with the message "3.0 Task 3: Blades game list and Now Playing [skip ci]".

### Task 4: Blades Home

**Files:**
- Create: `android/app/src/main/java/com/ryzer/os/Blades.kt` (`BladeTabs` pure logic + `BladesHome : Screen`)
- Create: `android/app/src/test/java/com/ryzer/os/BladeTabsTest.kt`
- Modify:
  - `Main.kt`: `applyHomeLayout()` and `MainActivity.start()` create `BladesHome` when `cfg.homeLayout == "blades"`
  - `render()`: treat `BladesHome` like `HomeShell` for `accentOverride`
- Modify: `Extra.kt` *Home layout* options → `listOf("blades", "3ds", "ribbon", "grid")`, labelled "Blades"
- Modify: `Showcase.kt`: add a `tab:<n>` step that sets `BladesHome.tab`

**Interfaces:**
- Consumes: `homeTiles(app): List<Tile>`, `openTile(app, Tile)`, `tileArt(app, Tile)`, `drawAvatar`, `drawGoalRing`, `Notifs`, Task 1 `Skin`
- Produces:
  - `object BladeTabs`:
    - `val NAMES = listOf("CONTINUE", "SYSTEMS", "COLLECTIONS", "TOOLS")`
    - `fun tabOf(kind: String): Int`: continue/pinned/gotd → 0; system → 1; favorites/foryou/smart/collection/recent/played/added → 2; everything else → 3
    - `fun group(kinds: List<String>): List<List<Int>>`: returns tile indices per tab, keeping their order
    - `fun next(groups: List<List<Int>>, from: Int, dir: Int): Int`: the next non-empty tab, wrapping; returns `from` if all are empty
    - `fun firstNonEmpty(groups: List<List<Int>>): Int`, which defaults to 3
  - `class BladesHome(app: Ryzer) : Screen(app)` with `var tab: Int` and `fun rebuild()`, keeping the `moved` first-tile behaviour from HomeShell

- [ ] **Step 1: Write the failing tests** in `BladeTabsTest.kt`:
  - `tabOfKinds`: `tabOf("continue")==0`, `tabOf("pinned")==0`, `tabOf("system")==1`, `tabOf("smart")==2`, `tabOf("settings")==3`, `tabOf("tools")==3`
  - `groupKeepsOrder`: `group(listOf("continue","system","favorites","system","settings"))` equals `listOf(listOf(0), listOf(1,3), listOf(2), listOf(4))`
  - `emptyGamesKeepsToolsTab`: `group(listOf("search","stats","settings"))` gives `firstNonEmpty == 3`
  - `cycleSkipsEmptyTabs`: with groups `[[0],[],[],[1]]`, `next(g, 0, 1) == 3` and `next(g, 3, 1) == 0`
  - `allEmptyStays`: `next(listOf(emptyList(),emptyList(),emptyList(),emptyList()), 2, 1) == 2`
- [ ] **Step 2: Run** the CI unit tests (dispatch `screenshots.yml`). Expected: FAIL to compile, because `BladeTabs` is undefined.
- [ ] **Step 3: Implement** `BladeTabs` and `BladesHome`, following the spec's §1 and `blades-home.png`:
  - **Bottom screen:**
    - the tab strip
    - the blade row with eased scroll, the selected blade glowing, and the name band in the system or accent colour
    - the name line and the note line
    - hints
    - touch (tap selects, tapping the selected blade opens it)
    - the compact hero strip when the top screen isn't visible (Review Focus #4)
    - "No games yet" on empty tabs (Review Focus #1)
  - **Top screen:**
    - a blurred-art background (cache max 8)
    - the HUD
    - the slanted cover card, system chip, and 2-line title
    - two power bars (games: play time against the max in the library, and rating /5; systems: game count against the largest system, and played/total)
    - the resume / ticker band
  - **Buttons:** A open, X random, Y search, START settings, SELECT themes, L/R tabs.
  - **Kid mode:** the same as HomeShell.
- [ ] **Step 4: Verify.** In CI, the unit tests pass. Then add shots to `capture.py`:
  - `("home", "home", "tab:0", "", 0)`
  - `("home-systems", "home", "tab:1", "right right", 0)`
  - `("home-collections", "home-neon", "tab:2", "right", 0)`
  - Then view them. Expected: they match `blades-home.png`, with no overlapping text and the glow visible.
- [ ] **Step 5: Commit** with the message "3.0 Task 4: Blades Home [skip ci]".

### Task 5: Strike boot animation

**Files:**
- Modify: `android/app/src/main/java/com/ryzer/os/Screens.kt` (`BootScreen`: `"strike"` branch for `draw` and `drawTop`; use `StrikeBoot.duration` when the style is strike)
- Create: `StrikeBoot` timeline object in `Blades.kt`
- Create: `android/app/src/test/java/com/ryzer/os/StrikeBootTest.kt`
- Modify: `Extra.kt` boot style options → `listOf("strike", "classic", "glitch", "minimal")`

**Interfaces:**
- Produces:
  - `object StrikeBoot`:
    - `const val duration = 2.0f`
    - `fun progress(t: Float, start: Float, end: Float): Float` (clamped 0..1)
    - `fun bladesIn(t) = progress(t, 0f, 0.5f)`
    - `fun mark(t) = progress(t, 0.4f, 0.9f)`
    - `fun title(t) = progress(t, 0.8f, 1.4f)`
    - `fun bladesOut(t) = progress(t, 1.6f, 2.0f)`
    - `fun markScale(t) = 1.4f - 0.4f * mark(t)`

- [ ] **Step 1: Write the failing tests:**
  - `phasesClamp`: `bladesIn(-1f)==0f`, `bladesIn(0.25f)==0.5f`, `bladesIn(3f)==1f`
  - `markScaleEnds`: `markScale(0f)==1.4f`, `markScale(0.9f)==1.0f`
  - `titleWindow`: `title(0.8f)==0f`, `title(1.1f)==0.5f`
  - `bladesOutLast`: `bladesOut(1.59f)==0f`, `bladesOut(2.0f)==1f`
- [ ] **Step 2: Run** the CI unit tests. Expected: FAIL, because `StrikeBoot` is undefined.
- [ ] **Step 3: Implement** `StrikeBoot` and the Strike drawing as the spec's §4 describes:
  - main screen: 5 accent parallelogram blades slashing diagonally; the Ryzer mark (an accent parallelogram with "R" in the display font) scaling with `Skin.glow`; "RYZER OS" clipped left→right, with a 2-frame red/cyan offset while `title(t) < 1`; blades sliding off
  - top screen: a 16-segment bar from `SkinMath.segmentsOn(t, duration)`, with `BuildInfo.version` and `BuildInfo.device` below
- [ ] **Step 4: Verify.** Add boot captures back to `capture.py`: start the config `"boot": {"boot": True, "bootStyle": "strike"}`, then snap at 0.5 s, 0.9 s and 1.3 s after `am start` as `boot-0..2`. Dispatch the workflow and view them. Expected: frame 0 shows blades mid-slash, frame 1 the glowing mark, and frame 2 the title reveal. Confirm the unit tests pass.
- [ ] **Step 5: Commit** with the message "3.0 Task 5: Strike boot animation [skip ci]".

### Task 6: Performance gate, migration notice, docs, release 3.0.0

**Files:**
- Modify: `android/app/src/main/java/com/ryzer/os/Showcase.kt`: add a `perf` step. It draws the current Home screen 120 times into an off-screen 640×480 `Bitmap` canvas, for Blades and then Classic (temporarily switching `cfg.uiStyle`), and writes `PERF blades=<ms> classic=<ms>` to `RyzerOS/perf.txt`.
- Modify: `tools/screenshots/capture.py`:
  - run `perf`, then `cat /sdcard/RyzerOS/perf.txt`
  - add shots `blades-snow` (config `{"theme": "snow"}`) and `blades-empty` (a run with an empty `ROMs` — push config `"empty": {"romDirs": ["/sdcard/EmptyRoms"]}` after `mkdir /sdcard/EmptyRoms`)
- Modify: `.github/workflows/screenshots.yml`: after the Screenshots step, add a step that fails if blades > 1.25 × classic, parsing the log line saved by capture to `docs/screenshots/raw/perf.txt`
- Modify: `Main.kt` `start()`: after migration (Task 1 sets a `Config.justMigrated` flag), `notify("New look! Switch back in Settings → Look & feel.", 5f)` once
- Modify: `ryzer/__init__.py` → `VERSION = "3.0.0"`. Update `CHANGELOG.md` (new `## 3.0.0: the Blades redesign` section), `WHATS_NEW` in `Fun.kt`, the README "New in 3.0" section, `site/index.html` (hero copy), and `tools/screenshots/process.py` hero lines (use the Blades shots).

**Interfaces:**
- Consumes: all earlier tasks; `Config.justMigrated: Boolean` (a transient field set by the Task 1 migration)

- [ ] **Step 1: Implement** the perf step, the migration notice, the shots and the workflow gate.
- [ ] **Step 2: Verify.** Dispatch `screenshots.yml`. Expected:
  - the unit tests pass
  - the log shows `PERF blades=X classic=Y` with X ≤ 1.25·Y, and the gate step passes
  - `blades-snow.png` is readable (Review Focus #3)
  - `blades-empty.png` shows the Tools tab and the "No games yet" state (Review Focus #1)
  - the swap, update and artwork checks pass, and there's no `crash.txt`
- [ ] **Step 3: Docs.** Run `python3 tools/screenshots/process.py` locally, update the README gallery file names if any changed, and check that `python3 tools/release_notes.py 3.0.0 --latest` renders.
- [ ] **Step 4: Release.** Commit without `[skip ci]` (or dispatch `build.yml`). Expected: `curl -L https://github.com/NyteRyzer/RyzerOS/releases/download/v3.0.0/RyzerOS.apk` returns 200.
- [ ] **Step 5: Commit** with the message "Ryzer OS 3.0: the Blades redesign".
