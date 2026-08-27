package com.msmobile.gsearch.widget

// Two imports below are easy to "correct" to the wrong package, so the reasons live here
// rather than inline — ktlint cannot sort an import list with comments inside it:
//  - actionStartActivity is taken from androidx.glance.appwidget.action, not the core
//    action package: starting an arbitrary Intent needs a real RemoteViews host.
//  - ColorProvider is taken from androidx.glance.color, not androidx.glance.unit. Only the
//    day/night pair can carry a runtime alpha and a night variant; the unit one takes a
//    single colour or a resource id and can do neither.
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode

import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState


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

            GSearchBar(
                actions = actions,
                opacity = opacityPercent / 100f,
                barAction = WidgetConfig.backgroundActionIn(actions),
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

