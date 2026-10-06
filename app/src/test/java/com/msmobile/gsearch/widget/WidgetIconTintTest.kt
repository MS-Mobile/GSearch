package com.msmobile.gsearch.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colours are the shipped ones from res/values/widget.xml, so these pin the cases that
 * matter on a real home screen rather than the contrast arithmetic in the abstract.
 */
class WidgetIconTintTest {

    private val dayPill = Color(0xFFF1F3F4)
    private val nightPill = Color(0xFF202124)
    private val dark = Color(0xFF5F6368)
    private val light = Color(0xFFE8EAED)
    private val darkWallpaper = Color(0xFF0B2A5B)
    private val lightWallpaper = Color(0xFFF5E9D0)

    private fun resolve(pill: Color, opacity: Float, wallpaper: Color?) =
        WidgetIconTint.resolve(pill, opacity, wallpaper, dark, light)

    @Test
    fun `an opaque pill keeps the shipped glyph colour whatever the wallpaper`() {
        assertEquals(dark, resolve(dayPill, 1f, darkWallpaper))
        assertEquals(light, resolve(nightPill, 1f, lightWallpaper))
    }

    @Test
    fun `an unknown wallpaper is judged against the pill alone`() {
        assertEquals(dark, resolve(dayPill, 0f, null))
        assertEquals(light, resolve(nightPill, 0f, null))
    }

    @Test
    fun `a see-through light pill over a dark wallpaper gets light glyphs`() {
        // The reported bug: grey glyphs meant for the light pill, on a dark blue wallpaper.
        assertEquals(light, resolve(dayPill, 0.2f, darkWallpaper))
    }

    @Test
    fun `a see-through dark pill over a light wallpaper gets dark glyphs`() {
        assertEquals(dark, resolve(nightPill, 0.2f, lightWallpaper))
    }

    @Test
    fun `a fully transparent pill is judged against the wallpaper alone`() {
        assertEquals(light, resolve(dayPill, 0f, darkWallpaper))
        assertEquals(dark, resolve(nightPill, 0f, lightWallpaper))
    }

    @Test
    fun `a translucent wallpaper colour is treated as opaque`() {
        assertEquals(light, resolve(dayPill, 0f, darkWallpaper.copy(alpha = 0f)))
    }

    @Test
    fun `contrast runs from 1 for identical colours to 21 for black on white`() {
        assertEquals(1f, WidgetIconTint.contrast(dark, dark), DELTA)
        assertEquals(21f, WidgetIconTint.contrast(Color.Black, Color.White), DELTA)
        assertTrue(WidgetIconTint.contrast(dark, dayPill) > WidgetIconTint.contrast(light, dayPill))
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
