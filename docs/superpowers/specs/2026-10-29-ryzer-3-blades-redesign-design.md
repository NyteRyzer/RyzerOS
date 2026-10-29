# Ryzer OS 3.0 — "Blades" redesign

Date: 2026-10-29 · Status: approved in conversation, pending written-spec review

## Why

The owner finds the current UI **dated/plain**, **too busy**, and **not unique enough** (too close to a 3DS copy).
They chose the **"Bold & gamer"** direction, and a scope of **new styling everywhere plus a new Home layout**.
They also asked for a **new boot animation** that matches the design.

Mockups the owner approved: `docs/superpowers/mockups/blades-home.png` (Home, option A "Blades") and
`docs/superpowers/mockups/blades-lists.png` (game list and Settings).

## Success criteria

1. A fresh install or an update opens on the new Blades Home, in the new style, on both screens.
2. Every screen that draws through the shared toolkit (menus, lists, dialogs, header, hints, toasts) shows the new style.
3. All 25 built-in themes and user-made themes still work and colour the new style.
4. The old look stays reachable through *Look & feel → Interface style: Classic*.
   The old Home layouts (3DS, Ribbon, Grid) stay available.
5. No frame-rate regression on Home. The emulator run logs average Home draw time, which must stay at or below
   the current HomeShell's within 25%.
6. The emulator test suite passes: screenshots, swap, update and artwork checks, with no `crash.txt`.

## Design

### 1. Blades Home (new screen, `Blades.kt`)

**Bottom screen**
- Tabs: CONTINUE · SYSTEMS · COLLECTIONS · TOOLS. Switch with L / R or by tapping a tab.
  - **Continue** holds recent games (newest first) and pinned games.
  - **Systems** has one blade per non-empty, non-hidden system. Art comes from the custom tile picture, else the
    system's most-played game cover, else the system colour with its short name.
  - **Collections** holds Favorites, For You, smart collections and user collections.
  - **Tools** holds Search, Stats, Wrapped, Themes, Notifications, Tools menu, Settings and Device.
- Content comes from the existing `homeTiles()` / `openTile()`. Tiles are grouped into tabs by `Tile.kind`, and the
  Home tile editor's hidden-tile set still applies.
- Blades are parallelograms, skewed 26 px over their height:
  - Unselected: 92×236, art darkened 45%.
  - Selected: 150×290, with a glow and the accent outline.
  - Each blade has a coloured name band: the system colour, or the accent colour for non-system tiles.
- Left / right moves the selection, and the row scrolls smoothly to keep it centred (eased, as HomeShell does now).
- Below the row: the item name in bold italic, plus one dim detail line from `Tile.note` (truncated).
- Buttons are unchanged: A open, X random, Y search, START settings, SELECT themes.
  - Touch: tap selects, tapping the selected blade opens it, and a horizontal swipe moves.
  - Kid mode keeps the same restrictions as HomeShell.

**Top screen**
- The background is the selected item's art, blurred once and cached (or the theme background), darkened, with a diagonal
  dark wedge.
- HUD strip: avatar, greeting, daily-goal ring, battery as 4 slanted segments, and the clock.
- A slanted cover card with a glow (art or system block), a system chip (parallelogram, accent fill), and a bold italic
  title (up to 2 lines).
- Two segmented "power bars" (16 segments each):
  - Games: play time against the longest-played game, and rating out of 5.
  - Systems: game count against the largest system, and played games against total.
- A bottom band with the resume line or note, else the notification ticker, else tips.

### 2. Shared style ("skin") in the toolkit

A new `Skin` object sets the drawing style. `G.panel`, `G.topBar`, `G.hints`, `G.toast` and `G.scrollbar` branch on
`cfg.uiStyle` ("blades" | "classic").

- **Panels and rows:** parallelograms (8–18 px skew depending on height), the panel colour at 92%, a 1 px border, and
  the accent glow on the selected one. The theme's corner radius is ignored in Blades.
- **Header:** a slanted accent title tab top-left (bold italic, uppercase), battery segments plus clock top-right, and a
  darker strip behind.
- **Hints:** slanted accent key chips (A / B / X / Y / START) followed by bold italic labels.
- **Background:** the theme gradient plus faint diagonal stripes. The stripes go into the existing background cache, so
  there is no per-frame cost.
- **Menu rows** (`Menu.draw`): slanted rows 44 px high with an 8 px gap. The selected row shifts left 8 px, glows, and
  uses bold italic. Items can carry a colour stripe (`Item.colorKey` / new `Item.stripe`); Settings sections get one each.
- **Game list** (`GameList.drawList` / details): slanted rows. The details side shows the slanted cover, year ·
  developer · genre, and a play-time segment bar.
- **Now Playing:** the same header and slanted buttons, with time and battery as segment bars.
- **Glow:** pre-rendered per (shape size, colour) into a small LRU bitmap cache. No `BlurMaskFilter` per frame.

### 3. Type

- One OFL display font is bundled: **Titillium Web Bold Italic** (`assets/fonts/TitilliumWeb-BoldItalic.ttf`, 70 KB,
  from google/fonts, with `OFL.txt` alongside).
- It is used for titles, selected rows, tabs and chips. Body text keeps the current font setting.
- The user's *Font* setting still applies to body text.

### 4. Boot animation "Strike"

- A new `bootStyle` value, "strike", which is the default for Blades. About 2.0 s, and any button skips it.
- **Main screen**, in order:
  1. 0–0.5 s: 5 accent parallelogram blades slash diagonally across.
  2. 0.4–0.9 s: the Ryzer mark scales from 140% to 100% with a glow.
  3. 0.8–1.4 s: **RYZER OS** reveals left to right, in bold italic, with a 2-frame red/cyan offset.
  4. 1.6–2.0 s: blades slide off.
- **Top screen:** a 16-segment loading bar fills in, with the version and device below.
- The existing styles (classic, glitch, minimal) stay.

### 5. Settings and migration

- New config field `uiStyle` (default "blades"). On first run after updating, set `uiStyle = "blades"`,
  `homeLayout = "blades"` and `bootStyle = "strike"` once (tracked by `uiVersion = 3`). Show a one-time notice:
  "New look! Switch back in Settings → Look & feel."
- *Look & feel* gains **Interface style** (Blades / Classic) and adds "Blades" to **Home layout**.
- New built-in theme **Ryzer Strike** (`ryzer/themes/ryzer-strike.json`): bg `#0A0A10`/`#120A12`, panel `#1A1A24`,
  accent `#FF2A3D`, accent2 `#00E5FF`, text `#F5F5FA`. It becomes the default theme for new installs only.
- Existing users keep their theme.

### 6. Performance rules

- Blurred top-screen backgrounds are cached per item (max 8).
- Glow bitmaps are cached by size and colour (max 24).
- Covers keep the existing image cache.
- When animations are off or battery saver is on: no pulse, glint or slide, and the static layout stays.

## Testing

- **Emulator run (`tools/screenshots`):** new shots in Blades style for:
  - Home: Continue, Systems and Collections tabs
  - a game list, Settings, Tools, Now Playing (via the swap test), the boot screen (mid-animation), and Wrapped
  - plus one Classic shot to confirm the old style still works
- **Frame time:** a showcase step `perf` draws Home 120 times off-screen and logs the average ms for Blades and for
  Classic. CI prints both, and a release needs Blades ≤ 1.25 × Classic.
- **Regression:** the existing swap, update and artwork checks, and no `crash.txt`.
- **Manual check by the owner on the RG DS** after release: Home, a game launch and return, and the boot animation.

## Out of scope

- Redesigning Theme Studio's controls (it inherits the new panel and row style only).
- New features.
- The Linux edition (it keeps its current look).
