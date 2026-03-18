package com.tasktracker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SortdColors {
    val accent = Color(0xFF7C3AED)
    val accentLight = Color(0xFFA78BFA)

    val nowStart = Color(0xFF7C3AED)
    val nowEnd = Color(0xFFA78BFA)
    val nextStart = Color(0xFFEC4899)
    val nextEnd = Color(0xFFF472B6)
    val soonStart = Color(0xFFF59E0B)
    val soonEnd = Color(0xFFFBBF24)
    val laterStart = Color(0xFF10B981)
    val laterEnd = Color(0xFF34D399)

    val nowGradient = Brush.linearGradient(listOf(nowStart, nowEnd))
    val nextGradient = Brush.linearGradient(listOf(nextStart, nextEnd))
    val soonGradient = Brush.linearGradient(listOf(soonStart, soonEnd))
    val laterGradient = Brush.linearGradient(listOf(laterStart, laterEnd))

    object Dark {
        val background = Color(0xFF0A0A0A)
        val surface = Color(0xFF1A1625)
        val card = Color(0xFF231E30)
        val elevated = Color(0xFF2D2640)
        val border = Color(0xFF3D3455)
        val textPrimary = Color(0xFFF1F5F9)
        val textSecondary = Color(0xFF94A3B8)
        val textTertiary = Color(0xFF555555)
    }

    object Light {
        val background = Color(0xFFFAFAFA)
        val surface = Color(0xFFFFFFFF)
        val card = Color(0xFFF8F7FF)
        val elevated = Color(0xFFF1F0FF)
        val border = Color(0xFFE9E5FF)
        val textPrimary = Color(0xFF1E293B)
        val textSecondary = Color(0xFF64748B)
        val textTertiary = Color(0xFF94A3B8)
    }
}
