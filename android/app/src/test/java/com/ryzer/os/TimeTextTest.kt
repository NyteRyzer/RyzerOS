package com.ryzer.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class TimeTextTest {
    private val at = 1_700_000_000_000L   // 2023-11-14 22:13:20 UTC

    @Test fun clockFormats() {
        Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("22:13", TimeText.clock(at, h24 = true))
        assertEquals("10:13 PM", TimeText.clock(at, h24 = false))
        assertEquals("10:13", TimeText.clock(at, h24 = false, amPm = false))
        assertEquals("Tue Nov 14", TimeText.format(at, "EEE MMM d"))
    }

    @Test fun formattersAreReused() {
        assertSame(TimeText.formatter("HH:mm"), TimeText.formatter("HH:mm"))
    }
}
