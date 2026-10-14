import random

import pygame
import pytest

import preview


@pytest.fixture(params=["single", "dual"])
def app(request, ryzer_home):
    a = preview.build_app(dual=request.param == "dual")
    yield a
    pygame.quit()


def frame(app, n=2):
    for _ in range(n):
        if app.current:
            app.current.update(1 / 30)
        app.render(1 / 30, 1000.0)


def test_screenshots_render(app, tmp_path):
    from ryzer.screens.home import HomeScreen
    app.push(HomeScreen(app))
    for action in ["right", "right", "a", "down", "x", "start", "b", "b", "y"]:
        app.current.handle(action)
        frame(app)
    assert app.state.data["favorites"]


def test_theme_editor_saves_custom_theme(app):
    from ryzer.screens.home import HomeScreen
    app.push(HomeScreen(app))
    app.open_themes()
    picker = app.current
    picker.handle("x")                  # edit a copy
    editor = app.current
    editor.sel = next(n for n, i in enumerate(editor.items) if getattr(i, "color_key", "") == "accent")
    editor.handle("right")
    editor.sel = len(editor.items) - 1  # "Save theme"
    editor.handle("a")
    assert app.current is picker
    assert app.cfg["theme"].startswith("custom-")
    assert app.cfg["theme"] in app.themes
    frame(app)
    assert picker.list[picker.sel].id == app.cfg["theme"]


def test_every_theme_renders_every_main_screen(app):
    from ryzer.screens.home import HomeScreen
    app.push(HomeScreen(app))
    for tid in list(app.themes):
        app.set_theme(tid, save=False)
        app.home()
        frame(app, 1)
        app.open_settings()
        frame(app, 1)


def test_random_input_fuzz(app):
    """Hammer the UI with random buttons; nothing may raise."""
    from ryzer.screens.home import HomeScreen
    app.push(HomeScreen(app))
    rnd = random.Random(1234)
    actions = ["up", "down", "left", "right", "a", "b", "x", "y", "l", "r", "start", "select"]
    for i in range(2500):
        a = rnd.choice(actions)
        if a == "a" and getattr(app.current, "title", "") == "Power":
            a = "b"  # don't trigger exit/reboot
        app.current.handle(a)
        if not app.running:
            app.running = True
        if i % 5 == 0:
            frame(app, 1)
    assert app.stack
