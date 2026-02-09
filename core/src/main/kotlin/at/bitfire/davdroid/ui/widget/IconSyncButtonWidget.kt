/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import at.bitfire.davdroid.R

/**
 * A widget with a "Sync all" button displaying just an icon to indicate the action.
 */
class IconSyncButtonWidget(
    private val model: SyncWidgetModel,
    private val lightColorScheme: ColorScheme,
    private val darkColorScheme: ColorScheme
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // will be called when the widget is updated
        provideContent {
            GlanceTheme(
                colors = ColorProviders(
                    light = lightColorScheme,
                    dark = darkColorScheme
                )
            ) {
                WidgetContent(model)
            }
        }
    }

    @Composable
    private fun WidgetContent(model: SyncWidgetModel) {
        val context = LocalContext.current

        Box(
            modifier = GlanceModifier
                .size(50.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(25.dp)
                .clickable {
                    model.requestSync()
                    Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_sync),
                contentDescription = context.getString(R.string.widget_sync_all_accounts),
                modifier = GlanceModifier.fillMaxSize().size(32.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
            )
        }
    }

}