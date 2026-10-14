"""UI sound effects, synthesised at startup so no audio files are needed."""

import array
import math

import pygame

RATE = 22050


def _tone(freqs, ms, volume=0.25, shape="sine"):
    n = int(RATE * ms / 1000)
    buf = array.array("h")
    for i in range(n):
        t = i / RATE
        f = freqs[min(len(freqs) - 1, i * len(freqs) // n)]
        if shape == "square":
            v = 1.0 if math.sin(2 * math.pi * f * t) >= 0 else -1.0
        else:
            v = math.sin(2 * math.pi * f * t)
        env = min(1.0, i / 200) * (1 - i / n) ** 2
        buf.append(int(v * env * volume * 32767))
    return buf


class Sounds:
    def __init__(self, enabled=True, volume=70):
        self.enabled = enabled
        self.ok = False
        self.sounds = {}
        try:
            pygame.mixer.init(RATE, -16, 1, 512)
            specs = {
                "move": ([880], 25, 0.12, "sine"),
                "select": ([660, 990], 70, 0.2, "sine"),
                "back": ([600, 400], 70, 0.18, "sine"),
                "error": ([220, 180], 140, 0.2, "square"),
                "boot": ([220, 330, 440, 660, 880], 700, 0.22, "sine"),
                "fav": ([990, 1320], 90, 0.18, "sine"),
            }
            for name, (freqs, ms, vol, shape) in specs.items():
                self.sounds[name] = pygame.mixer.Sound(buffer=_tone(freqs, ms, vol, shape).tobytes())
            self.ok = True
            self.set_volume(volume)
        except pygame.error:
            pass  # no audio device: stay silent

    def set_volume(self, volume):
        for s in self.sounds.values():
            s.set_volume(max(0, min(100, volume)) / 100)

    def play(self, name):
        if self.enabled and self.ok and name in self.sounds:
            self.sounds[name].play()
