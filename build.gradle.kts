// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Load-bearing despite `apply false` and no module applying it: this is what puts the
    // Kotlin Gradle plugin on the buildscript classpath at libs.versions.kotlin, outranking
    // the 2.2.10 AGP brings, so built-in Kotlin runs on that version. Delete this line and
    // the build silently drops to Kotlin 2.2.10 and the release build breaks.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.spotless)
}

// Formatting lives at the root so one `spotlessApply` covers the app sources and the build
// scripts alike. ktlint's own rules come from .editorconfig, not from here.
// Targets name source directories rather than `**/*.kt` plus a build/ exclusion. The broad
// glob makes Spotless walk app/build, which both wastes time and races `clean` deleting
// those paths mid-scan — `./gradlew clean check` failed on unreadable intermediates.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf(libs.compose.rules.ktlint.get().toString()))
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
