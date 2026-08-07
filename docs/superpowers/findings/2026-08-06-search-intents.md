# Search Intents POC — Findings

**Date:** 2026-08-06
**Device:** Samsung Galaxy S23 (`SM-S911B`), Android 16, One UI
**Google app:** `com.google.android.googlequicksearchbox` (standalone Lens `com.google.ar.lens` not installed)
**Spec:** `2026-08-06-search-intents-poc-design.md`

## Winners

| Action | Intent to use | Lands on |
|---|---|---|
| **Search** | `Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(GOOGLE_APP)` | search input |
| **Mic** | `Intent("android.intent.action.VOICE_ASSIST").setPackage(GOOGLE_APP)` | voice search, "Ouvindo…" |
| **Gemini (floating pill)** | `Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP)` | `...robin.ui.floaty.FloatyActivity` |
| **Lens** | ⚠️ No direct intent — reachable as **search intent + one tap**. See below. | Lens camera |
| **Gemini Live / Screen Share** | ❌ Not launchable directly — but both are buttons *inside* the floating pill. | — |

### The mic / Gemini intents are inverted relative to their names

Measured visually, not inferred from constant names:

- `VOICE_ASSIST` → plain Google **voice search** ("Ouvindo…", "Pesquisar uma música")
- `RecognizerIntent.ACTION_WEB_SEARCH` → **Gemini**, as the floating pill

This is the opposite of what both names suggest. Anyone "correcting" this by reading the
constant names will silently swap the two buttons.

## The central lesson

`LAUNCHED` means only that `startActivity` did not throw. It says nothing about where
you land. Every one of the first four Lens candidates reported `LAUNCHED`, and none
opened the camera. Two were silent no-ops, one bounced to a Chrome marketing page.

Had the POC only checked `resolveActivity()`, or only checked that launching didn't
throw, we would have shipped a Lens button that does nothing — and discovered it from
inside a widget, where there is no feedback at all.

## Full results

### SEARCH — all candidates launched

| Candidate | Outcome | Resolved to |
|---|---|---|
| `GLOBAL_SEARCH` implicit | LAUNCHED | `.GoogleAppGlobalSearchImplicitGatewayInternal` |
| `GLOBAL_SEARCH` pinned | LAUNCHED | same |
| `GLOBAL_SEARCH` explicit gateway | LAUNCHED | same |
| `ASSIST` pinned | LAUNCHED | `.GoogleAppImplicitActionAssistGatewayInternal` |
| `WEB_SEARCH` pinned | LAUNCHED | `...google.GoogleSearch` |

All three `GLOBAL_SEARCH` forms converge on the same gateway. Use the pinned form:
implicit `ASSIST` and `WEB_SEARCH` are ambiguous on real devices — Chrome competes for
`WEB_SEARCH`, and on the test emulator `com.anthropic.claude` competed for `ASSIST` and
`VOICE_ASSIST`. Unpinned, the user gets a chooser.

### MIC — both candidates launched

| Candidate | Outcome | Resolved to |
|---|---|---|
| `speech WEB_SEARCH` pinned | LAUNCHED | `...voicesearch.handsfree.HandsFreeActivity` |
| `VOICE_ASSIST` pinned | LAUNCHED | `.GoogleAppVoiceAssistEntrypoint` → `GoogleAppActivity` |

`speech WEB_SEARCH` is the right choice for the mic: it goes to voice *search*.

#### Bonus finding: `VOICE_ASSIST` opens Gemini chat

`VOICE_ASSIST` is not a worse mic candidate — it is the correct intent for a **different
action**. Confirmed on device: it opens **Gemini chat**, not voice search.

```kotlin
Intent("android.intent.action.VOICE_ASSIST").setPackage(GOOGLE_APP)
// → .GoogleAppVoiceAssistEntrypoint → ...googleapp.activity.GoogleAppActivity
```

Verified `LAUNCHED`. Keep this for a future Gemini action on the widget — it is already
proven, so that action needs no further discovery work.

Caveat: `VOICE_ASSIST` is ambiguous when unpinned — any installed assistant app can
claim it (on the test emulator, `com.anthropic.claude` competed for it). Pinning to the
Google app is what makes it land on Gemini specifically.

### LENS — only one candidate actually works

| Candidate | Outcome | What actually happened |
|---|---|---|
| `LensActivity` explicit | **SECURITY_EXCEPTION** | Permission Denial, not exported |
| `VIEW google://lens` (package-pinned) | LAUNCHED | **Silent no-op** — no task created |
| `VIEW googleapp://lens` | LAUNCHED | Silent no-op |
| `VIEW https://lens.google/trylens` | LAUNCHED | **Opened Chrome** on a Lens marketing page |
| `MainActivity` explicit component | LAUNCHED | **Silent no-op** — see correction below |
| `LensExportedActivity` explicit | LAUNCHED | Silent no-op |

#### Correction — Lens does NOT work

An earlier revision of this document claimed the explicit `MainActivity` component opened
the camera. **That was wrong**, and the error is worth understanding.

It rested on the probe's green `LAUNCHED` badge plus a same-moment report that Lens had
worked. But the camera had genuinely opened minutes earlier from a tap on the *real Google
widget*, and that is what was actually observed. `LAUNCHED` was then read as
corroboration. It is the precise trap this document opens by warning about.

Verified afterwards, repeatedly and while warm: `MainActivity` is a **trampoline** that
forwards to `...lens.LensExportedActivity`; both are destroyed within milliseconds and the
foreground never changes. `startActivity` returns cleanly, no exception, no denial —
`result code=0` from our uid. Targeting `LensExportedActivity` directly behaves identically.

No third-party path launches the Lens camera **directly**. The real widget reaches
`...lens.LensActivity`, which is not exported.

#### But Lens IS reachable in one extra tap

The Google search page — which we *can* open, via the `GLOBAL_SEARCH` intent already
chosen for the Search action — carries a Lens camera icon at the top right. Tapping it:

```
START {cmp=.../com.google.android.apps.search.lens.LensActivity (has extras)}
      from uid 10244 (com.google.android.googlequicksearchbox)
```

The Lens camera opens, verified by screenshot: full viewfinder with shutter and the
Pesquisar / Traduzir / Falar / Criar modes.

It is the **same non-exported `LensActivity`** we are denied — launched by the Google app
for itself. That is the whole lesson: the barrier is not the component, it is *who is
asking*. Reaching a surface the Google app owns lets it make the call we cannot.

```
our GLOBAL_SEARCH intent  →  search page  →  one tap on the Lens icon  →  Lens camera
```

Structurally identical to Gemini Live in the pill. Both actions the Google widget appears
to launch "directly" are, for a third-party widget, one intent plus one tap.

#### Best case: with the standalone Lens app installed, Lens is one tap

If `com.google.ar.lens` is present, `getLaunchIntentForPackage` gives
`...LensLauncherActivity`, and launching it from our widget opens the Lens camera —
verified by screenshot from the real home screen widget.

The twist: that launcher forwards to `com.google.android.apps.lens.MainActivity` — the
exact component that is a silent no-op for us. It works because the *Lens app* makes the
call, not us. Third confirmation of the same rule: the component is fine, the caller is
what gets rejected.

So Lens has three tiers, resolved at bind time:

| Condition | Result |
|---|---|
| Lens app installed | camera, one tap |
| not installed, store available | Play Store listing |
| neither | search page, camera one tap later |

#### What the real Google widget does, and why we can't copy it

Captured from logcat by tapping the Lens icon on the actual Google Search widget:

```
START u0 {flg=0x10008000 cmp=com.google.android.googlequicksearchbox/
          com.google.android.apps.search.lens.LensActivity (has extras)}
     from uid 10244 (com.google.android.googlequicksearchbox)
```

`LensActivity` has no intent filter and is **not exported**. Attempting it from our app:

```
Permission Denial: starting Intent { cmp=.../lens.LensActivity }
from ProcessRecord{com.msmobile.gsearch/u0a61} (uid=10061) not exported from uid 10244
```

The widget's `PendingIntent` runs as the Google app itself, which is why it works there
and cannot work for us. We cannot reproduce the Google widget's Lens path exactly.

#### The surprise

`VIEW google://lens` and the explicit `MainActivity` component **resolve to the same
activity**, yet only the explicit component actually opens the camera. The URI form is a
silent no-op. This was retested after Lens was warm, ruling out a cold-start confound.

Consequence: `resolveActivity()` telling you two intents reach the same component does
**not** mean they behave the same.

## Gemini Live and Screen Share — investigated, NOT currently reproducible

Requested as future actions. Captured from the Gemini widget (`com.google.android.apps.bard`,
widget receiver `.widget.RobinToolBarAppWidgetReceiver`). Both buttons follow an identical
three-hop chain:

```
1. bard/.widget.RobinWidgetEntryPointActivity   (dat=glance-action:/... , has extras)
      ↑ fired by the widget, from uid 10392 (bard itself)
2. VIEW https://bard.google.com/...  pkg=googlequicksearchbox
      cmp=...voice.deeplinks.handlers.gateway.impl.MainAssistantDeeplinkAnimated
3. final target, launched from uid 10244 (the Google app itself):
      Live  → ...voice.robin.main.MainActivity
      Share → ...voice.robin.ui.conversationmode.screenshare.activity.ScreenshareStartupActivity
```

**Hop 2 is exported and reachable by us.** `MainAssistantDeeplinkAnimated` registers
`https://bard.google.com/android*` with `BROWSABLE` + `DEFAULT`, auto-verified.

### Measured results (not inferred)

Absence from the resolver table proves only that a component has no *intent filter* — an
activity can still be `exported="true"` with no filter. So each was added to the probe
and launched for real from our app:

| Target | Result |
|---|---|
| `BardEntryPointActivity` (Gemini app) | **LAUNCHED** → lands on `robin.main.MainActivity` |
| `robin.main.MainActivity` (Live) | **SECURITY_EXCEPTION** — not exported from uid 10244 |
| `ScreenshareStartupActivity` (Share) | **SECURITY_EXCEPTION** — not exported from uid 10244 |

**Gemini chat is reachable two ways** — `VOICE_ASSIST` pinned to the Google app, or an
explicit `ComponentName` on `com.google.android.apps.bard/.shellapp.BardEntryPointActivity`.
Both land on the Gemini chat surface (verified visually: greeting plus keyboard, with
Live offered as a button *inside* the input bar).

**Live and Screen Share are not reachable.** Their activities are not exported. The
widget's buttons work only because its `PendingIntent` runs as uid 10392 (the Gemini app)
— tapping the real widget is therefore not evidence that a third-party app can do the
same. Same shape as the Lens result.

Note `BardEntryPointActivity` requires `com.google.android.apps.bard` in `<queries>`;
without it the package is invisible and the launch fails for an unrelated reason.

### Why Live specifically is blocked

The discriminating part of the hop-2 URL cannot be recovered. Android logs intent data
through `Uri.toSafeString()`, which deliberately strips path and query — hence the
permanent `https://bard.google.com/...` in both logcat and dumpsys. The path is not
retrievable by observation, from any tool.

Probing the visible prefix does not substitute for it. These were tried and all three
landed in the **Play Store** (`com.android.vending`), meaning the gateway forwards
unrecognised `/android` subpaths to the app listing:

- `https://bard.google.com/android`
- `https://bard.google.com/android/live`
- `https://bard.google.com/android?mode=live`

The real link presumably carries specific query parameters, plus extras. That search
space is unbounded by guessing.

### The one remaining route

Extract the exact URI and extras by decompiling `com.google.android.apps.bard` and
reading how the widget builds its `PendingIntent`. That is a separate, larger task and
was not attempted.

### A floating pill is reachable — but it is Gemini *chat*, not Live

```kotlin
Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP)
// → ...voicesearch.handsfree.HandsFreeActivity
// → ...voice.robin.ui.floaty.activity.FloatyActivity
```

`FloatyActivity` is **Gemini chat in compact overlay form**. It sits on top of the current
screen instead of taking it over, and shows "Peça ao Gemini" with mic, Live, and
"Compartilhar tela com o Live" as buttons.

**It is not Gemini Live** — the pill opens on chat. But the pill *hosts* Live too, and
that is the key result:

#### Why no intent for Live-in-pill exists, and why that is fine

Tapping Live inside the pill was captured with logcat armed. The result:

```
--- START INTENTS ---
(nothing)
top activity: ...robin.ui.floaty.activity.FloatyActivity   (unchanged)
```

**No activity is launched at all.** Live is an in-process UI state change *within*
`FloatyActivity`. There is no intent to copy, which is why no amount of component hunting
was ever going to find one — the thing being looked for does not exist.

Confirmed visually: Live runs in the pill, greeting "Olá." with the full Live control bar
(camera, screen share, orb, mic, close), overlaid on the caller's screen.

So the requested state — **Gemini Live in the floating pill** — is reachable as:

```
our single intent  →  pill (chat)  →  one tap on Live  →  Live in pill
```

The widget's own Live button takes a different route, launching the non-exported
`robin.main.MainActivity` as a **full-screen** surface — verified by screenshot, not
inferred from the class name: black background, hamburger and overflow menus, the Live
control bar spanning the full screen. The three-hop chain reproduced identically on a
second capture, so it is stable.

That is a different surface from the pill, and the one closed to us. Little is lost:
full-screen Live *replaces* whatever the user was doing, while the pill overlays it. For a
search widget the pill is arguably the better behaviour, so the reachable surface is also
the more suitable one.

Two corrections are folded in here, both the same mistake made twice:

1. This document earlier called the pill outright "not feasible". Wrong — the reasoning
   ran ahead of the measurement. Only Gemini-*named* components were examined; all were
   non-exported, so the state was declared unreachable. The pill is in fact reached via an
   intent with nothing Gemini-like in its name.
2. The correction then overshot, describing `FloatyActivity` as satisfying the
   Live-minimized request. It does not. A pill was found and assumed to be *the* pill,
   without checking which surface it hosted.

Status: **Gemini chat — solved, in pill form. Gemini Live in the pill — reachable in one
extra tap; no direct intent exists because it is not an activity launch. Full-screen Live
(the widget's route) — closed, non-exported.**

## Risks to carry into the widget

> Updated 2026-08-07: risk 1 is handled and the open question at the end is now partly
> answered — see the notes inline. The rest still stand.

1. **`com.google.android.apps.lens.MainActivity` is an internal class name.** It is
   exported and works today, but it is not public API and can change with any Google app
   update. The widget needs a graceful fallback — most sensibly, fall back to the search
   gateway rather than leaving a dead button.

   **Handled.** `SearchIntents.lens()` prefers the standalone Lens app, falls back to the
   Play Store listing, then to a web listing, then to the search gateway. Verified on an
   emulator without Lens installed: the button opens the Play Store.
2. **Untested: whether the `google://lens` data URI is required** alongside the explicit
   component, or whether the component alone suffices. The verified-working intent sets
   both.
3. **Single-device result.** All of this is one Galaxy S23 with one Google app version.
   The emulator showed a different component set for `ASSIST`, so variation across
   devices and versions is real.
4. **Package visibility is mandatory.** Under `targetSdk` 37, without the `<queries>`
   block every probe falsely reports "does not resolve".

## Out of scope, still open

Behaviour when the Google app is absent or disabled. Every winning intent targets
`com.google.android.googlequicksearchbox` explicitly, so all three actions will fail on
such a device. The widget needs a decision here before shipping.

**Partly answered, 2026-08-07.** `WidgetActionActivity` — the trampoline every widget
button now goes through — catches `ActivityNotFoundException` and shows a toast, so a
button on such a device explains itself instead of looking broken. What is still undecided
is the product question: whether the settings screen should hide actions that cannot work,
and whether anything should stand in for them.

## Since these findings

Two things here have moved, and both are worth knowing before trusting a detail above:

- **The widget no longer launches these intents directly.** Clicks go through
  `WidgetActionActivity`, which resolves the action and then builds the intent from
  `SearchIntents` exactly as documented here. The intents themselves are unchanged; only
  who calls `startActivity` moved. Glance forced this — see
  `2026-08-07-glance-migration.md`.
- **Gemini shipped as a widget action**, which this document filed as a future
  possibility. It opens whatever assistant the device has configured.

Everything about *which* intent reaches *which* surface, and about caller identity being
the real barrier, still holds.
