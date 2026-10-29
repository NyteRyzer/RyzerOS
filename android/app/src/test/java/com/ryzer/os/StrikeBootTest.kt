package com.ryzer.os

import org.junit.Assert.assertEquals
import org.junit.Test

class StrikeBootTest {
    @Test fun phasesClamp() {
        assertEquals(0f, StrikeBoot.bladesIn(-1f), 0.001f)
        assertEquals(0.5f, StrikeBoot.bladesIn(0.25f), 0.001f)
        assertEquals(1f, StrikeBoot.bladesIn(3f), 0.001f)
    }

    @Test fun markScaleEnds() {
        assertEquals(1.4f, StrikeBoot.markScale(0f), 0.001f)
        assertEquals(1.0f, StrikeBoot.markScale(0.9f), 0.001f)
    }

    @Test fun titleWindow() {
        assertEquals(0f, StrikeBoot.title(0.8f), 0.001f)
        assertEquals(0.5f, StrikeBoot.title(1.1f), 0.001f)
    }

    @Test fun bladesOutLast() {
        assertEquals(0f, StrikeBoot.bladesOut(1.59f), 0.001f)
        assertEquals(1f, StrikeBoot.bladesOut(2.0f), 0.001f)
    }
}
