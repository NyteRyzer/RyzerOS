import os
import sys
from pathlib import Path

import pytest

os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
os.environ.setdefault("SDL_AUDIODRIVER", "dummy")
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "tools"))


@pytest.fixture
def ryzer_home(tmp_path, monkeypatch):
    monkeypatch.setenv("RYZER_HOME", str(tmp_path / "home"))
    monkeypatch.setenv("RYZER_ROMS", str(tmp_path / "roms"))
    return tmp_path
