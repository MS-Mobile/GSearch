package com.msmobile.gsearch.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * Where the widget learns what colour sits behind it.
 *
 * An interface rather than a direct [WallpaperManager] call so the widget can be handed a
 * fixed source — a preview has no launcher and no wallpaper, and the colour logic in
 * [WidgetIconTint] is tested without either.
 */
fun interface WallpaperColorSource {

    /** The wallpaper's dominant colour, or null when the platform cannot say. */
    fun primaryColor(): Color?
}

/**
 * The home screen wallpaper as the platform describes it.
 *
 * Only the dominant colour is available — the platform does not expose the pixels under the
 * widget's cell — so a wallpaper with very different regions can still mislead it. That is
 * why [WidgetIconTint] blends this with the pill rather than trusting it outright: the more
 * opaque the pill, the less the guess matters.
 *
 * `getWallpaperColors` needs no permission, but it is API 27 and some OEM builds have been
 * known to throw from it, so anything other than a clean answer is treated as "unknown".
 */
class SystemWallpaperColorSource(private val context: Context) : WallpaperColorSource {

    override fun primaryColor(): Color? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
                ?.let { Color(it) }
        }.getOrNull()
    }
}

/**
 * Picks the glyph colour from what the glyphs are actually drawn over.
 *
 * The glyphs used to carry one fixed colour per mode, chosen to read against an opaque pill.
 * Once the user turns the opacity down the pill stops being the background and the wallpaper
 * takes over, so a grey meant for a light pill ended up on a dark wallpaper and all but
 * vanished. The colour that matters is the pill laid over the wallpaper at the configured
 * opacity; of the two candidates, whichever contrasts more with that wins.
 *
 * At full opacity the wallpaper drops out entirely, which keeps the shipped look: dark glyphs
 * on the light pill, light glyphs on the dark one. With no wallpaper colour the pill is the
 * only thing known, so the result is the same as at full opacity — never worse than before.
 */
object WidgetIconTint {

    fun resolve(
        pill: Color,
        opacity: Float,
        wallpaper: Color?,
        dark: Color,
        light: Color,
    ): Color {
        val effective = if (wallpaper == null) {
            pill.copy(alpha = 1f)
        } else {
            pill.copy(alpha = opacity.coerceIn(0f, 1f)).compositeOver(wallpaper.copy(alpha = 1f))
        }
        return pickFor(effective, dark, light)
    }

    /** WCAG 2 contrast ratio, from 1 (identical) to 21 (black on white). */
    fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private fun pickFor(background: Color, dark: Color, light: Color): Color =
        if (contrast(dark, background) >= contrast(light, background)) dark else light

    private const val LUMINANCE_OFFSET = 0.05f
}
