package com.msmobile.gsearch.widget

import android.content.Context
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import com.msmobile.gsearch.R
import com.msmobile.gsearch.config.GSearchBarPreviewConfig
import com.msmobile.gsearch.config.GSearchBarPreviewConfigProvider
import com.msmobile.gsearch.utils.PreviewPhone

// Declared sizes, matching res/values/widget_dimens.xml — see that file for why they are
// larger than the size they render at on One UI. Glance takes dp in code rather than from
// resources, so the two have to be kept in step by hand; the XML layout is still the
// widget's initialLayout and preview, so it cannot simply be deleted.
private val BAR_HEIGHT = 67.dp
private val GLYPH_SIZE = 30.dp
private val BAR_PADDING = 10.dp

@Composable
fun GSearchBar(
    actions: List<WidgetAction>,
    opacity: Float,
    barAction: WidgetAction,
) {
    GSearchBarContent(
        actions = actions,
        opacity = opacity,
        onBarClicked = { context ->
            actionStartActivity(WidgetActionActivity.intentFor(context, barAction))
        },
    )
}

/**
 * Built from the `PreviewCompat` elements rather than Glance's own so the same tree can be
 * drawn by a Compose preview — see [PreviewCompatGlanceBox] for why Glance's cannot be.
 */
@Composable
private fun GSearchBarContent(
    actions: List<WidgetAction>,
    opacity: Float,
    onBarClicked: (Context) -> Action,
) {
    val context = PreviewCompatGlanceContext.current

    PreviewCompatGlanceBox(
        modifier = PreviewCompatGlanceModifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .then(pillBackground(context, opacity))
            // Tapping the gap between icons searches, as on the stock bar.
            .clickable { onBarClicked(context) },
        contentAlignment = Alignment.Center,
    ) {
        PreviewCompatGlanceRow(
            modifier = PreviewCompatGlanceModifier
                .fillMaxSize()
                .padding(horizontal = BAR_PADDING),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            actions.forEach { action ->
                PreviewCompatGlanceBox(
                    modifier = PreviewCompatGlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .clickable {
                            actionStartActivity(
                                WidgetActionActivity.intentFor(context, action),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewCompatGlanceImage(
                        resId = action.iconRes,
                        contentDescription = context.getString(action.labelRes),
                        modifier = PreviewCompatGlanceModifier.size(GLYPH_SIZE),
                    )
                }
            }
        }
    }
}

/**
 * The pill itself: rounded ends, in the right colour, at the configured opacity.
 *
 * This is where Glance is genuinely worse than the RemoteViews version it replaced. There
 * the background was an ImageView holding the shape drawable and opacity was `setImageAlpha`
 * — exact, continuous, and working at every API level. Glance exposes no alpha for an
 * image, and its one colour filter is `ImageView.setColorFilter`, which blends SRC_ATOP:
 * it takes the colour from the source and the alpha from the destination, so an alpha in
 * the tint is discarded and the pill stays fully opaque. That was measured, not assumed —
 * a widget configured at 0% opacity rendered solid.
 *
 * So opacity has to come from a background colour, and a background colour only gets
 * rounded ends via `cornerRadius`, which does nothing before API 31. Rather than ship a
 * rectangular pill on older devices, those fall back to the shape drawable and simply do
 * not get the opacity setting. Losing transparency below API 31 is a smaller regression
 * than losing the shape, and it is the only part of the widget that Glance made worse.
 *
 * Both colour variants are supplied rather than one resolved colour, because the widget is
 * drawn in the launcher's process and it is the launcher's dark-mode state that decides.
 */
private fun pillBackground(context: Context, opacity: Float): PreviewCompatGlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PreviewCompatGlanceModifier
            .background(
                day = pillColor(context, R.color.widget_pill_day, opacity),
                night = pillColor(context, R.color.widget_pill_night, opacity),
            )
            .cornerRadius(BAR_HEIGHT / 2)
    } else {
        PreviewCompatGlanceModifier.background(R.drawable.widget_pill_background)
    }

private fun pillColor(context: Context, @ColorRes colorRes: Int, opacity: Float) =
    Color(ContextCompat.getColor(context, colorRes)).copy(alpha = opacity)

/**
 * The bar as the launcher draws it.
 *
 * Goes through [GSearchBar] rather than straight to [GSearchBarContent] so the preview
 * covers the real entry point, action wiring included. Building those actions is deferred
 * by [PreviewCompatGlanceModifier.clickable] and so never happens here, which is what lets
 * a preview compose a tree whose clicks address a widget host that is not present.
 */
@VisibleForTesting
@PreviewPhone
@Composable
internal fun GSearchBarPreview(
    @PreviewParameter(GSearchBarPreviewConfigProvider::class) config: GSearchBarPreviewConfig,
) {
    GSearchBar(
        actions = config.actions,
        opacity = config.opacity,
        barAction = WidgetConfig.backgroundActionIn(config.actions),
    )
}
