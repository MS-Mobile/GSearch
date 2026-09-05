package com.msmobile.gsearch.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import com.msmobile.gsearch.utils.PreviewPhone
import com.msmobile.gsearch.widget.GSearchBarPreview

class GSearchBarScreenshotTest {
    @PreviewTest
    @PreviewPhone
    @Composable
    internal fun GSearchBarPreviewTest(
        @PreviewParameter(GSearchBarPreviewConfigProvider::class) config: GSearchBarPreviewConfig,
    ) {
        GSearchBarPreview(config)
    }
}
