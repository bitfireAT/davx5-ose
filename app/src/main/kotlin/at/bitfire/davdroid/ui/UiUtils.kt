/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsClient
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.getSpans
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.logging.Level

object UiUtils {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UiUtilsEntryPoint {
        fun settingsManager(): SettingsManager
    }

    const val SHORTCUT_SYNC_ALL = "syncAllAccounts"
    const val SNACKBAR_LENGTH_VERY_LONG = 5000          // 5s

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

    /**
     * Starts the [Intent.ACTION_VIEW] intent with the given URL, if possible.
     * If the intent can't be resolved (for instance, because there is no browser
     * installed), this method does nothing.
     *
     * @param toastInstallBrowser whether to show "Please install a browser" toast when
     * the Intent could not be resolved
     *
     * @return true on success, false if the Intent could not be resolved (for instance, because
     * there is no user agent installed)
     */
    fun launchUri(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW, toastInstallBrowser: Boolean = true): Boolean {
        val intent = Intent(action, uri)
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            // no browser available
        }

        if (toastInstallBrowser)
            Toast.makeText(context, R.string.install_browser, Toast.LENGTH_LONG).show()

        return false
    }

    fun setTheme(context: Context) {
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
                            .setIntent(Intent(Intent.ACTION_SYNC, null, context, AccountsActivity::class.java))
                            .build()
                    )
                } catch(e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't update dynamic shortcut(s)", e)
                }
            }
    }


    @OptIn(ExperimentalTextApi::class)
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
                    addUrlAnnotation(
                        UrlAnnotation(span.url),
                        start = start, end = end
                    )
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline),
                        start = start, end = end
                    )
                }
                else ->
                    Logger.log.warning("Ignoring unknown span type ${span.javaClass.name}")
            }
        }
    }

    /**
     * Converts the String into an [AnnotatedString] with little HTML support.
     * Supported features:
     * - **Links**:
     *     - Must be well formatted (without extra spaces).
     *
     *       Valid: `<a href="{link}">{text}</a>`
     *
     *       Invalid: `<a  href="{link}">{text}</a >`
     *     - Quotes (`"`) for the link may be excluded (removed by CDATA on string resources).
     *
     * @param linkStyle The style to be used with links.
     */
    @ExperimentalTextApi
    @Composable
    fun String.annotateHtml(
        linkStyle: SpanStyle = SpanStyle(
            color = MaterialTheme.colors.primary,
            textDecoration = TextDecoration.Underline
        )
    ): AnnotatedString = buildAnnotatedString {
        // Search all HTML link matches
        val linkRegex = Regex("<a href=\"?https?://.*?\"?>.*?</a>")
        val matches = linkRegex.findAll(this@annotateHtml)
        if (matches.count() <= 0) {
            // There isn't any link in the text, append all text as is
            append(this@annotateHtml)
        } else {
            // There's at least one link, iterate them
            var previousGroup: MatchResult? = null
            for (group in matches) {
                if (previousGroup == null) {
                    // This is the first group found, append all the text before the match
                    val middleText = substring(0, group.range.first)
                    append(middleText)
                } else if (
                    // Make sure the previous and current groups do not intersect with each other
                    previousGroup.range.last + 1 != group.range.first
                ) {
                    val middleText = substring(previousGroup.range.last + 1, group.range.first)
                    append(middleText)
                }
                // Extract the tag's contents
                val tag = substring(group.range)
                val link = tag
                    // Remove the href part
                    .substring(tag.indexOf("href=") + 5, tag.indexOf('>'))
                    // Remove quotes if any
                    .substringAfter('"')
                    // Also in the end
                    .substringBeforeLast('"')
                // Extract the text inside the tag
                val text = tag.substring(tag.indexOf('>') + 1, tag.lastIndexOf('<'))
                // Annotate with the link
                withAnnotation(
                    UrlAnnotation(link)
                ) {
                    // And stylize with the given SpanStyle
                    withStyle(linkStyle) { append(text) }
                }

                // Copy the current group as the previous one, in case there's text between tags
                previousGroup = group
            }

            // If there's some non-annotated text at the end, append it
            val lastMatch = matches.last()
            if (lastMatch.range.last + 1 < length) {
                append(substring(lastMatch.range.last + 1))
            }
        }
    }
}