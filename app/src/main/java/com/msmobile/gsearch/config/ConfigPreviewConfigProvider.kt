package com.msmobile.gsearch.config

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.msmobile.gsearch.widget.WidgetAction
import com.msmobile.gsearch.widget.WidgetConfig

/**
 * One rendering of [ConfigScreen], named so the reference image it produces can be told
 * apart from the others.
 *
 * The fields mirror [ConfigScreen]'s own initial-state parameters rather than a ui-state
 * object, because the screen holds its state itself — there is no view model to hand it a
 * snapshot of.
 */
@VisibleForTesting
internal data class ConfigPreviewConfig(
    val configName: String,
    val actions: List<WidgetAction>,
    val opacityPercent: Int,
    val canPinWidget: Boolean = true,
)

/**
 * The states of the config screen worth pinning to a reference image.
 *
 * Chosen for the states that are reachable but awkward to get to by hand, and where the
 * rendering is the thing that could regress: the two ends of the opacity slider, the
 * boundary cases of the action switches, and the launcher that cannot pin a widget. States
 * that differ only in which action sits in which row are deliberately not enumerated —
 * reordering is exercised by the drag tests, and each extra entry here is two more images
 * to regenerate on any UI change.
 */
@VisibleForTesting
internal class ConfigPreviewConfigProvider : PreviewParameterProvider<ConfigPreviewConfig> {

    override val values: Sequence<ConfigPreviewConfig> = sequenceOf(
        ConfigPreviewConfig(
            configName = "Default",
            actions = WidgetConfig.DEFAULT_ACTIONS,
            opacityPercent = WidgetConfig.DEFAULT_OPACITY_PERCENT,
        ),
        // Every switch on, which is also the state where the remaining off switches go
        // disabled — nothing more can be enabled at MAX_ACTIONS.
        ConfigPreviewConfig(
            configName = "All Actions",
            actions = WidgetAction.entries.toList(),
            opacityPercent = WidgetConfig.MAX_OPACITY_PERCENT,
        ),
        // The opposite boundary: one action left, so its switch is the disabled one. The
        // pill is also at its widest per icon here.
        ConfigPreviewConfig(
            configName = "Single Action",
            actions = listOf(WidgetAction.SEARCH),
            opacityPercent = WidgetConfig.MAX_OPACITY_PERCENT,
        ),
        // A fully transparent pill still has to show its icons. This is the case the
        // gradient wash behind the preview exists for, so it is worth a reference image.
        ConfigPreviewConfig(
            configName = "Transparent",
            actions = WidgetConfig.DEFAULT_ACTIONS,
            opacityPercent = 0,
        ),
        // Launchers that cannot pin a widget lose the "add to home screen" button, which
        // moves everything below it.
        ConfigPreviewConfig(
            configName = "Cannot Pin Widget",
            actions = WidgetConfig.DEFAULT_ACTIONS,
            opacityPercent = WidgetConfig.DEFAULT_OPACITY_PERCENT,
            canPinWidget = false,
        ),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).configName
}
