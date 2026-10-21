# Ryzer OS: Linux edition and technical notes

The Linux edition is for handhelds running a custom firmware (ROCKNIX, Knulli/Batocera, muOS or ArkOS). Most people want the **Android edition** instead: see the [main page](../README.md).

![Linux edition home screen](screenshots/linux/01-home.png)

## What Ryzer OS is (and isn't)

Neither edition replaces the kernel. The **Android edition** is a launcher app that runs on the stock OS. The **Linux edition** runs **on top of a custom firmware** (ROCKNIX, Knulli, muOS or ArkOS), which supplies the kernel, drivers and emulators. Ryzer OS replaces how the device looks and feels and tunes how hard it runs:

| Part | What it does |
| --- | --- |
| `ryzer/` | The frontend: dashboard, game lists, search, favorites, themes, theme editor, settings. It's plain Python and pygame, so you can change anything. |
| `system/ryzer-perf` | Switches CPU, GPU and memory clocks between **Battery / Balanced / Performance / Turbo**. |
| `system/ryzer-tune` | Boot tweaks: 1.5 GB zram compressed swap, VM and scheduler tuning, SD read-ahead, and THP for JIT emulators. |
| `ryzer/tweaks.py` | One button that sets Azahar (3DS), Dolphin (GC) and AetherSX2 (PS2) to their fastest settings. It backs up the originals first. |

Writing a kernel and drivers from scratch for one handheld isn't realistic, and it wouldn't make games run faster. The speed comes from clocks, memory and emulator settings, and Ryzer OS controls all three.

### Honest performance expectations

How well each system runs depends on the device's chip. The Android edition shows each system's requirements on its tile (for example, "Wants a flagship chip (Snapdragon 8 Gen 2 class)"). Roughly:

| Chip class (examples) | 3DS | GameCube / Wii | PS2 | Switch |
| --- | --- | --- | --- | --- |
| RK3568 (RG DS, RG 353) | Light games | A few light games | Very light games | No |
| Unisoc T618/T820 (RG 405/406, RP4) | Many games | Many 2D/light 3D | Light games | No |
| Dimensity 1100 / SD 865 (RP4 Pro, Odin 1) | Most games | Most games | Many games | Light games |
| Snapdragon 8 Gen 2+ (AYN Thor, Odin 2/3, RP5/6) | Full library | Most games | Most games | Many games |

On Android, Ryzer can't change CPU clocks without root. Use the device's own performance mode (AYN, Retroid and Anbernic all have one). Ryzer's **Boost** option closes cached background apps before heavy games to free RAM.

#### Linux edition on the RG DS

The RG DS uses a **Rockchip RK3568**: 4× Cortex-A55 at 2.0 GHz, a Mali-G52 GPU and 3 GB of RAM. That's an entry-level chip. Software can make it run at its best, but it can't make it faster than its hardware allows. Turbo uses the highest *stock* clocks only. There's no overclocking, which could damage the device.

| System | On the RG DS | Notes |
| --- | --- | --- |
| NES, SNES, GB/GBC/GBA, Genesis, PS1, arcade | Full speed | |
| Nintendo DS | Runs well | Native dual-screen |
| N64, PSP, Dreamcast | Mostly playable | |
| **3DS** | **Light games only** | 2D and lighter 3D titles at 1x resolution. Big 3D games run below full speed. |
| **GameCube** | **Experimental** | Only a handful of lighter games get close to full speed. |
| **PS2** | **Experimental** | You have to install an ARM64 PS2 emulator yourself. Only very light titles get close to full speed. |

The dashboard shows this rating on every system tile, so you know what to expect before you launch a game.

## Linux edition features

- **Console-style dashboard**: animated tile ribbon, boot animation, button-prompt bar, clock, battery and Wi-Fi status
- **Dual-screen aware**: the top screen shows a big clock, box art and game details; the bottom screen is the menu. It also works on a single screen.
- **20 built-in themes**: Ryzer Red, Neon Night, Midnight Blue, Arctic, Forest, Sunset, Retro Amber CRT, Matrix,
  Vaporwave, Pocket DMG, Snow, Crimson Steel, Ocean Deep, Purple Haze, Gold Rush, Cherry Blossom, Carbon, Lava,
  Mint and Cyberpunk. Each one previews live as you browse.
- **Theme editor on the device**: change all 12 colors (R/G/B), the background style (gradient, scanlines, grid, dots, waves, stars), tile style, corner radius, text size and particles, then save under your own name
- **Per-system speed profiles**: 3DS, GameCube and PS2 switch to Turbo automatically when they launch, and the menu goes back to Balanced to save battery
- **Emulator speed tweaks**: one-button tuning for Azahar, Dolphin and AetherSX2, plus a restore option
- **Library**: automatic ROM scan, clean names that drop tags like "(USA) [!]", box art, favorites, recently played, play time and launch counts
- **Continue tile**: jumps straight back into your last game
- **Surprise me**: launches a random game
- **Search**: on-screen keyboard that searches every system
- **Letter jump** (L/R) and **page jump** (left/right) in game lists
- **Per-game options**: favorite, speed profile, game info and the exact launch command
- **System info**: live CPU clock, temperature, RAM, storage and battery
- **Screenshots** (SELECT + R), **dim when idle**, **button test**, **swap A/B**, menu sounds with volume control, and a 30/60 fps menu cap

## Install the Linux edition

You need **ROCKNIX** or **Knulli** (fully supported), or **muOS** or **ArkOS** (experimental). The installer detects which one you have.

1. On a PC, build the package (or download a release zip):
   ```sh
   ./build/package.sh            # -> dist/RyzerOS-1.0.0.zip
   ```
2. Copy the zip to the device and unzip it (for example to `/storage/RyzerOS` on ROCKNIX or `/userdata/RyzerOS` on Knulli).
3. On the device (over SSH, or with a terminal port), run:
   ```sh
   sh /storage/RyzerOS/install.sh          # add --boot to apply the speed tweaks at every boot
   ```
4. Open **Ports → Ryzer OS**. To return to the stock frontend, hold **SELECT + START** or use Power → Exit.

To uninstall, run `sh /storage/RyzerOS/uninstall.sh`. Add `--all` to also delete your themes and settings.

Ryzer reads the firmware's usual ROM folders (`/storage/roms`, `/userdata/roms`, `/mnt/sdcard/ROMS`, `/roms` and `/roms2`). Folder names are matched without regard to case.
Box art is picked up from an `images/` (or `media/images/`) folder next to the ROMs.

> **Status:** the Linux frontend, themes, installer and tuning scripts are tested on a PC (see `tests/`), including headless rendering at both screen layouts. The Android APK is compiled by CI on every push. Neither edition has been tested on real handheld hardware yet. If something doesn't work on your firmware, look at `/storage/ryzer/ryzer.log` first.

## Controls

| Where | Button | Action |
| --- | --- | --- |
| Anywhere | SELECT + START (hold) | Exit Ryzer OS |
| Anywhere | SELECT + R | Screenshot (saved to `/storage/ryzer/screenshots`) |
| Home | X / Y / SELECT / START | Random game / Search / Themes / Settings |
| Game list | A / X / START | Play / Favorite / Options |
| Game list | L / R | Jump to the previous / next letter |
| Themes | X / Y | Edit a copy / Delete a custom theme |
| Theme editor | Left/Right, Y | Adjust value, switch the R/G/B channel |

## Editing Ryzer OS

Everything is meant to be edited:

- **Themes** are JSON files. Built-in themes are in `ryzer/themes/` and your own go in `/storage/ryzer/themes/`. Copy one, change the hex colors and it shows up in the picker.
  ```json
  { "id": "my-theme", "name": "My Theme",
    "colors": { "bg_top": "#101020", "accent": "#FF2A3D", "...": "12 keys total" },
    "style":  { "background": "grid", "tile_style": "glow", "corner_radius": 12,
                "font_scale": 1.0, "particles": true } }
  ```
- **Systems and emulators**: copy `ryzer/data/systems.json` to `/storage/ryzer/systems.json` and edit it. Each system lists its ROM folders, file extensions and a fallback chain of launch commands. `ra:<core>` means RetroArch with that core.
- **Settings** are saved in `/storage/ryzer/config.json`, including key mappings and the default speed profile for each system.
- **The UI** is in `ryzer/screens/`. Each screen is a small class with `handle()`, `draw()` and `draw_top()` methods.

### Develop on a PC

```sh
pip install pygame-ce pytest
python3 -m ryzer --windowed --dry-run              # keyboard: arrows, Z=A, X=B, C=X, V=Y, Q/E=L/R, S=Start, A=Select
python3 -m ryzer --windowed --dry-run --layout dual
python3 tools/preview.py shots/                    # render every screen to PNG
python3 -m pytest
```

## Layout

```
android/          Android edition (Kotlin, no third-party libraries)
  app/src/main/assets/   systems.json + emulators.json (regenerate with tools/gen_android_data.py)
ryzer/            Linux edition frontend (Python) + the 20 shared themes
  screens/        boot, home, games, themes + editor, settings, keyboard
  themes/         20 built-in themes (JSON)
  data/           systems.json - systems, folders, emulator commands, speed ratings
system/           ryzer-perf (speed profiles), ryzer-tune (boot tweaks)
install/          install.sh / uninstall.sh
build/            package.sh - builds the installable zip
tools/preview.py  headless screenshot renderer
tests/            pytest suite (core logic + UI fuzzing)
```
