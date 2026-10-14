"""Screen base class. The app keeps a stack of screens; the top one gets input."""


class Screen:
    title = ""

    def __init__(self, app):
        self.app = app

    @property
    def theme(self):
        return self.app.theme

    def handle(self, action):
        """React to an input action. Return nothing."""

    def update(self, dt):
        pass

    def draw(self, surf):
        """Draw the main (bottom / only) screen."""

    def draw_top(self, surf):
        """Draw the secondary top screen in dual-screen mode."""
        self.app.draw_default_top(surf)

    def hints(self):
        return [("B", "Back"), ("A", "Select")]

    def on_resume(self):
        """Called when a screen above this one is closed."""
