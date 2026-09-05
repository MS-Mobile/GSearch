---
name: appfunctions
description: Use when Gemini is not seeing a registered AppFunction or is passing unexpected parameter values, and when adding, modifying or ADB-verifying Android AppFunctions (Gemini on-device integration) in GSearch
---

# AppFunctions — what the code can't tell you

GSearch exposes on-device tools to Gemini via `androidx.appfunctions` (alpha). Adding a function is
just a new `@AppFunction suspend` method on `VisitAppFunctions` — KSP generates the schema and
`GSearchApp` provides the instance. The wiring is visible in those two files and in
`AndroidManifest.xml`; read them first.

What follows was established by experiment against a real device and is recorded nowhere in the
source.

## How Gemini actually calls these

- **Parameter values arrive in the vocabulary you documented in KDoc — English tokens — regardless
  of the user's spoken language.** A Portuguese prompt still yields `dateFilter="today"`. Do not add
  localised token handling.
- **Free-form `String` parameters are not validated by the framework.** Harden the implementation:
  handle `null` as the intended default, match the documented tokens, and throw
  `AppFunctionInvalidArgumentException` on anything else. A silent `else` fallback returns
  plausible-but-wrong data; throwing is what makes Gemini retry with a documented value.
- **A Kotlin `enum class` as a parameter type was never confirmed to work in this alpha.** Primitives,
  `@AppFunctionSerializable` data classes and `List<T>` do. Validate tokens in code rather than
  relying on an enum parameter.

## Status: blocked on EAP, not on code

The integration is wired and verified end-to-end on a Galaxy S23 (Android 16 / SDK 36) through the OS
`AppFunctionManagerService`. The **real Gemini agent path is EAP-gated**: the OS only indexes
AppFunctions from a pre-approved package set (Samsung/Google first-party). This is enforced at
OS registry-indexing level — it is *not* about debug vs release, signing, or anything in this repo.
An EAP form was submitted for `com.msmobile.gsearch`. Re-verify this gate before concluding that a
code change is needed; if Gemini can't see the functions, this is almost certainly why.

## Verifying via ADB (needs an Android 16 device)

```bash
# List registration
adb shell cmd app_function list-app-functions | grep -A30 gsearch

# Execute — the closest thing to Gemini. Wrap the whole command in double quotes to keep JSON quotes.
adb shell "cmd app_function execute-app-function --package com.msmobile.gsearch.debug --function 'com.msmobile.gsearch.appfunctions.VisitAppFunctions#listVisits' --parameters '{\"dateFilter\":\"today\"}' --brief-yaml"
```

Two caveats that will otherwise waste an afternoon:

- `execute-app-function` bypasses normal indexing using elevated shell permissions. It proves your
  function works; it does **not** prove Gemini can reach it (see the EAP gate).
- The debug build is `com.msmobile.gsearch.debug` with its **own empty database** — it returns
  `count:0` until you add visits *in that installed app*. Two package IDs mean two OS registries.

`:app:installDebug` needs a `VERSION_CODE` env var set (any value, e.g. `VERSION_CODE=1`).
