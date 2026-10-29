package com.ryzer.os

import org.junit.Assert.assertEquals
import org.junit.Test

class BladeTabsTest {
    @Test fun tabOfKinds() {
        assertEquals(0, BladeTabs.tabOf("continue"))
        assertEquals(0, BladeTabs.tabOf("pinned"))
        assertEquals(1, BladeTabs.tabOf("system"))
        assertEquals(2, BladeTabs.tabOf("smart"))
        assertEquals(3, BladeTabs.tabOf("settings"))
        assertEquals(3, BladeTabs.tabOf("tools"))
    }

    @Test fun groupKeepsOrder() {
        assertEquals(listOf(listOf(0), listOf(1, 3), listOf(2), listOf(4)),
            BladeTabs.group(listOf("continue", "system", "favorites", "system", "settings")))
    }

    @Test fun emptyGamesKeepsToolsTab() {
        assertEquals(3, BladeTabs.firstNonEmpty(BladeTabs.group(listOf("search", "stats", "settings"))))
    }

    @Test fun cycleSkipsEmptyTabs() {
        val g = listOf(listOf(0), emptyList(), emptyList(), listOf(1))
        assertEquals(3, BladeTabs.next(g, 0, 1))
        assertEquals(0, BladeTabs.next(g, 3, 1))
    }

    @Test fun allEmptyStays() {
        assertEquals(2, BladeTabs.next(listOf(emptyList(), emptyList(), emptyList(), emptyList()), 2, 1))
    }
}
