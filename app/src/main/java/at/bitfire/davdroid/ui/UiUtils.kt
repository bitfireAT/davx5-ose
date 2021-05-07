/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

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
import java.util.logging.Level

object UiUtils {

    const val SHORTCUT_SYNC_ALL = "syncAllAccounts"
    const val SNACKBAR_LENGTH_VERY_LONG = 5000          // 5s

    /**
     * Starts the [Intent.ACTION_VIEW] intent with the given URL, if possible.
     * If the intent can't be resolved (for instance, because there is no browser
     * installed), this method does nothing.
     *
     * @param toastInstallBrowser whether to show "Please install a browser" toast when
     * the Intent could not be resolved; will also add [Intent.CATEGORY_BROWSABLE] to the Intent
     *
     * @return true on success, false if the Intent could not be resolved (for instance, because
     * there is no user agent installed)
     */
    fun launchUri(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW, toastInstallBrowser: Boolean = true): Boolean {
        val intent = Intent(action, uri)
        if (toastInstallBrowser)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        } else if (toastInstallBrowser)
            Toast.makeText(context, R.string.install_browser, Toast.LENGTH_LONG).show()
        return false
    }

    fun setTheme(context: Context) {
        val settings = SettingsManager.getInstance(context)
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