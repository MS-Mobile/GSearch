package com.msmobile.gsearch.utils

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * The device and colour scheme every screenshot-tested preview is pinned to.
 *
 * Both `uiMode` variants are listed because [com.msmobile.gsearch.config.GSearchTheme] picks its colour scheme from
 * `isSystemInDarkTheme()`, so light and dark are genuinely different renderings rather than
 * the same tree in different colours — and the config screen is the one place the widget's
 * fixed palette meets Material's theming, which is where a dark-mode regression would show.
 *
 * A single device on purpose. Every entry here multiplies the reference images that have to
 * be regenerated and reviewed on any UI change, and this screen is a single scrolling column
 * with nothing width-dependent in it, so a second form factor would cost that upkeep to
 * assert the same layout twice.
 */
@Preview(
    name = "Phone",
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Phone",
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class PreviewPhone
