package com.example.kenyanradiostations

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `system default is the first option`() {
        assertEquals("", AppLanguage.OPTIONS.first().tag)
    }

    @Test
    fun `every shipped locale has an option`() {
        val tags = AppLanguage.OPTIONS.map { it.tag }
        // Must stay in step with res/xml/locales_config.xml.
        assertEquals(listOf("", "en", "sw"), tags)
    }

    @Test
    fun `index falls back to system for an unknown tag`() {
        assertEquals(0, AppLanguage.indexOf("fr"))
        assertEquals(0, AppLanguage.indexOf(""))
        assertEquals(2, AppLanguage.indexOf("sw"))
    }
}
