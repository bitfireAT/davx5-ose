/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.getSpans
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.logging.Level
import java.util.logging.Logger

object UiUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UiUtilsEntryPoint {
        fun logger(): Logger
        fun settingsManager(): SettingsManager
    }

    const val SHORTCUT_SYNC_ALL = "syncAllAccounts"


    @Composable
    fun adaptiveIconPainterResource(@DrawableRes id: Int): Painter {
        val context = LocalContext.current
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val adaptiveIcon = ResourcesCompat.getDrawable(context.resources, id, null) as? AdaptiveIconDrawable
            if (adaptiveIcon != null)
                BitmapPainter(adaptiveIcon.toBitmap().asImageBitmap())
            else
                painterResource(id)
        } else
            painterResource(id)
    }

    fun haveCustomTabs(context: Context) = CustomTabsClient.getPackageName(context, null, false) != null

    fun updateTheme(context: Context) {
        val settings = EntryPointAccessors.fromApplication(context, UiUtilsEntryPoint::class.java).settingsManager()
        val mode = settings.getIntOrNull(Settings.PREFERRED_THEME) ?: Settings.PREFERRED_THEME_DEFAULT
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun updateShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT >= 25)
            context.getSystemService<ShortcutManager>()?.let { shortcutManager ->
                try {
                    shortcutManager.dynamicShortcuts = listOf(
                        ShortcutInfo.Builder(context, SHORTCUT_SYNC_ALL)
                            .setIcon(Icon.createWithResource(context, R.drawable.ic_sync_shortcut))
                            .setShortLabel(context.getString(R.string.accounts_sync_all))
                            .setIntent(Intent(Intent.ACTION_SYNC, null, context, MainActivity::class.java))
                            .build()
                    )
                } catch(e: Exception) {
                    val logger = EntryPointAccessors.fromApplication(context, UiUtilsEntryPoint::class.java).logger()
                    logger.log(Level.WARNING, "Couldn't update dynamic shortcut(s)", e)
                }
            }
    }

    @Composable
    fun Spanned.toAnnotatedString() = buildAnnotatedString {
        val spanned = this@toAnnotatedString
        append(spanned.toString())

        for (span in getSpans<Any>(0, spanned.length)) {
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            when (span) {
                is StyleSpan ->
                    when (span.style) {
                        Typeface.BOLD -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start = start, end = end
                        )
                        Typeface.ITALIC -> addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start = start, end = end
                        )
                    }
                is URLSpan -> {
                    addLink(
                        LinkAnnotation.Url(span.url),
                        start = start, end = end
                    )
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        start = start, end = end
                    )
                }
                else -> {
                    val context = LocalContext.current
                    val logger = EntryPointAccessors.fromApplication(context, UiUtilsEntryPoint::class.java).logger()
                    logger.warning("Ignoring unknown span type ${span.javaClass.name}")
                }
            }
        }
    }

}