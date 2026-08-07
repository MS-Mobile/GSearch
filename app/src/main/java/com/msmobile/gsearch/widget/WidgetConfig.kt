package com.msmobile.gsearch.widget

import android.content.Context
import androidx.core.content.edit

/**
 * What the widget shows, in what order, and how see-through it is.
 *
 * One configuration shared by every instance rather than one per widget id. The earlier
 * per-id storage was dropped because the settings screen is reachable from the launcher
 * icon, where there is no widget id to edit; keeping both would have meant a global
 * default plus per-instance overrides, and a user who configured one widget then opened
 * the app would silently be editing something else. Moving back to per-instance is a
 * change to [key] and the call sites that pass an id — nothing else here assumes global.
 */
object WidgetConfig {

    /** Slot positions the layout provides. More actions than this cannot be shown. */
    const val MAX_ACTIONS = 4

    /** Matches the stock Google bar. Gemini is available but off until asked for. */
    val DEFAULT_ACTIONS = listOf(WidgetAction.SEARCH, WidgetAction.MIC, WidgetAction.LENS)

    /** Slightly see-through, which is how the stock bar looks over a wallpaper. */
    const val DEFAULT_OPACITY_PERCENT = 90

    /** Fully opaque, and the top of the range the settings slider offers. */
    const val MAX_OPACITY_PERCENT = 100

    /** Fully opaque as an 8-bit channel, which is the scale the platform APIs use. */
    private const val OPAQUE_ALPHA = 255

    private const val PREFS = "gsearch_widget"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_OPACITY = "opacity_percent"

    /**
     * The enabled actions in display order.
     *
     * Falls back to the defaults when nothing is stored or the stored value has decayed to
     * nothing — an empty bar is never a useful state, and the settings screen refuses to
     * save one, so this can only be reached by a corrupt or hand-edited preference.
     */
    fun actions(context: Context): List<WidgetAction> =
        parseActions(prefs(context).getString(KEY_ACTIONS, null))

    fun setActions(context: Context, actions: List<WidgetAction>) {
        prefs(context).edit { putString(KEY_ACTIONS, serialiseActions(actions)) }
    }

    /** Shared by the preferences store and the widget's own state, which mirrors it. */
    fun serialiseActions(actions: List<WidgetAction>): String =
        actions.distinct().take(MAX_ACTIONS).joinToString(",") { it.name }

    fun parseActions(stored: String?): List<WidgetAction> {
        if (stored == null) return DEFAULT_ACTIONS
        val parsed = stored.split(",")
            .mapNotNull { name -> WidgetAction.entries.firstOrNull { it.name == name } }
            .distinct()
            .take(MAX_ACTIONS)
        return parsed.ifEmpty { DEFAULT_ACTIONS }
    }

    /** 100 means as designed — the pill drawable is opaque and alpha comes only from here. */
    fun opacityPercent(context: Context): Int =
        prefs(context).getInt(KEY_OPACITY, DEFAULT_OPACITY_PERCENT)
            .coerceIn(0, MAX_OPACITY_PERCENT)

    fun setOpacityPercent(context: Context, percent: Int) {
        prefs(context).edit { putInt(KEY_OPACITY, percent.coerceIn(0, MAX_OPACITY_PERCENT)) }
    }

    /** The 0..255 value [android.widget.RemoteViews] and Glance both want. */
    fun backgroundAlpha(context: Context): Int =
        opacityPercent(context) * OPAQUE_ALPHA / MAX_OPACITY_PERCENT

    /**
     * What tapping the bar between the icons does.
     *
     * Search when it is enabled, matching the stock bar, so the habit of tapping anywhere
     * on the pill to type a query keeps working no matter where Search sits in the order.
     * Falls back to the first action rather than doing nothing when Search is switched off.
     */
    fun backgroundAction(context: Context): WidgetAction = backgroundActionIn(actions(context))

    /** As [backgroundAction], for callers that already hold the resolved list. */
    fun backgroundActionIn(actions: List<WidgetAction>): WidgetAction =
        if (WidgetAction.SEARCH in actions) WidgetAction.SEARCH else actions.first()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
