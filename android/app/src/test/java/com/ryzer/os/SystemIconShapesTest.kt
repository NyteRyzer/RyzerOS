package com.ryzer.os

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemIconShapesTest {
    private val ids = listOf("switch", "3ds", "wiiu", "wii", "gc", "ps2", "psvita", "nds", "psp", "dreamcast", "saturn", "n64",
        "psx", "gba", "gbc", "gb", "snes", "nes", "genesis", "segacd", "mastersystem", "gamegear", "pce", "arcade", "atari2600",
        "ngpc", "dos")

    @Test fun everySystemHasItsOwnIcon() {
        for (id in ids) {
            val icon = IconShapes.forSystem(id)
            assertNotSame("$id has no icon of its own", IconShapes.GENERIC, icon)
            assertTrue("$id icon is empty", icon.isNotEmpty())
        }
    }

    @Test fun unknownSystemGetsTheGamepad() {
        assertSame(IconShapes.GENERIC, IconShapes.forSystem("vectrex"))
    }

    @Test fun shapesStayInsideTheBox() {
        for (id in ids + "generic") for (p in IconShapes.forSystem(id)) {
            val (l, t, r, b) = p.bounds()
            assertTrue("$id: $p leaves the box", l >= -0.001f && t >= -0.001f && r <= 1.001f && b <= 1.001f)
        }
    }
}
