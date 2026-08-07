package com.msmobile.gsearch.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

/**
 * Home screen search bar.
 *
 * Kept as a receiver named exactly what it was named when it inflated RemoteViews. The
 * class name is the widget's identity as far as the launcher is concerned, so renaming it
 * during the Glance migration — or adding a second receiver alongside — would have
 * orphaned every widget already on a home screen. The rendering moved to
 * [GSearchGlanceWidget]; the manifest entry did not change at all.
 */
class GSearchWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = GSearchGlanceWidget()

    companion object {

        /**
         * Call after changing configuration to re-render every instance.
         *
         * Goes through Glance's own update rather than broadcasting APPWIDGET_UPDATE the
         * way the RemoteViews version did. The broadcast is not reliable here: with rapid
         * edits the widget was measured still showing a superseded configuration after the
         * preferences had already been written, because each broadcast kicks off an async
         * recomposition that the next edit can outrun. `updateAll` suspends until the new
         * content is actually handed to the host, so the caller can sequence edits.
         */
        suspend fun refreshAll(context: Context) {
            GSearchGlanceWidget.pushConfig(context)
            GSearchGlanceWidget().updateAll(context)
        }
    }
}
