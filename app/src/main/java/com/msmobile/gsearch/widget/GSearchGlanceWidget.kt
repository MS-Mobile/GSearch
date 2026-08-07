package com.msmobile.gsearch.widget

// Two imports below are easy to "correct" to the wrong package, so the reasons live here
// rather than inline — ktlint cannot sort an import list with comments inside it:
//  - actionStartActivity is taken from androidx.glance.appwidget.action, not the core
//    action package: starting an arbitrary Intent needs a real RemoteViews host.
//  - ColorProvider is taken from androidx.glance.color, not androidx.glance.unit. Only the
//    day/night pair can carry a runtime alpha and a night variant; the unit one takes a
//    single colour or a resource id and can do neither.
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.msmobile.gsearch.R

// Declared sizes, matching res/values/widget_dimens.xml — see that file for why they are
// larger than the size they render at on One UI. Glance takes dp in code rather than from
// resources, so the two have to be kept in step by hand; the XML layout is still the
// widget's initialLayout and preview, so it cannot simply be deleted.
private val BAR_HEIGHT = 67.dp
private val GLYPH_SIZE = 30.dp
private val BAR_PADDING = 10.dp

/**
 * The search bar, composed rather than inflated.
 *
 * The gain over the RemoteViews version is that the row of buttons is a real list: actions
 * are emitted by iterating the configuration, so there is no fixed set of slot views to
 * declare, no hiding of unused ones, and no [WidgetConfig.MAX_ACTIONS] ceiling imposed by
 * the layout. The ceiling is kept anyway, because a widget still cannot grow its own cell
 * and past four icons they simply crowd.
 */
class GSearchGlanceWidget : GlanceAppWidget() {

    // Recompose when the user resizes, so the bar tracks the width it is actually given.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // The configuration is mirrored into the widget's Glance state and read back
            // from there, rather than read straight out of SharedPreferences.
            //
            // Two things forced that, both measured rather than assumed. provideGlance is
            // a session function — it runs once when the session opens, so anything read
            // in it and captured stays frozen for the session's life. Moving the reads in
            // here was not enough either: a plain SharedPreferences read is not Compose
            // state, so update() finds nothing invalidated and skips recomposition. Either
            // way the widget sat exactly one edit behind. currentState is observable, so
            // writing it is what actually makes the widget redraw.
            //
            // The fallback matters for a widget placed after the settings were changed:
            // it has no state of its own yet, so it reads the app's preferences directly.
            val state = currentState<Preferences>()
            val actions = state[KEY_ACTIONS]?.let(WidgetConfig::parseActions)
                ?: WidgetConfig.actions(context)
            val opacityPercent = state[KEY_OPACITY] ?: WidgetConfig.opacityPercent(context)

            SearchBar(
                actions = actions,
                opacity = opacityPercent / 100f,
                backgroundAction = WidgetConfig.backgroundActionIn(actions),
            )
        }
    }

    companion object {
        val KEY_ACTIONS = stringPreferencesKey("actions")
        val KEY_OPACITY = intPreferencesKey("opacity_percent")

        /**
         * Copies the saved configuration into every placed widget's own state.
         *
         * Writing the state is what triggers recomposition; [GlanceAppWidget.updateAll]
         * alone does not, since nothing it observes would have changed.
         */
        suspend fun pushConfig(context: Context) {
            val actions = WidgetConfig.serialiseActions(WidgetConfig.actions(context))
            val opacity = WidgetConfig.opacityPercent(context)
            GlanceAppWidgetManager(context)
                .getGlanceIds(GSearchGlanceWidget::class.java)
                .forEach { id ->
                    updateAppWidgetState(context, id) { state ->
                        state[KEY_ACTIONS] = actions
                        state[KEY_OPACITY] = opacity
                    }
                }
        }
    }
}

@Composable
private fun SearchBar(
    actions: List<WidgetAction>,
    opacity: Float,
    backgroundAction: WidgetAction,
) {
    val context = LocalContext.current

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .then(pillBackground(context, opacity))
                // Tapping the gap between icons searches, as on the stock bar.
                .clickable(
                    actionStartActivity(WidgetActionActivity.intentFor(context, backgroundAction)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = BAR_PADDING),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                actions.forEach { action ->
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(
                                actionStartActivity(
                                    WidgetActionActivity.intentFor(context, action),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(action.iconRes),
                            contentDescription = context.getString(action.labelRes),
                            modifier = GlanceModifier.size(GLYPH_SIZE),
                        )
                    }
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
@Composable
private fun pillBackground(context: Context, opacity: Float): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier
            .background(pillColor(context, opacity))
            .cornerRadius(BAR_HEIGHT / 2)
    } else {
        GlanceModifier.background(ImageProvider(R.drawable.widget_pill_background))
    }
private fun pillColor(context: Context, opacity: Float) = ColorProvider(
    day = Color(ContextCompat.getColor(context, R.color.widget_pill_day)).copy(alpha = opacity),
    night = Color(ContextCompat.getColor(context, R.color.widget_pill_night)).copy(alpha = opacity),
)
