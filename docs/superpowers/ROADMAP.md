# GSearch — Roadmap

Current state: working home screen widget, built on Jetpack Glance, with a settings screen.
Four verified actions (search, mic, Lens, Gemini), user-chosen set and order, adjustable
opacity, reachable from the launcher icon and from the widget's long-press menu.

Verified intent details and everything that failed:
`docs/superpowers/findings/2026-08-06-search-intents.md`

What the Glance migration broke and how each was found:
`docs/superpowers/findings/2026-08-07-glance-migration.md`

## Phase 1 — Configuration UI (done)

- `PocActivity` and `IntentProbe` deleted; `intents/SearchIntents.kt` was the part that
  shipped and already lived outside that package.
- `config/ConfigActivity` — Compose, Material 3 — replaces the POC screen as the launcher
  entry point. Every edit saves immediately and pushes to the widget, so there is no save
  button and no way to leave holding unsaved changes.
- Buttons are chosen with switches and reordered by dragging a handle. The last enabled
  action cannot be switched off, since an empty pill has nothing to tap and no way back.
- Opacity is a slider, applied to the pill only — the icons keep full strength at every
  setting.
- Long-press → **Change widget settings** works, via `android:configure` plus
  `widgetFeatures="reconfigurable|configuration_optional"`. The activity hands back
  `EXTRA_APPWIDGET_ID` in `onCreate` rather than on the way out, so backing out of the
  configure flow keeps the widget instead of silently deleting it.
- **Add to home screen** in the settings screen uses `requestPinAppWidget`, hidden on
  launchers that do not support it.
- The pill adapts to the button count on its own: slots take width from layout weight, so
  three icons take 266px each and four take 200px.

`WidgetSlots` became `WidgetConfig`, holding an ordered action list plus opacity.

**Configuration is global, not per widget id.** The open question in the previous roadmap is
settled that way because the settings screen is reachable from the launcher icon, where
there is no widget id to edit. Supporting both would have meant a global default plus
per-instance overrides, and a user who configured one widget and then opened the app would
have been silently editing something else. Moving back to per-instance is a change to the
preference key and the call sites that pass an id.

## Phase 2 — Glance (done)

`GSearchWidgetProvider` kept its class name and became a `GlanceAppWidgetReceiver`, so the
widget already on the home screen survived the swap — same instance, no re-add — and
measured identically afterwards. The manifest entry did not change.

The slot list is now a real list rather than four pre-declared views with visibility
toggling. `MAX_ACTIONS` is kept anyway: a widget cannot grow its own cell, and past four
icons they just crowd.

Three Glance regressions had to be worked around — a rewritten intent data URI that broke
implicit intents, a colour filter that cannot express alpha, and configuration reads that
never triggered recomposition. All three are written up in the findings doc with the
evidence; do not "simplify" any of them back without reading it.

The RemoteViews layout is still used as `initialLayout` and `previewLayout`, so it cannot
be deleted, which means its dimens and the dp constants in `GSearchGlanceWidget` have to be
kept in step by hand.

## Also outstanding

- **Branch name.** `poc/search-intents` now holds the whole app; rename before merging.
- **Icon contrast at low opacity.** With the pill nearly transparent, the light-mode icon
  tint (`#5F6368`) over a dark wallpaper is hard to read. Alpha is behaving correctly — the
  icons are at full strength — but the colour is wrong for that case. Worth considering an
  icon-colour or shadow option, or tinting from the wallpaper.
- **Density is launcher-specific and cannot be right everywhere in dp.** Measured: One UI
  0.833, Pixel Launcher 1.0. Fixing it properly means sizing in pixels the app computes
  itself, which needs `RemoteViews.setViewLayoutHeight` (API 31+); `setViewPadding` already
  takes pixels at any API level. Glance would need its own approach again.
- **Transparency does nothing below API 31**, by choice — see the findings doc. Raising
  `minSdk` to 31 would remove the fallback branch and the caveat with it.
- **Google app absent or disabled.** Every action targets
  `com.google.android.googlequicksearchbox`. The trampoline now catches
  `ActivityNotFoundException` and shows a toast rather than looking like a dead button, but
  the settings screen still offers actions that cannot work on such a device.
- **Gemini Live and Screen Share** cannot be launched directly. The Gemini chat pill puts
  Live one tap away; see the intent findings doc for why no intent exists.
- **No automated tests.** Skipped deliberately for the POC. The config screen's reorder
  logic and `WidgetConfig`'s parsing are the parts worth covering first.
