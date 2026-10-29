from pathlib import Path

import pytest

from ryzer import config, launcher, library, paths, themes, tweaks


def test_builtin_themes_are_valid():
    builtin = themes._load_dir(paths.BUILTIN_THEMES_DIR, builtin=True)
    files = list(paths.BUILTIN_THEMES_DIR.glob("*.json"))
    assert len(files) == 21
    assert len(builtin) == 21  # every file parsed
    assert "ryzer-red" in builtin
    assert "ryzer-strike" in builtin


def test_user_theme_roundtrip_and_override(ryzer_home):
    all_t = themes.load_all()
    new_id, new_name = themes.next_custom_id(all_t)
    t = all_t["ryzer-red"].copy(new_id, new_name)
    t.colors["accent"] = (1, 2, 3)
    themes.save_user_theme(t)
    again = themes.load_all()
    assert again[new_id].colors["accent"] == (1, 2, 3)
    assert not again[new_id].builtin
    themes.delete_user_theme(again[new_id])
    assert new_id not in themes.load_all()


def test_broken_user_theme_is_ignored(ryzer_home):
    paths.ensure_dirs()
    (paths.user_themes_dir() / "bad.json").write_text("{not json")
    (paths.user_themes_dir() / "bad2.json").write_text('{"id": "x", "colors": {"accent": "red"}}')
    assert "x" not in themes.load_all()


@pytest.mark.parametrize("bad", ["red", "#12345", "#GGGGGG"])
def test_parse_color_rejects(bad):
    with pytest.raises(ValueError):
        themes.parse_color(bad)


def test_clean_name():
    assert library.clean_name("Super Metroid (USA) [!]") == "Super Metroid"
    assert library.clean_name("Zelda_Minish_Cap (E)") == "Zelda Minish Cap"
    assert library.clean_name("(Beta)") == "(Beta)"


def test_scan_finds_games_in_folders_and_subfolders(ryzer_home):
    roms = Path(ryzer_home) / "roms"
    (roms / "gba" / "rpg").mkdir(parents=True)
    (roms / "gba" / "Metroid Fusion (USA).gba").touch()
    (roms / "gba" / "rpg" / "Golden Sun.gba").touch()
    (roms / "gba" / "readme.txt").touch()
    (roms / "gba" / "images").mkdir()
    (roms / "gba" / "images" / "Metroid Fusion (USA).png").touch()
    systems = library.load_systems()
    library.scan(systems, roms)
    gba = next(s for s in systems if s.id == "gba")
    assert [g.name for g in gba.games] == ["Golden Sun", "Metroid Fusion"]
    assert gba.games[1].image().name == "Metroid Fusion (USA).png"
    assert library.search(systems, "metroid")[0].system is gba


def test_systems_include_heavy_consoles():
    ids = {s.id for s in library.load_systems()}
    assert {"3ds", "gc", "ps2", "nds"} <= ids


def test_state_tracks_play(ryzer_home):
    st = config.State()
    st.record_play("/a", 100)
    st.record_play("/b", 50)
    st.record_play("/a", 20)
    assert st.data["recent"] == ["/a", "/b"]
    assert st.playtime("/a") == 120
    assert config.State().total_playtime() == 170  # persisted
    assert st.toggle_favorite("/a") and not st.toggle_favorite("/a")


def test_config_merges_defaults(ryzer_home):
    from ryzer.storage import save_json
    paths.ensure_dirs()
    save_json(paths.config_file(), {"theme": "matrix", "input": {"swap_ab": True}})
    cfg = config.Config()
    assert cfg["theme"] == "matrix"
    assert cfg["input"]["swap_ab"] is True
    assert "keys" in cfg["input"]  # defaults kept under a partial override
    assert cfg.profile_for("3ds") == "turbo"
    assert cfg.profile_for("gba") == "balanced"


def test_launcher_resolve(tmp_path, monkeypatch):
    prog = tmp_path / "fake-emu"
    prog.write_text("#!/bin/sh\nexit 0\n")
    prog.chmod(0o755)
    monkeypatch.setenv("PATH", str(tmp_path))
    rom = Path("/roms/My Game (USA).3ds")
    assert launcher.resolve(["missing {rom}", "ra:nope", "fake-emu --fullscreen {rom}"], rom) == \
        ["fake-emu", "--fullscreen", str(rom)]
    assert launcher.resolve(["missing {rom}"], rom) is None


def test_ini_tweaks_preserve_and_set():
    src = "[Renderer]\nresolution_factor=3\nresolution_factor\\default=true\nother=1\n[Misc]\nx=y\n"
    out = tweaks.set_ini_values(src, {"Renderer": {"resolution_factor": "1", "use_hw_shader": "true"},
                                      "Core": {"use_cpu_jit": "true"}})
    assert "resolution_factor=1" in out
    assert "resolution_factor\\default=false" in out
    assert "use_hw_shader=true" in out and "other=1" in out and "x=y" in out
    assert "[Core]\nuse_cpu_jit=true" in out


def test_tweaks_apply_and_restore(tmp_path, monkeypatch):
    cfgdir = tmp_path / "azahar-emu"
    cfgdir.mkdir()
    ini = cfgdir / "sdl2-config.ini"
    ini.write_text("[Renderer]\nresolution_factor=4\n")
    monkeypatch.setattr(tweaks, "HOMES", [tmp_path])
    report = tweaks.apply_all()
    assert any("tuned" in r for r in report)
    assert "resolution_factor=1" in ini.read_text()
    assert tweaks.restore_all() == 1
    assert "resolution_factor=4" in ini.read_text()


def test_scan_multiple_roots_case_insensitive(tmp_path):
    a, b = tmp_path / "sd1" / "ROMS", tmp_path / "sd2" / "roms"
    (a / "3DS").mkdir(parents=True)
    (b / "n3ds").mkdir(parents=True)
    (a / "3DS" / "Pilotwings.3ds").touch()
    (b / "n3ds" / "Kirby.3ds").touch()
    systems = library.load_systems()
    library.scan(systems, [a, b, tmp_path / "missing"])
    n3ds = next(s for s in systems if s.id == "3ds")
    assert [g.name for g in n3ds.games] == ["Kirby", "Pilotwings"]


def test_firmware_override(monkeypatch):
    from ryzer import device
    monkeypatch.setenv("RYZER_FIRMWARE", "knulli")
    assert device.firmware() == "knulli"
    assert isinstance(device.model(), str) and device.model()
