package com.msmobile.gsearch.config

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.msmobile.gsearch.widget.GSearchWidgetProvider
import com.msmobile.gsearch.widget.WidgetConfig
import com.msmobile.gsearch.widget.WidgetRefresh

/**
 * The app's only screen: which buttons the widget shows, in what order, and how
 * see-through it is.
 *
 * Serves two entry points with the same UI — the launcher icon, and the launcher's
 * long-press "settings" entry on the widget itself (declared as `android:configure`).
 * The configuration is global (see [WidgetConfig]), so the widget id the second entry
 * point supplies is not used to look anything up; it only has to be handed back.
 */
class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // When the launcher starts this to configure a widget it is waiting on a result,
        // and treats anything other than RESULT_OK as "the user backed out" — which for a
        // freshly dropped widget means silently deleting it. Settled up front rather than
        // on the way out, because there is nothing here that can fail and every edit is
        // saved as it is made, so backing out is always a valid way to finish.
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
        }

        setContent {
            GSearchTheme {
                ConfigScreen(
                    initialOrder = WidgetConfig.displayOrder(this),
                    initialActions = WidgetConfig.actions(this),
                    initialOpacity = WidgetConfig.opacityPercent(this),
                    canPinWidget = canPinWidget(),
                    onArrangementChange = { order, actions ->
                        WidgetConfig.setArrangement(this, order, actions)
                        WidgetRefresh.request(this)
                    },
                    onOpacityChange = { percent ->
                        WidgetConfig.setOpacityPercent(this, percent)
                        WidgetRefresh.request(this)
                    },
                    onAddToHomeScreen = ::requestPinWidget,
                    onDone = ::finish,
                )
            }
        }
    }

    /**
     * Whether the current launcher can be asked to place a widget. Pixel Launcher and One
     * UI both can; some third-party launchers cannot, and the button is hidden rather than
     * shown doing nothing.
     */
    // The annotation is what lets lint see that requestPinWidget's API-26 call is guarded.
    // Without it the guard is a plain Boolean helper, lint cannot follow it across the call
    // boundary, and the requestPinAppWidget call below reads as an unguarded NewApi error.
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private fun canPinWidget(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported

    private fun requestPinWidget() {
        if (!canPinWidget()) return
        AppWidgetManager.getInstance(this).requestPinAppWidget(
            ComponentName(this, GSearchWidgetProvider::class.java),
            null,
            null,
        )
    }
}

/**
 * Deliberately not a dynamic-colour theme. The widget's own palette is fixed to match the
 * stock Google bar, and a settings screen that recoloured itself from the wallpaper would
 * make the preview a misleading guide to what lands on the home screen.
 */
@Composable
fun GSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
