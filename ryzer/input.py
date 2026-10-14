"""Turns keyboard, gamepad and joystick events into Ryzer actions.

Actions: up down left right a b x y l r start select.
SDL's game-controller API is used when the pad has a mapping (built-in pads on
ROCKNIX, Knulli, muOS and ArkOS do); raw joystick indices from config are the fallback.
"""

import pygame

try:
    from pygame._sdl2 import controller as sdl_controller
except ImportError:  # very old pygame
    sdl_controller = None

DIRECTIONS = ("up", "down", "left", "right")
REPEAT_DELAY = 0.35
REPEAT_RATE = 0.075

_CONTROLLER_BUTTONS = {
    "a": "CONTROLLER_BUTTON_A", "b": "CONTROLLER_BUTTON_B",
    "x": "CONTROLLER_BUTTON_X", "y": "CONTROLLER_BUTTON_Y",
    "l": "CONTROLLER_BUTTON_LEFTSHOULDER", "r": "CONTROLLER_BUTTON_RIGHTSHOULDER",
    "start": "CONTROLLER_BUTTON_START", "select": "CONTROLLER_BUTTON_BACK",
    "up": "CONTROLLER_BUTTON_DPAD_UP", "down": "CONTROLLER_BUTTON_DPAD_DOWN",
    "left": "CONTROLLER_BUTTON_DPAD_LEFT", "right": "CONTROLLER_BUTTON_DPAD_RIGHT",
}


class Input:
    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.key_map = {}
        for action, names in cfg["keys"].items():
            for n in names:
                try:
                    self.key_map[pygame.key.key_code(n)] = action
                except ValueError:
                    pass
        self.button_map = {}
        for action, attr in _CONTROLLER_BUTTONS.items():
            if hasattr(pygame, attr):
                self.button_map[getattr(pygame, attr)] = action
        self.joy_map = {v: k for k, v in cfg["joy_buttons"].items()}
        self.held = set()
        self._repeat = {}
        self._axis_dir = {}
        self.pads = []
        self.open_pads()

    def open_pads(self):
        self.pads.clear()
        pygame.joystick.init()
        for i in range(pygame.joystick.get_count()):
            if sdl_controller is not None and sdl_controller.is_controller(i):
                c = sdl_controller.Controller(i)
                self.pads.append(c)
            else:
                j = pygame.joystick.Joystick(i)
                self.pads.append(j)

    def _swap(self, action):
        if self.cfg.get("swap_ab"):
            return {"a": "b", "b": "a"}.get(action, action)
        return action

    def _press(self, action, now, out):
        if action is None:
            return
        action = self._swap(action)
        self.held.add(action)
        out.append(action)
        if action in DIRECTIONS:
            self._repeat[action] = now + REPEAT_DELAY

    def _release(self, action):
        if action is None:
            return
        action = self._swap(action)
        self.held.discard(action)
        self._repeat.pop(action, None)

    def _axis(self, axis, value, now, out, horizontal_axes=(0,), vertical_axes=(1,)):
        if axis in horizontal_axes:
            neg, pos = "left", "right"
        elif axis in vertical_axes:
            neg, pos = "up", "down"
        else:
            return
        new = neg if value < -0.6 else pos if value > 0.6 else None
        old = self._axis_dir.get(axis)
        if new != old:
            if old:
                self._release(old)
            if new:
                self._press(new, now, out)
            self._axis_dir[axis] = new

    def process(self, events, now):
        """Returns the list of actions triggered this frame (with auto-repeat)."""
        out = []
        for e in events:
            if e.type == pygame.KEYDOWN:
                self._press(self.key_map.get(e.key), now, out)
            elif e.type == pygame.KEYUP:
                self._release(self.key_map.get(e.key))
            elif e.type == getattr(pygame, "CONTROLLERBUTTONDOWN", -1):
                self._press(self.button_map.get(e.button), now, out)
            elif e.type == getattr(pygame, "CONTROLLERBUTTONUP", -1):
                self._release(self.button_map.get(e.button))
            elif e.type == getattr(pygame, "CONTROLLERAXISMOTION", -1):
                v = e.value / 32767
                self._axis(e.axis, v, now, out,
                           (getattr(pygame, "CONTROLLER_AXIS_LEFTX", 0),),
                           (getattr(pygame, "CONTROLLER_AXIS_LEFTY", 1),))
            elif e.type == pygame.JOYBUTTONDOWN and not self._is_controller(e):
                self._press(self.joy_map.get(e.button), now, out)
            elif e.type == pygame.JOYBUTTONUP and not self._is_controller(e):
                self._release(self.joy_map.get(e.button))
            elif e.type == pygame.JOYHATMOTION and not self._is_controller(e):
                x, y = e.value
                for d in DIRECTIONS:
                    self._release(d)
                if x:
                    self._press("left" if x < 0 else "right", now, out)
                if y:
                    self._press("up" if y > 0 else "down", now, out)
            elif e.type == pygame.JOYAXISMOTION and not self._is_controller(e):
                self._axis(e.axis, e.value, now, out)
            elif e.type in (pygame.JOYDEVICEADDED, pygame.JOYDEVICEREMOVED):
                self.open_pads()
        for action, due in list(self._repeat.items()):
            if now >= due:
                out.append(action)
                self._repeat[action] = now + REPEAT_RATE
        return out

    def _is_controller(self, e):
        # SDL sends both JOY* and CONTROLLER* events for mapped pads; use only one.
        if sdl_controller is None:
            return False
        idx = getattr(e, "instance_id", getattr(e, "joy", None))
        for p in self.pads:
            if isinstance(p, sdl_controller.Controller):
                try:
                    if p.as_joystick().get_instance_id() == idx:
                        return True
                except (AttributeError, pygame.error):
                    pass
        return False

    def combo(self, *actions) -> bool:
        return all(a in self.held for a in actions)
