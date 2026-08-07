package com.msmobile.gsearch.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msmobile.gsearch.R
import com.msmobile.gsearch.intents.SearchIntents

/**
 * One thing the widget can do. Everything a slot needs to render and fire is here, so
 * adding an action later means adding an entry — not touching the layout or the provider.
 *
 * The enum name is what gets persisted in [WidgetConfig], so renaming a constant breaks
 * existing widget configurations. Declaration order is the order the settings screen
 * offers them in, and the fallback order for actions the user has not arranged.
 */
enum class WidgetAction(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    SEARCH(R.drawable.ic_widget_search, R.string.widget_action_search),
    MIC(R.drawable.ic_widget_mic, R.string.widget_action_mic),
    LENS(R.drawable.ic_widget_lens, R.string.widget_action_lens),
    GEMINI(R.drawable.ic_widget_gemini, R.string.widget_action_gemini),
    ;

    /**
     * Resolved per-bind rather than stored, because [LENS] depends on what is installed
     * and that can change between updates without the widget being reconfigured.
     */
    fun intent(context: Context): Intent = when (this) {
        SEARCH -> SearchIntents.search().intent
        MIC -> SearchIntents.voice().intent
        LENS -> SearchIntents.lens(context).intent
        GEMINI -> SearchIntents.geminiChat().intent
    }
}
