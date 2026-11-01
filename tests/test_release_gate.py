"""The release gate: a version ships only if the emulator run tested this exact app code and passed."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_gate", Path(__file__).resolve().parent.parent / "tools/release_gate.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

GOOD_STABILITY = """[home] events finished=True running=True problems=0
[classic] events finished=True running=True problems=0
handled launch/update errors: 1
crash.txt: empty
"""
TREES = "android=abc ryzer=def"


def test_passes_when_everything_is_fine():
    assert gate.problems("PERF blades=2.4 classic=3.9", GOOD_STABILITY, TREES, TREES) == []


def test_slow_blades_fails():
    assert any("slower" in p for p in gate.problems("PERF blades=5.0 classic=3.0", GOOD_STABILITY, TREES, TREES))


def test_skipped_perf_fails():
    assert gate.problems("PERF skipped: a screen is open over Home", GOOD_STABILITY, TREES, TREES)


def test_crash_fails():
    bad = GOOD_STABILITY.replace("crash.txt: empty", "crash.txt: \njava.lang.IllegalStateException")
    assert any("crash" in p for p in gate.problems("PERF blades=1 classic=1", bad, TREES, TREES))


def test_monkey_problem_fails():
    bad = GOOD_STABILITY.replace("[classic] events finished=True running=True problems=0",
                                 "[classic] events finished=True running=False problems=2")
    assert len(gate.problems("PERF blades=1 classic=1", bad, TREES, TREES)) >= 2


def test_untested_code_fails():
    assert any("tested" in p for p in gate.problems("PERF blades=1 classic=1", GOOD_STABILITY, TREES, "android=xyz ryzer=def"))
