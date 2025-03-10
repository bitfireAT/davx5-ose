/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.unit.ColorProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.M3ColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * A widget with a "Sync all" button displaying just an icon to indicate the action.
 */
class IconSyncButtonWidget : GlanceAppWidget() {

    // Hilt over @AndroidEntryPoint is not available for widgets
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncButtonWidgetEntryPoint {
        fun model(): SyncWidgetModel
    }


    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // initial data
        val entryPoint = EntryPointAccessors.fromApplication<SyncButtonWidgetEntryPoint>(context)
        val model = entryPoint.model()

        // will be called when the widget is updated
        provideContent {
            WidgetContent(model)
        }
    }

    @Composable
    private fun WidgetContent(model: SyncWidgetModel) {
        val context = LocalContext.current

        Box(
            modifier = GlanceModifier
                .size(50.dp)
                .background(ColorProvider(M3ColorScheme.primaryLight))
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
                colorFilter = ColorFilter.tint(
                    ColorProvider(M3ColorScheme.onPrimaryLight)
                )
            )
        }
    }

}