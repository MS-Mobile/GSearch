import com.android.build.api.dsl.VariantDimension
import com.project.starter.easylauncher.filter.ColorRibbonFilter
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support: it registers the `kotlin` extension itself, so
    // applying org.jetbrains.kotlin.android on top fails with a duplicate-extension error.
    // The Compose compiler plugin is the exception — it adds no `kotlin` extension, so it
    // applies cleanly on top of built-in Kotlin. Its version must track the Kotlin the build
    // runs on — see libs.versions.kotlin and the note on the root `kotlin.android` alias.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.easylauncher)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom(files("src/main/java"))
}

// The compose-rules ruleset is deliberately NOT registered here. Spotless already runs it
// through ktlint, and detekt's copy of it would report every Compose finding a second time.

android {
    namespace = "com.msmobile.gsearch"
    compileSdk {
        version = release(libs.versions.android.compile.sdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.msmobile.gsearch"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = System.getenv(EnvKeys.VERSION_CODE)?.toIntOrNull() ?: 1
        versionName = requireVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv(EnvKeys.KEYSTORE_FILE)
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv(EnvKeys.KEYSTORE_PASSWORD)
                keyAlias = System.getenv(EnvKeys.KEYSTORE_ALIAS)
                keyPassword = System.getenv(EnvKeys.KEYSTORE_PASSWORD)
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDefault = true
            escapedBuildConfigField(
                EnvKeys.ENCRYPTION_PASSPHRASE,
                envVariableOrDefault(EnvKeys.ENCRYPTION_PASSPHRASE),
            )
            escapedBuildConfigField(
                EnvKeys.SENTRY_DSN,
                envVariableOrDefault(EnvKeys.SENTRY_DSN),
            )
        }
        release {
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            escapedBuildConfigField(
                EnvKeys.ENCRYPTION_PASSPHRASE,
                requireEnvVariable(EnvKeys.ENCRYPTION_PASSPHRASE),
            )
            escapedBuildConfigField(
                EnvKeys.SENTRY_DSN,
                requireEnvVariable(EnvKeys.SENTRY_DSN),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // Lint is only worth having if it can fail the build; left advisory its warnings
        // accumulate until nobody reads the report.
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true

        // The widget metadata deliberately declares attributes newer than minSdk 24 —
        // previewLayout and targetCell* (31) and widgetFeatures (28). Older launchers ignore
        // them and fall back to minWidth/minHeight, which is the whole point; raising minSdk
        // to silence this would drop devices for a cosmetic gain.
        disable += "UnusedAttribute"

        // Reported against widget_search_bar.xml, the RemoteViews layout. The nesting there
        // may well be load-bearing for how the pill measures on One UI, and that can only be
        // settled on a device — kept visible in the report rather than fixed blind or hidden.
        informational += "UselessParent"

        // "A newer version is available" must never fail a build: it turns an unrelated
        // upstream release into a red CI run with no code change behind it. Both stay in the
        // report.
        informational += "GradleDependency"
        informational += "NewerVersionAvailable"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Still gated behind an experimental flag by AGP 9.3, even though the screenshot plugin
    // is applied above; without it the `screenshotTest` source set is never created and the
    // tests below compile into nothing.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

easylauncher {
    buildTypes {
        create("debug") {
            filters(
                customRibbon(
                    gravity = ColorRibbonFilter.Gravity.BOTTOM,
                    label = "DEV"
                )
            )

        }
        create("release") {
            enable(false)
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // The rendering side (layoutlib, the preview harness) is pulled in by the plugin; these
    // two are what the test sources themselves compile against — the @PreviewTest annotation
    // and the @Preview/@PreviewParameter ones the previews under src/main are written with.
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

private object EnvKeys {
    const val VERSION_CODE = "VERSION_CODE"
    const val KEYSTORE_FILE = "KEYSTORE_FILE"
    const val KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD"
    const val KEYSTORE_ALIAS = "KEYSTORE_ALIAS"
    const val ENCRYPTION_PASSPHRASE = "ENCRYPTION_PASSPHRASE"
    const val SENTRY_DSN = "SENTRY_DSN"
    const val SENTRY_ORG = "SENTRY_ORG"
    const val SENTRY_PROJECT = "SENTRY_PROJECT"
    const val SENTRY_AUTH_TOKEN = "SENTRY_AUTH_TOKEN"
}

private fun VariantDimension.escapedBuildConfigField(key: String, value: String) {
    buildConfigField("String", key, "\"$value\"")
}

private fun envVariableOrDefault(key: String): String {
    return System.getenv(key) ?: ""
}

private fun requireEnvVariable(key: String): String {
    return System.getenv(key) ?: error("$key environment variable is required for release builds")
}

private fun requireVersionName(): String {
    val versionPropsFile = file("${rootProject.projectDir}/version.properties")

    if (!versionPropsFile.exists()) {
        error("version.properties file not found at ${versionPropsFile.absolutePath}")
    }

    val versionProps = Properties().apply {
        versionPropsFile.inputStream().use { load(it) }
    }
    val versionName = versionProps.getProperty("versionName")
        ?: error("versionName property not found in version.properties")

    return versionName
}
