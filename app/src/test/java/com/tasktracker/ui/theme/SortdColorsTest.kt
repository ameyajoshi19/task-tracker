package com.tasktracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SortdColorsTest {
    @Test
    fun `quadrant gradient start colors are correct`() {
        assertEquals(Color(0xFF7C3AED), SortdColors.nowStart)
        assertEquals(Color(0xFFEC4899), SortdColors.nextStart)
        assertEquals(Color(0xFFF59E0B), SortdColors.soonStart)
        assertEquals(Color(0xFF10B981), SortdColors.laterStart)
    }

    @Test
    fun `quadrant gradient end colors are correct`() {
        assertEquals(Color(0xFFA78BFA), SortdColors.nowEnd)
        assertEquals(Color(0xFFF472B6), SortdColors.nextEnd)
        assertEquals(Color(0xFFFBBF24), SortdColors.soonEnd)
        assertEquals(Color(0xFF34D399), SortdColors.laterEnd)
    }

    @Test
    fun `dark surface colors are correct`() {
        assertEquals(Color(0xFF1A1625), SortdColors.Dark.background)
        assertEquals(Color(0xFF1A1625), SortdColors.Dark.surface)
        assertEquals(Color(0xFF231E30), SortdColors.Dark.card)
        assertEquals(Color(0xFF2D2640), SortdColors.Dark.elevated)
        assertEquals(Color(0xFF3D3455), SortdColors.Dark.border)
    }

    @Test
    fun `light surface colors are correct`() {
        assertEquals(Color(0xFFFAFAFA), SortdColors.Light.background)
        assertEquals(Color(0xFFFFFFFF), SortdColors.Light.surface)
        assertEquals(Color(0xFFF8F7FF), SortdColors.Light.card)
        assertEquals(Color(0xFFF1F0FF), SortdColors.Light.elevated)
        assertEquals(Color(0xFFE9E5FF), SortdColors.Light.border)
    }

    @Test
    fun `accent color is purple`() {
        assertEquals(Color(0xFF7C3AED), SortdColors.accent)
    }
}
