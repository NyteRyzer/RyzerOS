package com.ryzer.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {
    @Test fun exactAndPrefixRankFirst() {
        val a = FuzzyMatch.score("mario", "Super Mario World")!!
        val b = FuzzyMatch.score("mario", "Dr. Mario")!!
        val c = FuzzyMatch.score("mario", "Mario Kart 64")!!
        assertTrue(c >= a && c >= b)
    }

    @Test fun typosStillMatch() {
        assertNotNull(FuzzyMatch.score("zelda minsh", "The Legend of Zelda - The Minish Cap"))
        assertNotNull(FuzzyMatch.score("pokmon emerld", "Pokemon Emerald"))
    }

    @Test fun unrelatedDoesNotMatch() {
        assertNull(FuzzyMatch.score("zelda", "Sonic the Hedgehog 2"))
    }

    @Test fun ignoresCaseAndPunctuation() {
        assertNotNull(FuzzyMatch.score("castlevania aria", "Castlevania: Aria of Sorrow"))
    }
}

class RomInfoTest {
    @Test fun readsRegions() {
        assertEquals("USA", RomInfo.region("Super Metroid (USA).sfc"))
        assertEquals("EUR", RomInfo.region("Golden Sun (Europe) (En,Fr,De).gba"))
        assertEquals("JPN", RomInfo.region("Mother 3 (Japan).gba"))
        assertEquals("WLD", RomInfo.region("Tetris (World).gb"))
        assertEquals("USA", RomInfo.region("Sonic 2 (USA, Europe).md"))
        assertNull(RomInfo.region("sonic 2.md"))
    }
}

class ChallengesTest {
    @Test fun sameWeekSameChallenge() {
        assertEquals(Challenges.forWeek(2900).id, Challenges.forWeek(2900).id)
    }

    @Test fun progressCountsDistinctSystemsAndGames() {
        val sessions = listOf(Challenges.Played("gba:a", "gba", 600), Challenges.Played("snes:b", "snes", 900),
            Challenges.Played("gba:c", "gba", 300))
        assertEquals(2, Challenges.progress(Challenges.byId("systems3"), sessions))
        assertEquals(3, Challenges.progress(Challenges.byId("games3"), sessions))
        assertEquals(30, Challenges.progress(Challenges.byId("minutes120"), sessions))
    }

    @Test fun everyChallengeHasATarget() {
        for (c in Challenges.ALL) assertTrue(c.target > 0)
    }
}

class WeatherCodesTest {
    @Test fun describesWmoCodes() {
        assertEquals("Clear", WeatherCodes.describe(0))
        assertEquals("Rain", WeatherCodes.describe(63))
        assertEquals("Snow", WeatherCodes.describe(73))
        assertEquals("Storm", WeatherCodes.describe(95))
        assertEquals("Cloudy", WeatherCodes.describe(3))
        assertEquals("—", WeatherCodes.describe(999))
    }
}

class ConsoleFactsTest {
    @Test fun everySystemHasFacts() {
        for (id in listOf("switch", "3ds", "wiiu", "wii", "gc", "ps2", "psvita", "nds", "psp", "dreamcast", "saturn", "n64",
                "psx", "gba", "gbc", "gb", "snes", "nes", "genesis", "segacd", "mastersystem", "gamegear", "pce", "arcade",
                "atari2600", "ngpc", "dos"))
            assertTrue("$id has no facts", ConsoleFacts.forSystem(id).isNotEmpty())
    }

    @Test fun pickIsStableWithinADay() {
        assertEquals(ConsoleFacts.pick("gba", 20000), ConsoleFacts.pick("gba", 20000))
    }
}

class ListOpsTest {
    @Test fun moveSystemLeftAndRight() {
        assertEquals(listOf("b", "a", "c"), ListOps.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "c", "b"), ListOps.move(listOf("a", "b", "c"), "b", 1))
        assertEquals(listOf("a", "b", "c"), ListOps.move(listOf("a", "b", "c"), "a", -1))
        assertEquals(listOf("a", "b", "c"), ListOps.move(listOf("a", "b", "c"), "x", 1))
    }

    @Test fun orderedBySavedOrder() {
        assertEquals(listOf("c", "a", "b", "d"), ListOps.ordered(listOf("a", "b", "c", "d"), listOf("c", "a", "zz")))
    }

    @Test fun forgottenFavorites() {
        val day = 86_400_000L
        val now = 100 * day
        val last = mapOf("a" to now - 5 * day, "b" to now - 70 * day)
        assertEquals(listOf("b", "c"), ListOps.forgotten(listOf("a", "b", "c"), last, now, 60))
    }

    @Test fun completionPercent() {
        assertEquals(50, ListOps.completion(listOf("beaten", "", "completed", "playing")))
        assertEquals(0, ListOps.completion(emptyList()))
    }
}
