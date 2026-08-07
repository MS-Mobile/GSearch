package com.msmobile.gsearch.intents

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.core.net.toUri

const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

/** Standalone Google Lens app. Not present on every device. */
const val LENS_APP = "com.google.ar.lens"

const val PLAY_STORE = "com.android.vending"

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
