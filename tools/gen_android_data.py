"""Generates the Android emulator + system tables in android/app/src/main/assets/.

Launch intents follow ES-DE's Android configuration (es_systems.xml / es_find_rules.xml),
which is maintained against real emulator builds. Edit this file and re-run it,
or put an emulators.json / systems.json in /storage/emulated/0/RyzerOS/ on the device.
"""

import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "android/app/src/main/assets"
CLEAR = ["clear_task", "clear_top"]
VIEW = "android.intent.action.VIEW"
MAIN = "android.intent.action.MAIN"

# id: (name, components, action, category, data, extras, flags)
EMU = {
    "azahar": ("Azahar", ["org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
                          "io.github.lime3ds.android/org.citra.citra_emu.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "azaharplus": ("AzaharPlus", ["io.github.azaharplus.android/org.citra.citra_emu.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "citra": ("Citra", ["org.citra.citra_emu/.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "citra_canary": ("Citra Canary", ["org.citra.citra_emu.canary/org.citra.citra_emu.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "citra_mmj": ("Citra MMJ", ["org.citra.emu/.ui.EmulationActivity"], None, None, None, {"GamePath": "{path}"}, []),
    "mandarine": ("Mandarine", ["io.github.mandarine3ds.mandarine/.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "lime3ds": ("Lime3DS", ["io.github.lime3ds.android/.activities.EmulationActivity"], None, None, "{saf}", {}, CLEAR),
    "panda3ds": ("Panda3DS", ["com.panda3ds.pandroid/.app.MainActivity"], None, None, "{provider}", {}, []),
    "dolphin": ("Dolphin", ["org.dolphinemu.dolphinemu/.ui.main.TvMainActivity"], MAIN,
                "android.intent.category.LEANBACK_LAUNCHER", None, {"AutoStartFile": "{saf}"}, []),
    "dolphin_mmjr2": ("Dolphin MMJR2", ["org.dolphinemu.mmjr/org.dolphinemu.dolphinemu.ui.main.MainActivity"], VIEW, None, None, {"AutoStartFile": "{saf}"}, []),
    "nethersx2": ("NetherSX2", ["xyz.aethersx2.android/.EmulationActivity"], MAIN, None, None, {"bootPath": "{saf}"}, CLEAR),
    "armsx2": ("ARMSX2", ["come.nanodata.armsx2/com.armsx2.MainActivity", "com.armsx2/.MainActivity",
                          "com.armsx2.nightly/com.armsx2.MainActivity",
                          "come.nanodata.armsx2/kr.co.iefriends.pcsx2.MainActivity"], VIEW, None, "{saf}", {}, []),
    "duckstation": ("DuckStation", ["com.github.stenzek.duckstation/.EmulationActivity"], None, None, None,
                    {"bool:resumeState": "false", "bootPath": "{saf}"}, CLEAR),
    "drastic": ("DraStic", ["com.dsemu.drastic/.DraSticActivity"], None, None, "{saf}", {}, CLEAR),
    "melonds": ("melonDS", ["me.magnum.melonds/.ui.emulator.EmulatorActivity",
                            "me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity"],
                "me.magnum.melonds.LAUNCH_ROM", None, None, {"uri": "{saf}"}, []),
    "ppsspp": ("PPSSPP", ["org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity", "org.ppsspp.ppsspp/.PpssppActivity"],
               VIEW, "android.intent.category.DEFAULT", "{saf}", {}, []),
    "flycast": ("Flycast", ["com.flycast.emulator/com.flycast.emulator.MainActivity",
                            "com.flycast.emulator/com.reicast.emulator.MainActivity"], VIEW, None, "{saf}", {}, []),
    "redream": ("Redream", ["io.recompiled.redream/.MainActivity"], VIEW, None, "{saf}", {}, []),
    "m64plus_fz": ("M64Plus FZ", ["org.mupen64plusae.v3.fzurita.pro/paulscode.android.mupen64plusae.SplashActivity",
                                  "org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae.SplashActivity",
                                  "org.mupen64plusae.v3.fzurita.amazon/paulscode.android.mupen64plusae.SplashActivity"],
                   VIEW, None, "{saf}", {}, []),
    "mupen64plus_ae": ("Mupen64Plus AE", ["org.mupen64plusae.v3.alpha/paulscode.android.mupen64plusae.SplashActivity"], VIEW, None, "{saf}", {}, []),
    "yabasanshiro2": ("Yaba Sanshiro 2", ["org.devmiyax.yabasanshioro2.pro/org.uoyabause.android.Yabause",
                                          "org.devmiyax.yabasanshioro2/org.uoyabause.android.Yabause"],
                      VIEW, None, None, {"org.uoyabause.android.FileNameUri": "{saf}"}, CLEAR),
    "saturn_emu": ("Saturn.emu", ["com.explusalpha.SaturnEmu/com.imagine.BaseActivity"], None, None, "{saf}", {}, []),
    "eden": ("Eden", ["dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
                      "dev.legacy.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
                      "dev.eden.eden_emulator.nightly/org.yuzu.yuzu_emu.activities.EmulationActivity"],
             "android.nfc.action.TECH_DISCOVERED", None, "{provider}", {}, []),
    "cemu": ("Cemu", ["info.cemu.cemu/info.cemu.cemu.emulation.EmulationActivity",
                      "info.cemu.Cemu/info.cemu.Cemu.emulation.EmulationActivity"], None, None, "{saf}", {}, []),
    "pizzaboy_gba": ("Pizza Boy GBA", ["it.dbtecno.pizzaboygbapro/it.dbtecno.pizzaboygbapro.MainActivity",
                                       "it.dbtecno.pizzaboygba/it.dbtecno.pizzaboygba.MainActivity"],
                     None, None, None, {"rom_uri": "{saf}"}, CLEAR),
    "my_boy": ("My Boy!", ["com.fastemulator.gba/.EmulatorActivity"], VIEW, None, "{saf}", {}, []),
    "retroarch": ("RetroArch", ["com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
                                "com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture",
                                "com.retroarch.ra32/com.retroarch.browser.retroactivity.RetroActivityFuture"],
                  None, None, None, {"CONFIGFILE": "{external}/Android/data/{package}/files/retroarch.cfg",
                                     "LIBRETRO": "/data/data/{package}/cores/{core}_libretro_android.so",
                                     "ROM": "{path}"}, []),
}

# Performance needs, by the chip class that runs a system well.
LOW, MID, HIGH, TOP = ("Runs on any handheld", "Wants a mid-range chip (Unisoc T618 / Dimensity 900 or better)",
                       "Wants a strong chip (Snapdragon 865 / Dimensity 1100 or better)",
                       "Wants a flagship chip (Snapdragon 8 Gen 2 class, e.g. AYN Thor / Odin 2)")

# id, name, short, color, folders, extensions, emulators (preferred first), demand, note
SYS = [
    ("switch", "Nintendo Switch", "NSW", "#E60012", ["switch"], ".nca .nro .nso .nsp .xci", ["eden"], TOP,
     "Light titles only below Snapdragon 8 Gen 2. Needs keys/firmware set up inside Eden."),
    ("3ds", "Nintendo 3DS", "3DS", "#D12228", ["3ds", "n3ds"], ".3ds .3dsx .app .axf .cci .cxi .elf .z3dsx .zcci .zcxi",
     ["azahar", "azaharplus", "citra_mmj", "lime3ds", "mandarine", "citra", "citra_canary", "panda3ds", "ra:azahar", "ra:citra"], HIGH,
     "Dual-screen devices can show both 3DS screens natively (set the layout in Azahar)."),
    ("wiiu", "Nintendo Wii U", "WIIU", "#009AC7", ["wiiu"], ".elf .rpx .tmd .wua .wud .wuhb .wux", ["cemu"], TOP, "Cemu for Android is early; expect issues."),
    ("wii", "Nintendo Wii", "WII", "#8FA3AD", ["wii"], ".ciso .dol .elf .gcz .iso .rvz .wad .wbfs .wia", ["dolphin", "dolphin_mmjr2", "ra:dolphin"], HIGH, ""),
    ("gc", "Nintendo GameCube", "GC", "#6A5ACD", ["gc", "gamecube", "ngc"], ".ciso .dol .elf .gcm .gcz .iso .rvz .tgc .wia",
     ["dolphin", "dolphin_mmjr2", "ra:dolphin"], HIGH, "On RK3568 / T618-class chips only a handful of light games approach full speed."),
    ("ps2", "PlayStation 2", "PS2", "#2E5BD8", ["ps2"], ".bin .chd .ciso .cso .elf .gz .img .iso .isz .m3u .mdf .nrg",
     ["nethersx2", "armsx2", "ra:pcsx2"], HIGH, "Adreno GPUs (Snapdragon) do far better than Mali. RK3568 devices: very light games only."),
    ("psvita", "PlayStation Vita", "VITA", "#003791", ["psvita", "vita"], ".psvita .vpk", [], TOP, "Use Vita3K's own launcher; pick it with 'Any app'."),
    ("nds", "Nintendo DS", "NDS", "#9AA3AD", ["nds"], ".nds .dsi .ids .app", ["drastic", "melonds", "ra:melondsds", "ra:melonds", "ra:desmume"], LOW,
     "On dual-screen devices DraStic/melonDS can put each DS screen on its own display."),
    ("psp", "PlayStation Portable", "PSP", "#3A3A3A", ["psp"], ".chd .cso .elf .iso .pbp .prx", ["ppsspp", "ra:ppsspp"], MID, ""),
    ("dreamcast", "Sega Dreamcast", "DC", "#F28C28", ["dreamcast", "dc"], ".cdi .chd .cue .gdi .m3u", ["flycast", "redream", "ra:flycast"], MID, ""),
    ("saturn", "Sega Saturn", "SAT", "#455A64", ["saturn"], ".bin .ccd .chd .cue .iso .m3u .mds .toc",
     ["yabasanshiro2", "saturn_emu", "ra:yabasanshiro", "ra:mednafen_saturn"], MID, ""),
    ("n64", "Nintendo 64", "N64", "#2DA44E", ["n64"], ".n64 .v64 .z64 .ndd", ["m64plus_fz", "mupen64plus_ae", "ra:mupen64plus_next_gles3", "ra:parallel_n64"], MID, ""),
    ("psx", "PlayStation", "PS1", "#8C8C8C", ["psx", "ps1"], ".bin .cbn .ccd .chd .cue .ecm .exe .img .iso .m3u .mdf .mds .pbp .toc",
     ["duckstation", "ra:swanstation", "ra:pcsx_rearmed", "ra:mednafen_psx_hw"], LOW, ""),
    ("gba", "Game Boy Advance", "GBA", "#5B3FA8", ["gba"], ".agb .gba", ["ra:mgba", "pizzaboy_gba", "my_boy", "ra:gpsp", "ra:vbam"], LOW, ""),
    ("gbc", "Game Boy Color", "GBC", "#C2185B", ["gbc"], ".cgb .gbc", ["ra:gambatte", "ra:sameboy", "ra:mgba"], LOW, ""),
    ("gb", "Game Boy", "GB", "#8BAC0F", ["gb"], ".dmg .gb", ["ra:gambatte", "ra:sameboy", "ra:mgba"], LOW, ""),
    ("snes", "Super Nintendo", "SNES", "#6C5CE7", ["snes", "sfc"], ".bs .fig .sfc .smc .swc", ["ra:snes9x", "ra:bsnes", "ra:snes9x2010"], LOW, ""),
    ("nes", "Nintendo Entertainment System", "NES", "#E53935", ["nes", "fc", "famicom"], ".fds .nes .unf .unif", ["ra:mesen", "ra:nestopia", "ra:fceumm"], LOW, ""),
    ("genesis", "Sega Genesis / Mega Drive", "MD", "#1E1E1E", ["genesis", "megadrive", "md"], ".32x .bin .gen .md .smd",
     ["ra:genesis_plus_gx", "ra:picodrive"], LOW, ""),
    ("segacd", "Sega CD", "SCD", "#37474F", ["segacd", "megacd"], ".chd .cue .iso .m3u", ["ra:genesis_plus_gx", "ra:picodrive"], LOW, ""),
    ("mastersystem", "Sega Master System", "SMS", "#B71C1C", ["mastersystem", "sms"], ".sms .bin", ["ra:genesis_plus_gx", "ra:picodrive"], LOW, ""),
    ("gamegear", "Sega Game Gear", "GG", "#263238", ["gamegear", "gg"], ".gg .bin", ["ra:genesis_plus_gx", "ra:gearsystem"], LOW, ""),
    ("pce", "PC Engine / TurboGrafx", "PCE", "#F57C00", ["pcengine", "tg16", "pce"], ".ccd .chd .cue .pce .sgx", ["ra:mednafen_pce_fast", "ra:mednafen_pce"], LOW, ""),
    ("arcade", "Arcade", "ARC", "#FFB300", ["arcade", "fbneo", "mame", "neogeo"], ".zip .7z", ["ra:fbneo", "ra:mame2003_plus", "ra:mamearcade"], LOW, ""),
    ("atari2600", "Atari 2600", "2600", "#795548", ["atari2600"], ".a26 .bin", ["ra:stella"], LOW, ""),
    ("ngpc", "Neo Geo Pocket Color", "NGP", "#C62828", ["ngpc", "ngp"], ".ngc .ngp .ngpc", ["ra:mednafen_ngp", "ra:race"], LOW, ""),
    ("dos", "DOS", "DOS", "#455A64", ["dos", "pc"], ".bat .com .dosz .exe", ["ra:dosbox_pure"], LOW, ""),
]

# Archives are valid ROMs for cartridge systems that RetroArch cores open directly.
ARCHIVE_OK = {"gba", "gbc", "gb", "snes", "nes", "genesis", "mastersystem", "gamegear", "pce", "atari2600", "ngpc", "n64", "nds", "dos"}


def main():
    emus = {k: {"name": n, "components": c, "action": a, "category": cat, "data": d, "extras": e, "flags": f}
            for k, (n, c, a, cat, d, e, f) in EMU.items()}
    systems = []
    for sid, name, short, color, folders, exts, emulators, demand, note in SYS:
        ext = exts.split() + ([".zip", ".7z"] if sid in ARCHIVE_OK else [])
        systems.append({"id": sid, "name": name, "short": short, "color": color, "folders": folders,
                        "extensions": sorted(set(ext)), "emulators": emulators, "demand": demand, "note": note})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "emulators.json").write_text(json.dumps({"emulators": emus}, indent=1))
    (OUT / "systems.json").write_text(json.dumps({"systems": systems}, indent=1))
    print(f"{len(emus)} emulators, {len(systems)} systems -> {OUT}")


if __name__ == "__main__":
    main()
