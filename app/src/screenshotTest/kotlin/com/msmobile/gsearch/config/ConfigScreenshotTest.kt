package com.msmobile.gsearch.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import com.msmobile.gsearch.utils.PreviewPhone

/**
 * Pins every state in [ConfigPreviewConfigProvider] to a reference image.
 *
 * [com.msmobile.gsearch.utils.PreviewPhone] is repeated here rather than inherited from [ConfigScreenPreview]: the
 * plugin discovers previews by the `@Preview` annotations on the function it is about to
 * render, and this wrapper is that function — without one it is not a preview at all and
 * the test silently contributes no images.
 *
 * Regenerate with `./gradlew updateDebugScreenshotTest` after an intended UI change, and
 * look at the diff in `app/src/screenshotTestDebug/reference` before committing — an
 * updated reference image is only correct if someone actually looked at it.
 */
class ConfigScreenshotTest {

    @PreviewTest
    @PreviewPhone
    @Composable
    internal fun ConfigScreenPreviewTest(
        @PreviewParameter(ConfigPreviewConfigProvider::class) config: ConfigPreviewConfig,
    ) {
        ConfigScreenPreview(config)
    }
}
