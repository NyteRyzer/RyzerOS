package com.ryzer.os

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRulesTest {
    // RetroArch, My Boy!, PPSSPP... have no flags in emulators.json; they must still start fresh,
    // or a second game just brings back the first one that's still open.
    @Test fun emulatorsWithoutFlagsStartFresh() = assertTrue(LaunchRules.startsFresh(emptyList()))

    @Test fun clearFlagsStillStartFresh() = assertTrue(LaunchRules.startsFresh(listOf("clear_task", "clear_top")))

    @Test fun keepTaskOptsOut() = assertFalse(LaunchRules.startsFresh(listOf("keep_task")))
}
