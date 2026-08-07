# Glance migration — what actually bit

Everything here was measured on a Pixel Launcher emulator (API 36, density 480) with
`uiautomator dump`, logcat and screenshots. Three of these looked like working code and
were not.

## Build setup

AGP 9 supplies Kotlin itself, and applying `org.jetbrains.kotlin.android` on top fails with
a duplicate `kotlin` extension — that was already known. The Compose compiler plugin is the
exception: `org.jetbrains.kotlin.plugin.compose` registers only a compiler plugin and a
`composeCompiler` extension, so it applies cleanly alongside built-in Kotlin. AGP refuses to
configure with `buildFeatures.compose = true` unless it is applied.

Its version must equal the Kotlin version AGP bundles. That is not published anywhere
convenient — read it from AGP's own POM:

    https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.3.1/gradle-9.3.1.pom

AGP 9.3.1 depends on `kotlin-gradle-plugin` 2.2.10, so `kotlinBundledByAgp = "2.2.10"`.
Bumping `agp` means re-reading that POM.

## The migration itself was free

`GSearchWidgetProvider` kept its class name and became a `GlanceAppWidgetReceiver`. The
manifest did not change. The widget already sitting on the home screen kept working through
the swap — same instance, same id, no re-add — and measured identically afterwards: bar
`[59,1248][919,1449]` = 201px = 67dp, slots 200px each, glyphs 90px = 30dp.

Renaming the receiver, or adding a second one beside it, would have orphaned it.

## Three things Glance broke

### 1. It rewrites your intent's data URI

`actionStartActivity(Intent)` stamps a unique `glance-action:/...` data URI onto any intent
that does not already have one, so two buttons do not collapse into the same PendingIntent.

Implicit intents whose filters declare no `<data>` then stop resolving. The Search button
died with:

    ActivityTaskManager: START ... act=android.search.action.GLOBAL_SEARCH
      dat=glance-action:/... result code=-91
    E RemoteViews: Cannot send pending intent due to unknown exception:
    E RemoteViews: android.content.ActivityNotFoundException

Lens kept working the whole time, purely because its `market://` intent already carried
data and so was left alone. One working button and one dead button from the same change is
what made this worth chasing rather than assuming.

**Fix:** route every click through `WidgetActionActivity`, an invisible trampoline in this
app. An explicit intent resolves by component and ignores data, so the rewrite is harmless.
The action travels in the data URI (`gsearch://action/SEARCH`), not an extra — PendingIntent
equality ignores extras, so two buttons differing only by an extra would be the same
PendingIntent and one would overwrite the other.

### 2. Its colour filter cannot express alpha

The RemoteViews version did opacity with `setImageAlpha` on an ImageView holding the pill
shape: exact, continuous, working at every API level.

Glance exposes no alpha for an image. Its `ColorFilter.tint` maps to
`ImageView.setColorFilter`, which blends **SRC_ATOP** — colour from the source, alpha from
the destination. An alpha in the tint is silently discarded. A widget configured at 0%
opacity rendered fully solid.

**Fix:** opacity comes from a background colour instead, which needs `cornerRadius` for the
pill shape, which does nothing before API 31. Below that the shape drawable is used and the
opacity setting is ignored. Losing transparency on old devices beats losing the shape.

### 3. `provideGlance` is a session function, and SharedPreferences is not Compose state

This one took two wrong fixes.

`provideGlance` runs **once**, when the session opens. `update()` recomposes the content
lambda without calling it again. So configuration read in `provideGlance` and captured is
frozen for the session's life — the widget sat exactly one edit behind the saved state.

Moving the reads inside `provideContent` did not help, and that is the interesting part: a
plain SharedPreferences read is not Compose state, so `update()` finds nothing invalidated
and skips recomposition altogether. Still one edit behind, with no error anywhere — logcat
showed `AppWidgetServiceImpl: Trying to notify widget update ... widget id: 4` every time.

**Fix:** mirror the configuration into the widget's own Glance state with
`updateAppWidgetState`, and read it back with `currentState`. That is observable, so writing
it is what actually makes the widget redraw. `updateAll` alone never will. A widget placed
after the settings were last changed has no state yet, so the composable falls back to
reading the app's preferences directly.

## Density, finally settled

The 1.2x inflation in `widget_dimens.xml` compensates for One UI rendering third-party
widget RemoteViews at density 400 instead of 480. It was never tested elsewhere.

Pixel Launcher applies **no rescale at all**: 67dp declared measured 201px = 67dp, and the
glyph measured 31dp against the 25dp it renders at on One UI. So a single dp value cannot be
right on both, and the widget is about a fifth larger on Pixel-family launchers. The 1.2x is
kept because it targets the device this was tuned and approved on. Glance changed nothing
here — it takes the same dp values and lands in the same place.

## Still true from the intent POC

The barrier is caller identity, not the component: Lens and Gemini Live reject third-party
callers whatever surface you go through. `VOICE_ASSIST` gives voice search and
`RecognizerIntent.ACTION_WEB_SEARCH` gives Gemini, which is the opposite of what the names
suggest. The GEMINI action opens whatever assistant the device has configured.

On the emulator the Lens app is absent, so Lens correctly falls through to the Play Store —
which is the intended behaviour, verified rather than inferred.
