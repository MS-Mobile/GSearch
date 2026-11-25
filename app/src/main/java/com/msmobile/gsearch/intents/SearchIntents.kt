package com.msmobile.gsearch.intents

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.core.net.toUri

const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

/** Standalone Google Lens app. Not present on every device. */
const val LENS_APP = "com.google.ar.lens"

const val PLAY_STORE = "com.android.vending"

/** The Gemini app — a thin shell that hands most of its work to [GOOGLE_APP]. */
const val GEMINI_APP = "com.google.android.apps.bard"

/** Gemini's accessibility-shortcut target for guided vision; exported since 1.0.1009. */
private const val GUIDED_VISION_ACTIVITY =
    "com.google.android.apps.bard.shellapp.VisualInterpreterShortcutActivity"

data class Candidate(val label: String, val intent: Intent)

/**
 * The intents behind the widget's actions, each verified by real launch on device.
 *
 * Everything is pinned to a package. The implicit forms are ambiguous on real devices —
 * Chrome competes for `WEB_SEARCH`, and any installed assistant competes for `ASSIST` and
 * `VOICE_ASSIST` — so unpinned they would show a chooser.
 *
 * Findings, including everything that failed:
 * docs/superpowers/findings/2026-08-06-search-intents.md
 */
object SearchIntents {

    /** Opens the Google search input. → `.GoogleAppGlobalSearchImplicitGatewayInternal` */
    fun search() = Candidate(
        "Search",
        Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(GOOGLE_APP),
    )

    /**
     * Voice search — the "Ouvindo…" listening screen. → `.GoogleAppVoiceAssistEntrypoint`
     *
     * Counterintuitive but measured: despite the name, `VOICE_ASSIST` gives plain voice
     * search here, while [geminiChat]'s `RecognizerIntent.ACTION_WEB_SEARCH` gives Gemini.
     * The two are inverted relative to what the constants imply. Do not "fix" this by
     * swapping them back on the strength of the names.
     */
    fun voice() = Candidate(
        "Mic",
        Intent("android.intent.action.VOICE_ASSIST").setPackage(GOOGLE_APP),
    )

    /**
     * Gemini chat in its compact floating-pill form — an overlay rather than a full-screen
     * takeover. → `...handsfree.HandsFreeActivity` → `...robin.ui.floaty.FloatyActivity`
     *
     * The pill also hosts Gemini Live: tapping Live inside it launches no activity at all,
     * so no intent for Live exists to copy. This intent plus one tap is the shortest path
     * to Live in pill form.
     */
    fun geminiChat() = Candidate(
        "Gemini",
        Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP),
    )

    /**
     * Gemini Live with the camera on — "guided vision". Only offered when
     * [hasGuidedVision] says the shortcut is installed; there is no fallback, so on a
     * device without it the button is hidden rather than doing something else.
     *
     * This is the one way into Live open to a third-party app. Live itself only honours
     * its `feature=` deep link when Gemini is the caller; the same link fired from anywhere
     * else lands on plain chat. Gemini's accessibility shortcut is exported, takes no
     * input, and forwards to the Google app *as Gemini* (`setShareIdentityEnabled`), so
     * launching it gets the caller check passed on our behalf. Its options are fixed by
     * Gemini — the camera is always on, which is why this is not offered as plain "Live".
     *
     * One failure stays invisible: the shortcut is gated by a server flag, and with the
     * flag off it finishes without showing anything. That cannot be detected from here.
     */
    fun guidedVision() = Candidate(
        "Guided vision",
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(GEMINI_APP, GUIDED_VISION_ACTIVITY)),
    )

    /**
     * Whether the installed Gemini app has the guided-vision shortcut — absent before
     * 1.0.1009, or when Gemini is missing or disabled. Needs [GEMINI_APP] in `<queries>`.
     *
     * Asks [android.content.pm.PackageManager.resolveActivity], not
     * [Intent.resolveActivity]: for an explicit intent the latter hands back the
     * component it was given without looking it up, so it says yes even with Gemini
     * disabled. Measured, not assumed — the button stayed visible until this was changed.
     */
    fun hasGuidedVision(context: Context): Boolean =
        context.packageManager.resolveActivity(guidedVision().intent, 0) != null

    /**
     * PROTOTYPE — voice-only Gemini Live, reached through [guidedVision].
     *
     * There is no intent for voice Live a third-party app may use (see [guidedVision]). But
     * Live survives being sent to the background: it keeps running as a foreground service
     * with its "Live with Gemini — Listening…" notification, and the camera is dropped,
     * because Android does not let a backgrounded app keep it. So guided vision followed by
     * a trip to the launcher ends as voice Live, minimised. The caller does the second half.
     * Offered under the same condition as guided vision, [hasGuidedVision].
     *
     * Known costs: the camera is on for the length of the delay, and a first-run consent
     * dialog in Live would be interrupted by the trip home.
     */
    fun liveVoice() = guidedVision()

    /**
     * PROTOTYPE — whether Live, which runs inside [GOOGLE_APP], may already use the camera.
     * Without it, Live's first guided-vision start asks for it — measured on a Galaxy S23
     * after clearing data: the mic started first, then the camera prompt appeared, so the
     * mic alone cannot tell "ready" from "about to ask". Needs [GOOGLE_APP] in `<queries>`.
     */
    fun liveHasCamera(context: Context): Boolean =
        context.packageManager.checkPermission(Manifest.permission.CAMERA, GOOGLE_APP) ==
            PackageManager.PERMISSION_GRANTED

    /** The launcher, which is what pressing HOME would show. */
    fun home(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /**
     * Lens, resolved at bind time:
     *
     * 1. the standalone Lens app, when installed — a direct, single-tap camera;
     * 2. otherwise the Play Store listing for it;
     * 3. and if no store can handle that, [search] — whose search page carries a Lens icon
     *    that opens the camera one tap later.
     *
     * Note step 3 works without installing anything, so it is a viable alternative to
     * step 2 rather than only a last resort. Sending users to the store is a deliberate
     * choice, not a limitation.
     *
     * There is deliberately no direct Lens intent. The Google widget fires
     * `...lens.LensActivity`, which is **not exported**; from a third-party app it is
     * denied. `google://lens` and its variants resolve and then silently no-op — they
     * report LAUNCHED while doing nothing, so do not reintroduce them.
     */
    fun lens(context: Context): Candidate {
        context.packageManager.getLaunchIntentForPackage(LENS_APP)?.let {
            return Candidate("Lens (app)", it)
        }

        val store = Intent(Intent.ACTION_VIEW, "market://details?id=$LENS_APP".toUri())
            .setPackage(PLAY_STORE)
        if (store.resolveActivity(context.packageManager) != null) {
            return Candidate("Lens (Play Store)", store)
        }

        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$LENS_APP".toUri(),
        )
        if (web.resolveActivity(context.packageManager) != null) {
            return Candidate("Lens (Play Store web)", web)
        }

        return Candidate("Lens (via search)", search().intent)
    }

    /**
     * Kept only to document what does not work, so it is not retried: this resolves
     * cleanly, reports LAUNCHED, and does nothing. `MainActivity` trampolines to
     * `...lens.LensExportedActivity` and both die immediately.
     */
    @Suppress("unused")
    fun brokenDirectLens() = Candidate(
        "Lens (BROKEN — no-op)",
        Intent(Intent.ACTION_VIEW, "google://lens".toUri()).setComponent(
            ComponentName(GOOGLE_APP, "com.google.android.apps.lens.MainActivity"),
        ),
    )
}
