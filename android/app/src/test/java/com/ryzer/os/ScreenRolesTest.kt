package com.ryzer.os

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenRolesTest {
    // AYN Thor: big 1920x1080 main (top) screen, small 1240x1080 second (bottom) screen
    @Test fun thorPutsMenusOnTheSmallBottomScreen() = assertEquals(true, ScreenRoles.menusOnSecond(1920, 1080, 1240, 1080, chosen = null))

    // AYANEO Pocket DS: 1920x1080 top, 960x640 bottom
    @Test fun pocketDsToo() = assertEquals(true, ScreenRoles.menusOnSecond(1920, 1080, 960, 640, chosen = null))

    // Two same-size screens (RG DS): keep menus on the main screen
    @Test fun equalScreensKeepMenusOnMain() = assertEquals(false, ScreenRoles.menusOnSecond(640, 480, 640, 480, chosen = null))

    // A big external monitor as the second display: menus stay on the handheld
    @Test fun biggerSecondKeepsMenusOnMain() = assertEquals(false, ScreenRoles.menusOnSecond(1920, 1080, 3840, 2160, chosen = null))

    @Test fun yourChoiceWins() {
        assertEquals(false, ScreenRoles.menusOnSecond(1920, 1080, 1240, 1080, chosen = false))
        assertEquals(true, ScreenRoles.menusOnSecond(640, 480, 640, 480, chosen = true))
    }
}
