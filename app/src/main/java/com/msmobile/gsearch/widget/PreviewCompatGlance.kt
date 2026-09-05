package com.msmobile.gsearch.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Box as ComposeBox
import androidx.compose.foundation.layout.Row as ComposeRow
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.platform.LocalContext as ComposeLocalContext
import androidx.glance.Image as GlanceImage
import androidx.glance.LocalContext as GlanceLocalContext
import androidx.glance.layout.Box as GlanceBox
import androidx.glance.layout.Row as GlanceRow

/**
 * Drop-in replacements for the Glance elements the search bar is built from, invoked with the
 * same call syntax via `invoke`.
 *
 * In production they delegate to the real Glance elements, so the widget is unchanged. Under
 * [LocalInspectionMode] they draw the equivalent Compose UI element instead, because the real
 * ones cannot be drawn there at all: Glance composes with its own applier and emits
 * `Emittable` nodes, while a preview — and so the screenshot test that renders it — composes
 * with the Compose UI applier and can only build `LayoutNode`s. A Glance tree under a preview
 * does not render wrongly, it fails outright, on `androidx.glance.LocalContext` having no
 * value before the applier even gets a chance to reject the node.
 *
 * The pairing is what makes this worth having rather than a second hand-written Compose copy
 * of the bar: one tree, one set of layout decisions, and the preview cannot silently stop
 * matching the widget. What it does not do is prove the *Glance* tree renders — the reference
 * images pin the Compose stand-in. Anything Glance alone decides (how it maps a node to
 * `RemoteViews`, what a launcher does with it) is still only covered on a device.
 */
internal object PreviewCompatGlanceBox {

    @Composable
    operator fun invoke(
        modifier: PreviewCompatGlanceModifier = PreviewCompatGlanceModifier,
        contentAlignment: Alignment = Alignment.TopStart,
        content: @Composable () -> Unit,
    ) = currentRenderer().Box(modifier, contentAlignment, content)
}

/**
 * As [PreviewCompatGlanceBox], for [GlanceRow].
 *
 * The content lambda has no receiver, unlike both frameworks' rows: the one thing a row scope
 * offers here is `defaultWeight`, and [PreviewCompatGlanceModifier] picks that up from
 * [LocalGlanceRowScope] instead so the call site never names one framework's scope type.
 */
internal object PreviewCompatGlanceRow {

    @Composable
    operator fun invoke(
        modifier: PreviewCompatGlanceModifier = PreviewCompatGlanceModifier,
        verticalAlignment: Alignment.Vertical = Alignment.Vertical.Top,
        content: @Composable () -> Unit,
    ) = currentRenderer().Row(modifier, verticalAlignment, content)
}

/**
 * As [PreviewCompatGlanceBox], for [GlanceImage].
 *
 * Takes a drawable id rather than an [ImageProvider] because that is the only source the
 * widget uses and the only one a preview could resolve — a bitmap or an [android.graphics.drawable.Icon]
 * has no Compose painter to fall back to.
 */
internal object PreviewCompatGlanceImage {

    @Composable
    operator fun invoke(
        @DrawableRes resId: Int,
        contentDescription: String?,
        modifier: PreviewCompatGlanceModifier = PreviewCompatGlanceModifier,
    ) = currentRenderer().Image(resId, contentDescription, modifier)
}

/**
 * The [Context] the enclosing composition can supply.
 *
 * Reads like [GlanceLocalContext], and is a compat shim for the same reason: that local is a
 * `staticCompositionLocalOf` with no default, so reading it in a preview throws rather than
 * falling back. Only the branch that matches the current composition is evaluated.
 */
internal object PreviewCompatGlanceContext {

    val current: Context
        @Composable get() =
            if (LocalInspectionMode.current) ComposeLocalContext.current else GlanceLocalContext.current
}

/**
 * Contract shared by the production and preview renderers. Because both implementations
 * override the same signatures, they are forced to expose identical params and cannot drift
 * apart — an element added for one is a compile error until the other can draw it too.
 */
private interface GlanceElementRenderer {

    @Composable
    fun Box(
        modifier: PreviewCompatGlanceModifier,
        contentAlignment: Alignment,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Row(
        modifier: PreviewCompatGlanceModifier,
        verticalAlignment: Alignment.Vertical,
        content: @Composable () -> Unit,
    )

    @Composable
    fun Image(
        @DrawableRes resId: Int,
        contentDescription: String?,
        modifier: PreviewCompatGlanceModifier,
    )
}

/** Production renderer: the real Glance elements, as composed into the widget's `RemoteViews`. */
private object GlanceRenderer : GlanceElementRenderer {

    @Composable
    override fun Box(
        modifier: PreviewCompatGlanceModifier,
        contentAlignment: Alignment,
        content: @Composable () -> Unit,
    ) = GlanceBox(
        modifier = modifier.toGlanceModifier(),
        contentAlignment = contentAlignment,
        content = content,
    )

    @Composable
    override fun Row(
        modifier: PreviewCompatGlanceModifier,
        verticalAlignment: Alignment.Vertical,
        content: @Composable () -> Unit,
    ) = GlanceRow(
        modifier = modifier.toGlanceModifier(),
        verticalAlignment = verticalAlignment,
    ) {
        CompositionLocalProvider(LocalGlanceRowScope provides this) { content() }
    }

    @Composable
    override fun Image(
        @DrawableRes resId: Int,
        contentDescription: String?,
        modifier: PreviewCompatGlanceModifier,
    ) = GlanceImage(
        provider = ImageProvider(resId),
        contentDescription = contentDescription,
        modifier = modifier.toGlanceModifier(),
    )
}

/** Preview renderer: the Compose UI element that lays out the same way. */
private object ComposeRenderer : GlanceElementRenderer {

    @Composable
    override fun Box(
        modifier: PreviewCompatGlanceModifier,
        contentAlignment: Alignment,
        content: @Composable () -> Unit,
    ) = ComposeBox(
        modifier = modifier.toComposeModifier(),
        contentAlignment = contentAlignment.toCompose(),
    ) {
        content()
    }

    @Composable
    override fun Row(
        modifier: PreviewCompatGlanceModifier,
        verticalAlignment: Alignment.Vertical,
        content: @Composable () -> Unit,
    ) = ComposeRow(
        modifier = modifier.toComposeModifier(),
        verticalAlignment = verticalAlignment.toCompose(),
    ) {
        CompositionLocalProvider(LocalComposeRowScope provides this) { content() }
    }

    /**
     * Drawn as an image rather than an `Icon`, matching [GlanceImage]: these glyphs carry
     * their own colours and a tint would flatten them to one.
     */
    @Composable
    override fun Image(
        @DrawableRes resId: Int,
        contentDescription: String?,
        modifier: PreviewCompatGlanceModifier,
    ) = ComposeImage(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.toComposeModifier(),
    )
}

@Composable
private fun currentRenderer(): GlanceElementRenderer =
    if (LocalInspectionMode.current) ComposeRenderer else GlanceRenderer

private fun Alignment.toCompose(): ComposeAlignment = when (this) {
    Alignment.TopStart -> ComposeAlignment.TopStart
    Alignment.TopCenter -> ComposeAlignment.TopCenter
    Alignment.TopEnd -> ComposeAlignment.TopEnd
    Alignment.CenterStart -> ComposeAlignment.CenterStart
    Alignment.Center -> ComposeAlignment.Center
    Alignment.CenterEnd -> ComposeAlignment.CenterEnd
    Alignment.BottomStart -> ComposeAlignment.BottomStart
    Alignment.BottomCenter -> ComposeAlignment.BottomCenter
    Alignment.BottomEnd -> ComposeAlignment.BottomEnd
    // Glance builds an Alignment from one of the three values on each axis, so the nine
    // above are the whole set; this is only here because Alignment is a class, not an enum.
    else -> ComposeAlignment.TopStart
}

private fun Alignment.Vertical.toCompose(): ComposeAlignment.Vertical = when (this) {
    Alignment.Vertical.CenterVertically -> ComposeAlignment.CenterVertically
    Alignment.Vertical.Bottom -> ComposeAlignment.Bottom
    else -> ComposeAlignment.Top
}
