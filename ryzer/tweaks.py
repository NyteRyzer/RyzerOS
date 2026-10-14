"""Speed-first settings for the heavy emulators (3DS, GameCube, PS2).

Each tweak edits an existing emulator ini file in place, after making a one-time
`.ryzer-backup` copy. Files that don't exist are skipped: Ryzer never creates
an emulator config from scratch, so an emulator's own first-run setup still works.
"""

import shutil
from pathlib import Path

HOMES = [Path("/storage/.config"), Path.home() / ".config", Path.home() / ".local/share"]

# (relative config paths, {section: {key: value}})
TWEAKS = {
    "3DS (Azahar/Citra)": (
        ["azahar-emu/sdl2-config.ini", "azahar-emu/qt-config.ini",
         "lime3ds-emu/sdl2-config.ini", "lime3ds-emu/qt-config.ini",
         "citra-emu/sdl2-config.ini", "citra-emu/qt-config.ini"],
        {
            "Core": {"use_cpu_jit": "true", "cpu_clock_percentage": "100"},
            "Renderer": {
                "resolution_factor": "1",       # native 400x240 - the A55 can't afford more
                "use_hw_shader": "true",
                "use_shader_jit": "true",
                "shaders_accurate_mul": "false",
                "use_disk_shader_cache": "true",
                "async_shader_compilation": "true",
                "use_vsync_new": "false",
                "texture_filter": "0",
            },
            "Audio": {"enable_audio_stretching": "true"},
        },
    ),
    "GameCube (Dolphin)": (
        ["dolphin-emu/Config/Dolphin.ini"],
        {
            "Core": {"CPUThread": "True", "Fastmem": "True", "SyncGPU": "False",
                     "DSPHLE": "True", "CPUCore": "4", "SkipIdle": "True"},
            "DSP": {"EnableJIT": "True"},
        },
    ),
    "GameCube video (Dolphin)": (
        ["dolphin-emu/Config/GFX.ini"],
        {
            "Settings": {"InternalResolution": "1", "MSAA": "0", "SSAA": "False",
                         "ShaderCompilationMode": "2", "WaitForShadersBeforeStarting": "False",
                         "FastDepthCalc": "True"},
            "Enhancements": {"MaxAnisotropy": "0", "ForceTextureFiltering": "False"},
            "Hacks": {"EFBToTextureEnable": "True", "XFBToTextureEnable": "True",
                      "EFBScaledCopy": "False", "DeferEFBCopies": "True",
                      "SkipDuplicateXFBs": "True", "ImmediateXFBEnable": "True"},
        },
    ),
    "PS2 (AetherSX2)": (
        ["aethersx2/inis/PCSX2.ini", "PCSX2/inis/PCSX2.ini"],
        {
            "EmuCore/GS": {"upscale_multiplier": "1",
                           "accurate_blending_unit": "0", "mipmap_hw": "0",
                           "UserHacks": "true", "UserHacks_CPUSpriteRenderBW": "0"},
            "EmuCore/Speedhacks": {"EECycleRate": "-1", "vuThread": "true",
                                   "fastCDVD": "true", "MTVU": "true"},
        },
    ),
}


def set_ini_values(text: str, values: dict) -> str:
    """Set key=value pairs per section, keeping everything else untouched.
    Also flips Qt-style `key\\default=true` markers so the value sticks."""
    lines = text.splitlines()
    out, section, seen = [], None, {s: set() for s in values}

    def flush(sec):
        if sec in values:
            for k, v in values[sec].items():
                if k not in seen[sec]:
                    out.append(f"{k}={v}")
                    seen[sec].add(k)

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("[") and stripped.endswith("]"):
            flush(section)
            section = stripped[1:-1]
            out.append(line)
            continue
        if section in values and "=" in stripped and not stripped.startswith((";", "#")):
            key = stripped.split("=", 1)[0].strip()
            if key in values[section]:
                out.append(f"{key}={values[section][key]}")
                seen[section].add(key)
                continue
            if key.endswith("\\default") and key[: -len("\\default")] in values[section]:
                out.append(f"{key}=false")
                continue
        out.append(line)
    flush(section)
    for sec in values:
        if not seen[sec]:
            out.append(f"[{sec}]")
            flush(sec)
    return "\n".join(out) + "\n"


def apply_all() -> list:
    """Apply every tweak to every config found. Returns a list of result lines."""
    report = []
    for label, (rel_paths, values) in TWEAKS.items():
        hits = [h / r for h in HOMES for r in rel_paths if (h / r).is_file()]
        if not hits:
            report.append(f"{label}: not installed / not run yet")
            continue
        for f in hits:
            backup = f.with_name(f.name + ".ryzer-backup")
            if not backup.exists():
                shutil.copy2(f, backup)
            f.write_text(set_ini_values(f.read_text(errors="replace"), values))
            report.append(f"{label}: tuned")
    return report


def restore_all() -> int:
    restored = 0
    for _, (rel_paths, _) in TWEAKS.items():
        for h in HOMES:
            for r in rel_paths:
                f = h / r
                backup = f.with_name(f.name + ".ryzer-backup")
                if backup.is_file():
                    shutil.copy2(backup, f)
                    restored += 1
    return restored
