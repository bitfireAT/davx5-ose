/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
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

}