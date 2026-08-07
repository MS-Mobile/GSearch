package com.msmobile.gsearch.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.msmobile.gsearch.R

/**
 * Fires a [WidgetAction]'s real intent on behalf of a widget button, then gets out of the
 * way.
 *
 * Needed because of how Glance builds click intents. `actionStartActivity(Intent)` stamps
 * a unique `glance-action:/...` data URI onto any intent that does not already have one,
 * so that two buttons do not collapse into one PendingIntent. Implicit intents whose
 * filters declare no `<data>` then stop resolving: the widget's Search button threw
 * ActivityNotFoundException with `result code=-91`, while Lens kept working purely because
 * its `market://` intent already carried data. That was measured from logcat, not guessed.
 *
 * Routing through an explicit component sidesteps it — an explicit intent resolves by
 * component and ignores data entirely — and the real intent is then built and launched
 * here, in a normal Activity context, exactly as the RemoteViews version built it.
 *
 * The action travels in the data URI rather than an extra. PendingIntent equality ignores
 * extras, so two buttons differing only by an extra would be the same PendingIntent and
 * one would overwrite the other; putting it in the URI makes each button distinct and
 * survives a PendingIntent being reused.
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.data?.lastPathSegment
            ?.let { name -> WidgetAction.entries.firstOrNull { it.name == name } }

        if (action == null) {
            Log.w(TAG, "No widget action in ${intent?.data}")
            finish()
            return
        }

        try {
            startActivity(action.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            // Reachable in the real world: every action targets the Google app, which can
            // be disabled or missing. Silently doing nothing would look like a dead button.
            Log.w(TAG, "Nothing handles ${action.name}", e)
            Toast.makeText(this, R.string.widget_action_unavailable, Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    companion object {
        private const val TAG = "WidgetAction"

        /** The click target for [action] — explicit, and unique per action. */
        fun intentFor(context: Context, action: WidgetAction): Intent =
            Intent(context, WidgetActionActivity::class.java)
                .setData("gsearch://action/${action.name}".toUri())
    }
}
