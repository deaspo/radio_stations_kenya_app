package com.example.kenyanradiostations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class RecordingStoreTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    @Test
    fun `names are sortable and readable`() {
        assertEquals(
            "Citizen FM 91.5 - 2026-09-13 14-32.mp3",
            RecordingStore.displayName("Citizen FM 91.5", "mp3", at(2026, 9, 13, 14, 32))
        )
    }

    @Test
    fun `characters that filesystems reject are replaced`() {
        val name = RecordingStore.displayName("Hot 96 / Nairobi: 96.3*", "aac", at(2026, 1, 2, 3, 4))
        assertTrue(name, name.none { it in "\\/:*?\"<>|" })
        // The trailing "-" left by "*" is trimmed, so names never end in punctuation.
        assertEquals("Hot 96 - Nairobi- 96.3 - 2026-01-02 03-04.aac", name)
    }

    @Test
    fun `runs of whitespace collapse and edges are trimmed`() {
        assertEquals(
            "Radio Jambo - 2026-01-02 03-04.mp3",
            RecordingStore.displayName("  Radio\t Jambo  ", "mp3", at(2026, 1, 2, 3, 4))
        )
    }

    @Test
    fun `a blank station name still produces a usable file name`() {
        assertEquals(
            "Recording - 2026-01-02 03-04.mp3",
            RecordingStore.displayName("   ", "mp3", at(2026, 1, 2, 3, 4))
        )
    }

    @Test
    fun `very long names are truncated`() {
        val name = RecordingStore.displayName("x".repeat(200), "mp3", at(2026, 1, 2, 3, 4))
        assertEquals(60, name.substringBefore(" - 2026").length)
    }
}
