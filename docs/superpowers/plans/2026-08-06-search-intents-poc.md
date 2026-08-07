# Search Intents POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a throwaway Activity that probes candidate intents for Google search, voice search, and the Google app's built-in Lens, reporting which ones actually launch.

> **Status: historical — executed 2026-08-06, then superseded.** Two of the three files
> below no longer exist; they were discovery scaffolding and were deleted once the intents
> were known. Do not execute this plan. It is kept as the record of how the intent work was
> sequenced. For the current shape of the app, read `docs/superpowers/ROADMAP.md`.

**Architecture:** Three small Kotlin files. `SearchIntents` builds candidate `Intent`s as pure data. `IntentProbe` attempts a launch and classifies the outcome. `PocActivity` renders tappable rows and shows results. Only `SearchIntents` survives into the real widget.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.1, Gradle 9.5.0, KSP 2.3.11 (declared, not applied), AppCompat, minSdk 24 / targetSdk 37.

**Spec:** `docs/superpowers/specs/2026-08-06-search-intents-poc-design.md`

---

## Pre-verified candidate data

Queried on the dev emulator with `adb shell cmd package query-activities`. These are
facts, not guesses, and they shaped the candidate list:

| Intent | Resolves to |
|---|---|
| `android.search.action.GLOBAL_SEARCH` | `googlequicksearchbox/.GoogleAppGlobalSearchImplicitGatewayInternal` (sole match) |
| `android.intent.action.ASSIST` | Google app gateway **+ `com.anthropic.claude`** — ambiguous |
| `android.intent.action.WEB_SEARCH` | Chrome **+** `googlequicksearchbox/...GoogleSearch` — ambiguous |
| `android.speech.action.WEB_SEARCH` | `googlequicksearchbox/...handsfree.HandsFreeActivity` (sole match) |
| `android.intent.action.VOICE_ASSIST` | Google app **+ `com.anthropic.claude`** — ambiguous |
| `android.speech.action.RECOGNIZE_SPEECH` | `com.google.android.tts/...GoogleTTSActivity` — wrong target, dropped |
| `VIEW google://lens` | `googlequicksearchbox/...apps.lens.MainActivity` |
| `VIEW googleapp://lens` | `googlequicksearchbox/...lens.deeplink.LensDeeplink` |
| `VIEW https://lens.google/trylens` | `LensDeeplink` + browsers — ambiguous |
| `VIEW lens://` | nothing |

Two consequences baked into the plan:

1. **Ambiguous intents get `setPackage(...)`** pinning them to the Google app, otherwise
   the user sees a chooser (and on this emulator, Claude competes for `ASSIST`/`VOICE_ASSIST`).
2. `RECOGNIZE_SPEECH` is dropped — it returns a transcription result to the caller
   rather than performing a search.

## Critical detail: package visibility

`targetSdk` is 37, so Android 11+ package-visibility filtering applies.
`resolveActivity()` returns `null` for other packages unless we declare `<queries>`.
Without it, every probe reports "does not resolve" and the POC teaches us nothing.
The manifest must declare the Google app package and the probed intent shapes.

## File Structure

- Create: `app/src/main/java/com/msmobile/gsearch/poc/SearchIntents.kt` — candidate lists, pure data
- Create: `app/src/main/java/com/msmobile/gsearch/poc/IntentProbe.kt` — resolve + launch + classify
- Create: `app/src/main/java/com/msmobile/gsearch/poc/PocActivity.kt` — throwaway UI
- Modify: `gradle/libs.versions.toml` — Kotlin + KSP
- Modify: `app/build.gradle.kts` — apply Kotlin plugin
- Modify: `app/src/main/AndroidManifest.xml` — `<queries>` + launcher activity

---

### Task 1: Add Kotlin to the build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and plugin aliases**

In `[versions]`:

```toml
kotlin = "2.4.10"
ksp = "2.3.11"
```

In `[plugins]`:

```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

KSP is declared but deliberately **not applied** — nothing in this POC does annotation
processing, and applying it would add build time for no benefit.

- [ ] **Step 2: Apply the Kotlin plugin**

In `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
```

And inside `android { }`, alongside the existing `compileOptions`:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
```

- [ ] **Step 3: Verify the build configures**

Run: `./gradlew :app:tasks --offline -q` (or `assembleDebug`)
Expected: configuration succeeds, no "plugin not found" error.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Kotlin 2.4.10 and declare KSP"
```

---

### Task 2: Candidate intents

**Files:**
- Create: `app/src/main/java/com/msmobile/gsearch/poc/SearchIntents.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.msmobile.gsearch.poc

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent

private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

data class Candidate(val label: String, val intent: Intent)

data class Section(val name: String, val candidates: List<Candidate>)

object SearchIntents {

    fun all(): List<Section> = listOf(
        Section("SEARCH (magnifier)", search()),
        Section("MIC (voice)", voice()),
        Section("LENS (in Google app)", lens()),
    )

    private fun search(): List<Candidate> = listOf(
        Candidate(
            "GLOBAL_SEARCH (implicit)",
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
        ),
        Candidate(
            "GLOBAL_SEARCH pinned to Google app",
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(GOOGLE_APP),
        ),
        Candidate(
            "GLOBAL_SEARCH explicit gateway component",
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setComponent(
                ComponentName(
                    GOOGLE_APP,
                    "$GOOGLE_APP.GoogleAppGlobalSearchImplicitGatewayInternal",
                ),
            ),
        ),
        Candidate(
            "ASSIST pinned to Google app",
            Intent(Intent.ACTION_ASSIST).setPackage(GOOGLE_APP),
        ),
        Candidate(
            "WEB_SEARCH pinned to Google app",
            Intent(Intent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP),
        ),
    )

    private fun voice(): List<Candidate> = listOf(
        Candidate(
            "speech WEB_SEARCH (implicit)",
            Intent(RecognizerIntent.ACTION_WEB_SEARCH),
        ),
        Candidate(
            "speech WEB_SEARCH pinned to Google app",
            Intent(RecognizerIntent.ACTION_WEB_SEARCH).setPackage(GOOGLE_APP),
        ),
        Candidate(
            "VOICE_ASSIST pinned to Google app",
            Intent("android.intent.action.VOICE_ASSIST").setPackage(GOOGLE_APP),
        ),
    )

    private fun lens(): List<Candidate> = listOf(
        Candidate(
            "VIEW google://lens",
            Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).setPackage(GOOGLE_APP),
        ),
        Candidate(
            "VIEW googleapp://lens",
            Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GOOGLE_APP),
        ),
        Candidate(
            "VIEW https://lens.google/trylens",
            Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google/trylens"))
                .setPackage(GOOGLE_APP),
        ),
        Candidate(
            "Lens MainActivity explicit",
            Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).setComponent(
                ComponentName(GOOGLE_APP, "com.google.android.apps.lens.MainActivity"),
            ),
        ),
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/msmobile/gsearch/poc/SearchIntents.kt
git commit -m "poc: add candidate search/voice/lens intents"
```

---

### Task 3: Intent probe

**Files:**
- Create: `app/src/main/java/com/msmobile/gsearch/poc/IntentProbe.kt`

- [ ] **Step 1: Write the file**

`resolve()` and `launch()` are separate because a candidate can resolve and still fail
to launch. Both facts are reported.

```kotlin
package com.msmobile.gsearch.poc

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class ProbeResult(val resolvedTo: String?, val outcome: String)

object IntentProbe {

    fun resolve(context: Context, intent: Intent): String? =
        context.packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.let { "${it.packageName}/${it.name}" }

    fun launch(context: Context, intent: Intent): ProbeResult {
        val resolvedTo = resolve(context, intent)
        val toLaunch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(toLaunch)
            ProbeResult(resolvedTo, "LAUNCHED")
        } catch (e: ActivityNotFoundException) {
            ProbeResult(resolvedTo, "ACTIVITY_NOT_FOUND")
        } catch (e: SecurityException) {
            ProbeResult(resolvedTo, "SECURITY_EXCEPTION: ${e.message}")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/msmobile/gsearch/poc/IntentProbe.kt
git commit -m "poc: add intent probe with launch outcome classification"
```

---

### Task 4: POC Activity and manifest

**Files:**
- Create: `app/src/main/java/com/msmobile/gsearch/poc/PocActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add `<queries>` and the launcher activity**

Without `<queries>`, every probe falsely reports "no resolve" under targetSdk 37.

```xml
<queries>
    <package android:name="com.google.android.googlequicksearchbox" />
    <intent><action android:name="android.search.action.GLOBAL_SEARCH" /></intent>
    <intent><action android:name="android.intent.action.ASSIST" /></intent>
    <intent><action android:name="android.intent.action.WEB_SEARCH" /></intent>
    <intent><action android:name="android.speech.action.WEB_SEARCH" /></intent>
    <intent><action android:name="android.intent.action.VOICE_ASSIST" /></intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="google" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="googleapp" />
    </intent>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="https" />
    </intent>
</queries>
```

Inside `<application>`:

```xml
<activity
    android:name=".poc.PocActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Write the Activity**

UI is built in code — no XML layout, since none of it survives the POC.

```kotlin
package com.msmobile.gsearch.poc

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PocActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        root.addView(text("Tap a candidate to attempt a real launch.", 13f, Color.GRAY))
        root.addView(
            text(
                "Resolving is not proof — a non-exported target resolves, then throws " +
                    "SecurityException on launch.",
                13f,
                Color.GRAY,
            ),
        )

        SearchIntents.all().forEach { section ->
            root.addView(
                text(section.name, 18f, Color.WHITE).apply {
                    setPadding(0, dp(24), 0, dp(8))
                },
            )
            section.candidates.forEach { root.addView(row(it)) }
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun row(candidate: Candidate): LinearLayout {
        val status = text("", 12f, Color.GRAY)
        status.text = IntentProbe.resolve(this, candidate.intent)
            ?.let { "resolves → $it" }
            ?: "does not resolve"

        val button = Button(this).apply {
            text = candidate.label
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                val result = IntentProbe.launch(this@PocActivity, candidate.intent)
                status.text = buildString {
                    append(result.outcome)
                    result.resolvedTo?.let { append("\n→ ").append(it) }
                }
                status.setTextColor(
                    if (result.outcome == "LAUNCHED") Color.parseColor("#4CAF50")
                    else Color.parseColor("#FF5252"),
                )
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(12))
            addView(button)
            addView(status)
        }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
```

- [ ] **Step 3: Build and install**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, APK installed on the running emulator.

- [ ] **Step 4: Launch and verify**

Run: `adb shell am start -n com.msmobile.gsearch/.poc.PocActivity`
Expected: the POC screen appears listing three sections. Rows show
`resolves → <component>` rather than "does not resolve" — if everything says
"does not resolve", the `<queries>` block is wrong.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/msmobile/gsearch/poc/PocActivity.kt app/src/main/AndroidManifest.xml
git commit -m "poc: add probe activity and package visibility queries"
```

---

### Task 5: Record findings

**Files:**
- Create: `docs/superpowers/findings/2026-08-06-search-intents.md`

- [ ] **Step 1: Tap every candidate on the emulator and record outcomes**

For each of the three sections, note per candidate: the outcome
(`LAUNCHED` / `ACTIVITY_NOT_FOUND` / `SECURITY_EXCEPTION`), the resolved component,
and — for launches — what actually appeared on screen. A candidate that launches the
wrong screen (e.g. search results instead of the search input) is a failure for our
purposes and must be recorded as such.

- [ ] **Step 2: Write the findings note with a "winner" line per action**

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/findings/2026-08-06-search-intents.md
git commit -m "docs: record search intent probe findings"
```
