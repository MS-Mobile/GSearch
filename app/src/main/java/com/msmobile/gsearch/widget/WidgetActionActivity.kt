package com.msmobile.gsearch.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.msmobile.gsearch.R
import com.msmobile.gsearch.intents.SearchIntents

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
            val target = action.intent(this)
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (action == WidgetAction.GEMINI_LIVE) {
                scheduleTripHome(applicationContext)
            }
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

        /**
         * PROTOTYPE — how long to wait for the microphone before giving up on the trip home.
         *
         * Giving up means staying put, not going home anyway. No mic means Live is not
         * running yet — most likely Gemini is asking for the microphone permission on first
         * use, or the shortcut's server flag is off — and sending that to the background
         * would hide the very prompt the user has to answer. Kept short so a mic that only
         * starts after the user answers a prompt does not yank Live away from them.
         */
        private const val LIVE_MIC_WAIT_MS = 3_000L

        /**
         * PROTOTYPE — opens the launcher once Live is up, so it carries on as voice Live.
         *
         * "Up" is Live starting the microphone, observed through
         * [AudioManager.registerAudioRecordingCallback], which needs no permission. Measured
         * on a Galaxy S23: Live's screen at +0.08 s, mic at +0.44 s, camera at +0.82 s —
         * so leaving on the mic beats the camera, where a fixed 1 s delay did not. The
         * camera was measured opening late on an emulator too (after the trip home), so it
         * is no use as the trigger. Blind spot: any other app starting to record in that
         * window triggers it as well.
         *
         * Lives in the application context because this activity is gone by then: it
         * finishes straight away, and noHistory would remove it anyway once Live covers it.
         */
        private fun scheduleTripHome(context: Context) {
            val handler = Handler(Looper.getMainLooper())
            val audio = context.getSystemService(AudioManager::class.java)
            val started = SystemClock.elapsedRealtime()
            var done = false
            lateinit var callback: AudioManager.AudioRecordingCallback
            fun stopWaiting(): Boolean {
                if (done) return false
                done = true
                audio.unregisterAudioRecordingCallback(callback)
                handler.removeCallbacksAndMessages(null)
                return true
            }
            fun goHome() {
                if (!stopWaiting()) return
                val elapsed = SystemClock.elapsedRealtime() - started
                Log.i(TAG, "Sending Live to the background (microphone started, +${elapsed}ms)")
                try {
                    context.startActivity(
                        SearchIntents.home().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.w(TAG, "No launcher to return to", e)
                }
            }
            callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    if (configs.isNotEmpty()) goHome()
                }
            }
            audio.registerAudioRecordingCallback(callback, handler)
            handler.postDelayed({
                if (stopWaiting()) Log.i(TAG, "No microphone within ${LIVE_MIC_WAIT_MS}ms; leaving Live in front")
            }, LIVE_MIC_WAIT_MS)
        }

        /** The click target for [action] — explicit, and unique per action. */
        fun intentFor(context: Context, action: WidgetAction): Intent =
            Intent(context, WidgetActionActivity::class.java)
                .setData("gsearch://action/${action.name}".toUri())
    }
}
