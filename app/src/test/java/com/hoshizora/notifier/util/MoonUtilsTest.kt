package com.hoshizora.notifier.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MoonUtilsTest {

    @Test
    fun parseMoonInfo_nullOrInvalidInput_returnsNull() {
        assertNull(MoonUtils.parseMoonInfo(null))
        assertNull(MoonUtils.parseMoonInfo(""))
        assertNull(MoonUtils.parseMoonInfo("abc"))
    }

    @Test
    fun parseMoonInfo_newMoon_calculatesCorrectly() {
        val info = MoonUtils.parseMoonInfo("0.0")
        assertNotNull(info)
        assertEquals("🌑", info?.icon)
        assertEquals("新月", info?.phaseName)
        assertEquals(0, info?.percentage)
    }

    @Test
    fun parseMoonInfo_crescentMoon_calculatesCorrectly() {
        val info = MoonUtils.parseMoonInfo("3.5")
        assertNotNull(info)
        assertEquals("🌒", info?.icon)
        assertEquals("三日月", info?.phaseName)
    }

    @Test
    fun parseMoonInfo_firstQuarter_calculatesCorrectly() {
        val info = MoonUtils.parseMoonInfo("7.38")
        assertNotNull(info)
        assertEquals("🌓", info?.icon)
        assertEquals("上弦の月", info?.phaseName)
        assertEquals(50, info?.percentage)
    }

    @Test
    fun parseMoonInfo_fullMoon_calculatesCorrectly() {
        val info = MoonUtils.parseMoonInfo("14.76")
        assertNotNull(info)
        assertEquals("🌕", info?.icon)
        assertEquals("満月", info?.phaseName)
        assertEquals(100, info?.percentage)
    }

    @Test
    fun parseMoonInfo_thirdQuarter_calculatesCorrectly() {
        val info = MoonUtils.parseMoonInfo("22.14")
        assertNotNull(info)
        assertEquals("🌗", info?.icon)
        assertEquals("下弦の月", info?.phaseName)
        assertEquals(50, info?.percentage)
    }

    @Test
    fun formatMoonDisplay_validAge_returnsFormattedString() {
        val result = MoonUtils.formatMoonDisplay("14.2")
        assertEquals("🌕 満月 100% (月齢 14.2)", result)
    }

    @Test
    fun formatMoonDisplay_invalidAge_fallbackToOriginal() {
        val result = MoonUtils.formatMoonDisplay("invalid")
        assertEquals("🌙月齢 invalid", result)
    }

    @Test
    fun formatMoonDisplay_null_returnsEmpty() {
        val result = MoonUtils.formatMoonDisplay(null)
        assertEquals("", result)
    }
}
