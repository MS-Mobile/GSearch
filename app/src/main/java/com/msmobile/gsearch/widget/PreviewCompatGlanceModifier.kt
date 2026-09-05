package com.msmobile.gsearch.widget

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.msmobile.gsearch.widget.PreviewCompatGlanceModifier.Op
import androidx.compose.foundation.layout.RowScope as ComposeRowScope
import androidx.compose.ui.Modifier as ComposeModifier
import androidx.glance.layout.RowScope as GlanceRowScope

/**
 * Drop-in replacement for [GlanceModifier], chained with the same call syntax.
 *
 * It exists for the same reason [com.msmobile.gsearch.widget.PreviewCompatGlanceBox] does:
 * a Glance composable cannot be rendered by a Compose preview or a screenshot test, because
 * those compose with the Compose UI applier and Glance emits its own node type. Rather than
 * resolve to one modifier or the other at the call site, a chain is recorded as a list of
 * [Op]s and materialised into whichever modifier the enclosing composition can actually
 * take — [toGlanceModifier] in the widget, [toComposeModifier] under
 * [androidx.compose.ui.platform.LocalInspectionMode].
 *
 * Only the operations the widget uses are modelled. That is deliberate: every op costs two
 * implementations, and the pair of exhaustive `when`s below is what stops them from
 * drifting — adding an op that only one side knows how to apply will not compile.
 */
// TooManyFunctions: the surface is not this type's own — it is one builder per operation
// [GlanceModifier] already offers, and a chain that could not be written the same way at the
// call site would defeat the point of the shim.
@Suppress("TooManyFunctions")
internal interface PreviewCompatGlanceModifier {

    val ops: List<Op>

    fun then(other: PreviewCompatGlanceModifier): PreviewCompatGlanceModifier =
        chained(ops + other.ops)

    fun fillMaxSize(): PreviewCompatGlanceModifier = chained(ops + Op.FillMaxSize)

    fun fillMaxWidth(): PreviewCompatGlanceModifier = chained(ops + Op.FillMaxWidth)

    fun fillMaxHeight(): PreviewCompatGlanceModifier = chained(ops + Op.FillMaxHeight)

    fun height(height: Dp): PreviewCompatGlanceModifier = chained(ops + Op.Height(height))

    fun size(size: Dp): PreviewCompatGlanceModifier = chained(ops + Op.Size(size))

    fun padding(horizontal: Dp): PreviewCompatGlanceModifier =
        chained(ops + Op.HorizontalPadding(horizontal))

    /**
     * Both colour variants rather than one resolved colour, matching the [ColorProvider] the
     * widget needs: it is drawn in the launcher's process, so it is the launcher's dark-mode
     * state that decides. The preview resolves them itself against [isSystemInDarkTheme].
     */
    fun background(day: Color, night: Color): PreviewCompatGlanceModifier =
        chained(ops + Op.BackgroundColor(day, night))

    fun background(@DrawableRes resId: Int): PreviewCompatGlanceModifier =
        chained(ops + Op.BackgroundImage(resId))

    fun cornerRadius(radius: Dp): PreviewCompatGlanceModifier =
        chained(ops + Op.CornerRadius(radius))

    /** Only has an effect inside a [PreviewCompatGlanceRow], as in both frameworks. */
    fun defaultWeight(): PreviewCompatGlanceModifier = chained(ops + Op.DefaultWeight)

    /**
     * The [Action] is built lazily because the preview never needs one — and must not build
     * one, since [androidx.glance.appwidget.action.actionStartActivity] describes a launch
     * that only means something to a widget host.
     */
    fun clickable(action: () -> Action): PreviewCompatGlanceModifier =
        chained(ops + Op.Clickable(action))

    /** One link of a chain. See the class comment for why the set is kept this small. */
    sealed interface Op {
        data object FillMaxSize : Op
        data object FillMaxWidth : Op
        data object FillMaxHeight : Op
        data object DefaultWeight : Op
        data class Height(val height: Dp) : Op
        data class Size(val size: Dp) : Op
        data class HorizontalPadding(val padding: Dp) : Op
        data class BackgroundColor(val day: Color, val night: Color) : Op
        data class BackgroundImage(@DrawableRes val resId: Int) : Op
        data class CornerRadius(val radius: Dp) : Op
        data class Clickable(val action: () -> Action) : Op
    }

    /** The empty chain every modifier starts from, mirroring [GlanceModifier]'s companion. */
    companion object : PreviewCompatGlanceModifier {
        override val ops: List<Op> = emptyList()
    }
}

private class ChainedPreviewCompatGlanceModifier(
    override val ops: List<Op>,
) : PreviewCompatGlanceModifier

private fun chained(ops: List<Op>): PreviewCompatGlanceModifier =
    ChainedPreviewCompatGlanceModifier(ops)

/**
 * The [GlanceRowScope] of the nearest enclosing [PreviewCompatGlanceRow], or null outside one.
 *
 * `defaultWeight` is a member of the row's scope in both frameworks, but the compat row hands
 * its content no scope of its own — a receiver would have to be one type or the other, which
 * is the thing this file exists to avoid. So the row publishes its scope here instead and the
 * modifier picks it up when it is materialised.
 */
internal val LocalGlanceRowScope = staticCompositionLocalOf<GlanceRowScope?> { null }

/** The Compose half of [LocalGlanceRowScope]; only one of the two is ever set. */
internal val LocalComposeRowScope = staticCompositionLocalOf<ComposeRowScope?> { null }

/** Materialises the chain for the widget, where Glance's applier is the one composing. */
@Composable
internal fun PreviewCompatGlanceModifier.toGlanceModifier(): GlanceModifier {
    val rowScope = LocalGlanceRowScope.current
    return ops.fold<Op, GlanceModifier>(GlanceModifier) { modifier, op ->
        when (op) {
            Op.FillMaxSize -> modifier.fillMaxSize()
            Op.FillMaxWidth -> modifier.fillMaxWidth()
            Op.FillMaxHeight -> modifier.fillMaxHeight()
            Op.DefaultWeight -> rowScope?.run { modifier.defaultWeight() } ?: modifier
            is Op.Height -> modifier.height(op.height)
            is Op.Size -> modifier.size(op.size)
            is Op.HorizontalPadding -> modifier.padding(horizontal = op.padding)
            is Op.BackgroundColor ->
                modifier.background(ColorProvider(day = op.day, night = op.night))
            is Op.BackgroundImage -> modifier.background(ImageProvider(op.resId))
            is Op.CornerRadius -> modifier.cornerRadius(op.radius)
            is Op.Clickable -> modifier.clickable(op.action())
        }
    }
}

/**
 * Materialises the chain for a preview, where Compose UI's applier is the one composing.
 *
 * Two ops cannot be translated one-for-one. A corner radius is a property of the background
 * in Glance no matter where in the chain it sits, but in Compose it is the shape the
 * background is drawn with, so it is resolved ahead of the fold rather than applied in place.
 * And a click is dropped entirely: a preview is composed once and drawn, and the [Action]
 * behind it addresses a widget host that is not there.
 */
// CyclomaticComplexMethod: the branches are an exhaustive mapping table over [Op], not
// procedural logic — one arm per operation is the lowest it can go while the compiler is
// still the thing that catches an op the preview cannot draw.
// ModifierFactoryExtensionFunction: this returns a finished modifier for one call site to
// pass on, rather than adding a link a caller would go on chaining, so the receiver Compose
// lint wants here is the one type it cannot have.
@Suppress("CyclomaticComplexMethod", "ModifierFactoryExtensionFunction")
@Composable
internal fun PreviewCompatGlanceModifier.toComposeModifier(): ComposeModifier {
    val rowScope = LocalComposeRowScope.current
    val night = isSystemInDarkTheme()
    val shape = ops.backgroundShape()
    val backgroundPainter = ops.backgroundPainter()

    return ops.fold<Op, ComposeModifier>(ComposeModifier) { modifier, op ->
        when (op) {
            Op.FillMaxSize -> modifier.fillMaxSize()
            Op.FillMaxWidth -> modifier.fillMaxWidth()
            Op.FillMaxHeight -> modifier.fillMaxHeight()
            Op.DefaultWeight -> rowScope?.run { modifier.weight(1f) } ?: modifier
            is Op.Height -> modifier.height(op.height)
            is Op.Size -> modifier.size(op.size)
            is Op.HorizontalPadding -> modifier.padding(horizontal = op.padding)
            is Op.BackgroundColor ->
                modifier.background(color = if (night) op.night else op.day, shape = shape)
            is Op.BackgroundImage -> backgroundPainter
                ?.let { modifier.paint(it, contentScale = ContentScale.FillBounds) }
                ?: modifier
            is Op.CornerRadius -> modifier // Applied as the background's shape, see above.
            is Op.Clickable -> modifier // Nothing to click in a still image, see above.
        }
    }
}

/** The shape a [Op.BackgroundColor] is drawn with, square unless the chain rounds it. */
private fun List<Op>.backgroundShape(): Shape =
    filterIsInstance<Op.CornerRadius>().lastOrNull()
        ?.let { RoundedCornerShape(it.radius) }
        ?: RectangleShape

/**
 * Resolved ahead of the fold rather than inside it, so the composition sees the same call in
 * the same place whatever the chain holds.
 */
@Composable
private fun List<Op>.backgroundPainter(): Painter? {
    val resId = filterIsInstance<Op.BackgroundImage>().lastOrNull()?.resId
    return if (resId != null) painterResource(resId) else null
}
