package com.msmobile.gsearch.config

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.msmobile.gsearch.widget.WidgetAction
import com.msmobile.gsearch.widget.WidgetConfig

/**
 * One rendering of the widget bar itself, named so the reference image it produces can be
 * told apart from the others.
 *
 * Opacity is held as the 0f..1f fraction the bar is drawn with rather than the 0..100
 * percent the settings screen stores, because these fields mirror the composable's own
 * parameters — the percent-to-fraction conversion belongs at the call site that reads the
 * preference, not here.
 */
@VisibleForTesting
internal data class GSearchBarPreviewConfig(
    val configName: String,
    val actions: List<WidgetAction>,
    val opacity: Float,
)

/**
 * The states of the widget bar worth pinning to a reference image.
 *
 * The bar has only two inputs, so the entries cover the ends of each: the shipped default,
 * the most and fewest icons the slot layout allows — which is where the per-icon spacing
 * would regress — and the two ends of the opacity range. Which action sits in which slot is
 * not enumerated, because every icon is drawn by the same code path and ordering is covered
 * by [ConfigPreviewConfigProvider]'s screen; each entry here is two more images (light and
 * dark, per [com.msmobile.gsearch.utils.PreviewPhone]) to regenerate on any UI change.
 */
@VisibleForTesting
internal class GSearchBarPreviewConfigProvider :
    PreviewParameterProvider<GSearchBarPreviewConfig> {

    override val values: Sequence<GSearchBarPreviewConfig> = sequenceOf(
        GSearchBarPreviewConfig(
            configName = "Default",
            actions = WidgetConfig.DEFAULT_ACTIONS,
            opacity = WidgetConfig.DEFAULT_OPACITY_PERCENT.asOpacity(),
        ),
        // MAX_ACTIONS icons sharing the pill, which is the tightest the glyphs ever sit.
        GSearchBarPreviewConfig(
            configName = "All Actions",
            actions = WidgetAction.entries.toList(),
            opacity = WidgetConfig.MAX_OPACITY_PERCENT.asOpacity(),
        ),
        // The opposite boundary: a single icon, which the row centres in the whole pill.
        GSearchBarPreviewConfig(
            configName = "Single Action",
            actions = listOf(WidgetAction.SEARCH),
            opacity = WidgetConfig.MAX_OPACITY_PERCENT.asOpacity(),
        ),
        // A fully transparent pill still has to show its icons, and on the widget there is
        // no wallpaper behind them in a preview — so this is the entry that would catch a
        // glyph that had come to rely on the pill's fill for contrast.
        GSearchBarPreviewConfig(
            configName = "Transparent",
            actions = WidgetConfig.DEFAULT_ACTIONS,
            opacity = 0f,
        ),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).configName
}

private fun Int.asOpacity(): Float = this / WidgetConfig.MAX_OPACITY_PERCENT.toFloat()
