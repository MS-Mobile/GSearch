package com.msmobile.gsearch.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The one way to ask every placed widget to re-read the saved configuration.
 *
 * Deliberately not scoped to a screen. A refresh takes effect on the home screen, not in the
 * activity that asked for it, so the moment the user leaves the settings screen is exactly
 * when the update matters — and exactly when an activity-scoped coroutine is cancelled.
 * Editing and then immediately tapping Done or Home used to leave the widget showing the
 * previous configuration: the preferences were written, but the push that would have
 * rendered them died with the scope. A process-lifetime scope is what rules that out.
 *
 * Requests are conflated and served one at a time. A drag emits a change per row it crosses,
 * and overlapping Glance updates were measured leaving the widget on a superseded
 * configuration. Dropping the intermediate requests costs nothing, because
 * [GSearchWidgetProvider.refreshAll] re-reads the preferences when it runs — so whichever
 * request is served last publishes the current state, not a stale snapshot of it.
 */
object WidgetRefresh {

    /**
     * Carries the context rather than closing over one, which is what lets the worker start
     * at class-init time with no lazily-assigned field to guard. Only ever an application
     * context: an activity parked in here would leak for the life of the process.
     */
    private val requests = Channel<Context>(Channel.CONFLATED)

    /**
     * `Main.immediate` is what the `lifecycleScope` this replaced already used, so the only
     * thing changing here is how long the scope lives. Everything [GSearchWidgetProvider
     * .refreshAll] calls is a suspend function that dispatches its own work, so moving to
     * Dispatchers.Default is a fair follow-up — but that is a threading change, and one to
     * settle on a device rather than by reasoning about it.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            for (appContext in requests) {
                GSearchWidgetProvider.refreshAll(appContext)
            }
        }
    }

    /** Safe to call from any thread and as often as an edit gesture produces changes. */
    fun request(context: Context) {
        requests.trySend(context.applicationContext)
    }
}
