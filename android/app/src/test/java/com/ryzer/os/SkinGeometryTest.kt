package com.ryzer.os

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SkinGeometryTest {
    @Test fun paraShiftsTopEdge() {
        assertArrayEquals(floatArrayOf(22f, 20f, 122f, 20f, 110f, 60f, 10f, 60f),
            SkinMath.para(10f, 20f, 100f, 40f, 12f), 0.001f)
    }

    @Test fun skewClamps() {
        assertEquals(8f, SkinMath.skewFor(10f), 0.001f)
        assertEquals(12f, SkinMath.skewFor(40f), 0.001f)
        assertEquals(18f, SkinMath.skewFor(200f), 0.001f)
    }

    @Test fun segmentsRounding() {
        assertEquals(8, SkinMath.segmentsOn(7.0, 14.0))
        assertEquals(0, SkinMath.segmentsOn(0.0, 0.0))
        assertEquals(16, SkinMath.segmentsOn(30.0, 14.0))
        assertEquals(1, SkinMath.segmentsOn(1.0, 16.0, 16))
    }
}
