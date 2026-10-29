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

    @Test fun glowSizesQuantize() {
        assertEquals(152f, SkinMath.quantize(147.3f), 0.001f)
        assertEquals(144f, SkinMath.quantize(144f), 0.001f)
        assertEquals(8f, SkinMath.quantize(1f), 0.001f)
    }

    @Test fun twoLinesKeepsTheRestOnLineTwo() {
        val m = { s: String -> s.length * 10f }
        assertEquals(listOf("CASTLEVANIA", "- ARIA OF SORROW"), SkinMath.twoLines("CASTLEVANIA - ARIA OF SORROW", 100f, m))
        assertEquals(listOf("MOTHER 3"), SkinMath.twoLines("MOTHER 3", 100f, m))
    }

    @Test fun lightColours() {
        assertEquals(true, SkinMath.isLight(0xFFF7CDCD.toInt()))
        assertEquals(false, SkinMath.isLight(0xFF1A1A24.toInt()))
        assertEquals(false, SkinMath.isLight(0xFFFF2A3D.toInt()))
    }
}
