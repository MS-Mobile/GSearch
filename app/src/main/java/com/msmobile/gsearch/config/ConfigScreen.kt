package com.msmobile.gsearch.config

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.msmobile.gsearch.R
import com.msmobile.gsearch.widget.WidgetAction
import com.msmobile.gsearch.widget.WidgetConfig
import kotlin.math.roundToInt

private val ROW_HEIGHT = 64.dp
private val PREVIEW_PILL_HEIGHT = 56.dp
private val PREVIEW_GLYPH_SIZE = 25.dp

/**
 * Everything is saved the moment it changes and the widget is refreshed with it, so there
 * is no save button and no way to leave the screen holding unsaved edits. That also makes
 * backing out of the launcher's configure flow safe.
 */
@Composable
fun ConfigScreen(
    initialActions: List<WidgetAction>,
    initialOpacity: Int,
    canPinWidget: Boolean,
    onActionsChange: (List<WidgetAction>) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onAddToHomeScreen: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // All four actions in display order; whether each is on is tracked separately so a
    // row keeps its place when switched off instead of jumping to the end of the list.
    val order =
        remember {
            mutableStateListOf<WidgetAction>().apply { addAll(displayOrder(initialActions)) }
        }
    val enabled = remember {
        mutableStateMapOf<WidgetAction, Boolean>().apply {
            WidgetAction.entries.forEach { put(it, it in initialActions) }
        }
    }
    var opacity by remember { mutableFloatStateOf(initialOpacity.toFloat()) }

    fun enabledInOrder() = order.filter { enabled[it] == true }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                // Activities are edge-to-edge from API 35 on, so without this the heading
                // sits underneath the status bar clock.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.config_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            WidgetPreview(actions = enabledInOrder(), opacityPercent = opacity.roundToInt())

            Text(
                text = stringResource(R.string.config_buttons_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.config_buttons_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActionList(
                order = order,
                isEnabled = { enabled[it] == true },
                onToggle = { action, on ->
                    enabled[action] = on
                    onActionsChange(enabledInOrder())
                },
                canDisable = { enabledInOrder().size > 1 },
                canEnable = { enabledInOrder().size < WidgetConfig.MAX_ACTIONS },
                onMove = { from, to ->
                    order.add(to, order.removeAt(from))
                    onActionsChange(enabledInOrder())
                },
            )

            Text(
                text = stringResource(R.string.config_opacity_heading, opacity.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                // Persisting only when the drag ends keeps this from broadcasting a widget
                // update for every pixel of slider travel.
                onValueChangeFinished = { onOpacityChange(opacity.roundToInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("opacity_slider"),
            )

            if (canPinWidget) {
                OutlinedButton(
                    onClick = onAddToHomeScreen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.config_add_to_home))
                }
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.config_done))
            }
        }
    }
}

/**
 * A live rendering of the bar as configured.
 *
 * Drawn over a colour wash rather than a flat surface so the opacity setting is actually
 * visible — against a plain background a translucent pill looks identical to an opaque one.
 */
@Composable
private fun WidgetPreview(actions: List<WidgetAction>, opacityPercent: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF3B5BA9), Color(0xFF7E4B9A), Color(0xFFB5542F)),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_PILL_HEIGHT)
                .background(
                    color = colorResource(R.color.widget_pill_background)
                        .copy(alpha = opacityPercent / 100f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .padding(horizontal = 10.dp)
                .testTag("widget_preview"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = stringResource(action.labelRes),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(PREVIEW_GLYPH_SIZE),
                    )
                }
            }
        }
    }
}

/**
 * The reorderable list of actions.
 *
 * Dragging is bound to the handle and uses a plain drag gesture rather than a long-press
 * one, so the whole row stays free for the switch and the drag starts on first touch.
 *
 * Each row is wrapped in [key] so its composition — and with it the gesture handler that
 * captured `action` — follows the row as the list is reordered mid-drag. Without that the
 * handler at a given position would keep acting on whichever action started there.
 */
@Composable
private fun ActionList(
    order: List<WidgetAction>,
    isEnabled: (WidgetAction) -> Boolean,
    onToggle: (WidgetAction, Boolean) -> Unit,
    canDisable: () -> Boolean,
    canEnable: () -> Boolean,
    onMove: (Int, Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<WidgetAction?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    Column {
        order.forEach { action ->
            key(action) {
                val on = isEnabled(action)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .padding(vertical = 4.dp)
                        // The dragged row floats above its neighbours as they shuffle.
                        .zIndex(if (dragging == action) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging == action) dragOffset else 0f
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drag_handle),
                            contentDescription = stringResource(
                                R.string.config_reorder_description,
                                stringResource(action.labelRes),
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("drag_${action.name}")
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = action
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            // Positions are looked up fresh rather than
                                            // captured, because the list is reordered
                                            // underneath this gesture as it runs.
                                            val from = order.indexOf(action)
                                            if (from < 0) return@detectDragGestures
                                            val shift =
                                                (dragOffset / rowHeightPx).roundToInt()
                                            val to = (from + shift)
                                                .coerceIn(0, order.size - 1)
                                            if (to != from) {
                                                onMove(from, to)
                                                // The row has physically moved by this
                                                // much, so drop it from the offset or it
                                                // would be counted twice.
                                                dragOffset -= (to - from) * rowHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffset = 0f
                                        },
                                    )
                                },
                        )

                        Spacer(Modifier.width(8.dp))

                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(24.dp)
                                .alpha(if (on) 1f else 0.4f),
                        )

                        Spacer(Modifier.width(16.dp))

                        Text(
                            text = stringResource(action.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (on) 1f else 0.4f),
                        )

                        Switch(
                            checked = on,
                            // The last enabled action cannot be switched off: an empty
                            // pill has nothing to tap and no way back except this screen.
                            enabled = if (on) canDisable() else canEnable(),
                            onCheckedChange = { onToggle(action, it) },
                            modifier = Modifier
                                .testTag("switch_${action.name}")
                                .semantics {
                                    contentDescription = "toggle ${action.name}"
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders [ConfigScreen] for the previews and, through [ConfigScreenshotTest], for the
 * screenshot gate.
 *
 * Wrapped in [GSearchTheme] rather than left bare so the preview resolves the same colour
 * scheme [ConfigActivity] does; without it the Material colours fall back to defaults and
 * the reference images would stop matching what ships.
 *
 * The callbacks are all no-ops. Nothing here is interacted with — the screen is composed
 * once and drawn — and the real ones write to SharedPreferences and poke the widget host,
 * neither of which exists in a preview.
 */
@VisibleForTesting
@PreviewPhone
@Composable
internal fun ConfigScreenPreview(
    @PreviewParameter(ConfigPreviewConfigProvider::class) config: ConfigPreviewConfig,
) {
    GSearchTheme {
        ConfigScreen(
            initialActions = config.actions,
            initialOpacity = config.opacityPercent,
            canPinWidget = config.canPinWidget,
            onActionsChange = {},
            onOpacityChange = {},
            onAddToHomeScreen = {},
            onDone = {},
        )
    }
}
