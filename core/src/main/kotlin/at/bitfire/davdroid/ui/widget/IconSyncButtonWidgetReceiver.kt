/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import androidx.compose.material3.ColorScheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import at.bitfire.davdroid.di.qualifier.DarkColorScheme
import at.bitfire.davdroid.di.qualifier.LightColorScheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IconSyncButtonWidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var model: SyncWidgetModel

    @Inject
    @LightColorScheme
    lateinit var lightColorScheme: ColorScheme

    @Inject
    @DarkColorScheme
    lateinit var darkColorScheme: ColorScheme

    override val glanceAppWidget: GlanceAppWidget
        get() = IconSyncButtonWidget(
            model = model,
            lightColorScheme = lightColorScheme,
            darkColorScheme = darkColorScheme
        )

}
