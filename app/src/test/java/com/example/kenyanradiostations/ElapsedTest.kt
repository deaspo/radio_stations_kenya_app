package com.example.kenyanradiostations

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTest {

    @Test
    fun `under an hour shows minutes and seconds`() {
        assertEquals("0:00", Elapsed.format(0))
        assertEquals("0:05", Elapsed.format(5_000))
        assertEquals("4:12", Elapsed.format(252_000))
        assertEquals("59:59", Elapsed.format(3_599_000))
    }

    @Test
    fun `an hour and over shows hours`() {
        assertEquals("1:00:00", Elapsed.format(3_600_000))
        assertEquals("2:03:04", Elapsed.format(7_384_000))
    }

    @Test
    fun `a negative duration does not produce nonsense`() {
        assertEquals("0:00", Elapsed.format(-1_000))
    }
}
