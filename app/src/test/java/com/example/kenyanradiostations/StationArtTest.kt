package com.example.kenyanradiostations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationArtTest {

    @Test
    fun `palette index is stable for the same id`() {
        val first = StationArt.paletteIndexOf("citizen-fm-91-5")
        repeat(20) {
            assertEquals(first, StationArt.paletteIndexOf("citizen-fm-91-5"))
        }
    }

    @Test
    fun `palette index is always in range`() {
        // Int.MIN_VALUE is the case abs() gets wrong: abs(Int.MIN_VALUE) is
        // still negative. Exercise a hash that lands there plus a wide sample.
        val ids = buildList {
            add("polygenelubricants") // String with hashCode == Int.MIN_VALUE
            repeat(2000) { add("station-$it") }
        }
        for (id in ids) {
            val index = StationArt.paletteIndexOf(id)
            assertTrue("index $index out of range for '$id'", index in 0 until StationArt.paletteCount)
        }
    }

    @Test
    fun `initials take the first two letters of the first word`() {
        assertEquals("Ci", StationArt.initialsOf("Citizen FM 91.5"))
        assertEquals("Kb", StationArt.initialsOf("KBC"))
        assertEquals("Cp", StationArt.initialsOf("Capital FM"))
        assertEquals("Ra", StationArt.initialsOf("Radio One"))
    }

    @Test
    fun `initials survive punctuation and stray whitespace`() {
        assertEquals("Ho", StationArt.initialsOf("  Hot 96 "))
        assertEquals("Q", StationArt.initialsOf("Q"))
        assertEquals("RD", StationArt.initialsOf("   "))
        assertEquals("RD", StationArt.initialsOf(""))
        assertEquals("Ba", StationArt.initialsOf("-Baraka- FM"))
    }
}
