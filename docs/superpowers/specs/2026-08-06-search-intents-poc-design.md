# Search Intents POC — Design

**Date:** 2026-08-06
**Branch:** `poc/search-intents`
**Status:** Historical — delivered, then superseded on 2026-08-07

> This describes the throwaway intent probe, not the app. `PocActivity` and `IntentProbe`
> were deleted once they had done their job; `SearchIntents.kt` is the only file here that
> shipped. The widget it fed is now built on Glance and configured from a settings screen.
>
> Kept as the record of what was designed and why. For what exists today, read
> `docs/superpowers/ROADMAP.md`.

## Context

The end goal is an Android home screen widget that replicates the Google Search
widget: a pill-shaped bar offering search, voice search, and Lens. The reference
is the resizable Google widget on the home screen — not the Pixel launcher's
fixed bottom bar, which is part of the launcher and not a widget.

The widget will expose three actions:

- **Search** (magnifier icon) — open the Google search input
- **Mic** — voice search
- **Lens** — the Google app's built-in Lens camera, not the standalone Lens app

The emulator used for development has `com.google.android.googlequicksearchbox`
(the Google app) installed and does **not** have `com.google.ar.lens` (standalone
Lens). This matches the intent to target Lens as hosted inside the Google app.

## Goal of this POC

Determine which concrete intents actually launch each of the three actions.

This is a discovery exercise, not a feature. Nothing here ships as UI. The output
is knowledge: a verified intent per action, recorded so the widget can be built
against it.

## Why a POC before the widget

The riskiest unknown is which component names and intent actions work — not the
widget plumbing, which is well-trodden.

Widget `PendingIntent`s give almost no feedback when an intent fails to resolve:
the tap silently does nothing, and every iteration requires re-adding the widget
to the home screen and reading logcat to find out why. An Activity gives an
immediate, visible result and a fast edit-run-observe loop.

The intent-building code then moves into the widget unchanged. The only
widget-specific delta is `FLAG_ACTIVITY_NEW_TASK`, which is known and small.

## Key constraint

`PackageManager.resolveActivity()` returning a match does **not** mean the intent
will launch.

Google app activities are frequently non-exported. A candidate can resolve
cleanly and then throw `SecurityException` on `startActivity`. Only an actual
launch attempt proves a candidate works.

The POC therefore attempts real launches and reports the outcome. A
resolution-only check would produce false confidence, and we would discover the
problem later from inside the widget, where there is no feedback.

## Design

Three sections — Search, Mic, Lens. Each section lists its candidate intents as
individually tappable rows.

This is deliberately more than three buttons. We do not know which intent is
correct, and that is the entire question the POC exists to answer. Three buttons
would test one guess per action; rows test all candidates and identify the
winner. The cost is roughly 30 additional lines.

Each row displays:

- the candidate's label
- whether it resolves via `PackageManager`
- after tapping: the outcome — `LAUNCHED`, `ACTIVITY_NOT_FOUND`, or
  `SECURITY_EXCEPTION` — and the component it resolved to

### Components

**`SearchIntents.kt`** — pure functions returning ordered candidate lists for
`SEARCH`, `VOICE`, and `LENS`. Constructs `Intent` objects only: no `Context`, no
side effects. This is the only file that ships to the real widget, carrying the
verified winners.

**`IntentProbe.kt`** — accepts a `Context` and a single candidate. Checks
resolution, attempts the launch, catches `ActivityNotFoundException` and
`SecurityException`, and returns a result object. The only piece that touches the
system, and deliberately small.

**`PocActivity.kt`** — renders the sections, wires row taps to `IntentProbe`,
displays results. Throwaway; none of it moves to the widget.

The boundary that matters: `SearchIntents` knows nothing about launching, and
`IntentProbe` knows nothing about which intents exist. `PocActivity` is the only
place they meet.

### Candidate intents

A starting set, to be refined as we learn:

**Search**
- `SearchManager.INTENT_ACTION_GLOBAL_SEARCH`
- `Intent.ACTION_ASSIST`
- `Intent.ACTION_WEB_SEARCH`
- The explicit `googlequicksearchbox` search component

**Voice**
- `RecognizerIntent.ACTION_WEB_SEARCH`
- `Intent.ACTION_VOICE_ASSIST`
- `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`

**Lens**
- The Google app's exported Lens activity
- A `google://lens`-style URI with the package pinned

If every candidate in a section fails, that is a finding rather than a failure:
it tells us the action needs a different approach before we commit to the widget
design.

## Error handling

Failures are the POC's primary output and are surfaced, never swallowed:

- No candidate resolves — the row reports it, naming the action
- `SecurityException` on launch — reported distinctly from
  `ActivityNotFoundException`, since the two imply different fixes (non-exported
  target versus wrong component name)

## Verification

Manual, on the emulator. Each candidate is tapped and its outcome recorded.

Automated tests are explicitly out of scope for this phase. The candidate list is
hardcoded and the meaningful behaviour is what the system does with each intent,
which unit tests cannot observe. The manual emulator run is the verification.

## Deliverable

A short findings note recording, per action:

- the winning intent and its resolved component
- what failed, and how

That note is what the widget is built against.

## Build changes

- Add the Kotlin Android plugin to `libs.versions.toml` and `app/build.gradle.kts`
  (the scaffold is currently Java-only, with no Kotlin plugin applied)
- Add `PocActivity` to `AndroidManifest.xml` as the launcher activity — the
  project currently has no Activity at all
- Add `.superpowers/` to `.gitignore`

## Out of scope

- The widget itself and `AppWidgetProvider`
- Widget layout, visual design, and the magnifier / mic / Lens artwork
- Widget configuration and resizing
- Behaviour on devices without the Google app installed
- Automated tests
