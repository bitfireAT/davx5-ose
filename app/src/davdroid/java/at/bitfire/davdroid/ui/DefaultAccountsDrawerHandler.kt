/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.ISettings

class DefaultAccountsDrawerHandler: IAccountsDrawerHandler {

    override fun onSettingsChanged(settings: ISettings?, menu: Menu) {
        if (BuildConfig.VERSION_NAME.contains("-beta") || BuildConfig.VERSION_NAME.contains("-rc"))
            menu.findItem(R.id.nav_beta_feedback).isVisible = true
    }

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_about ->
                activity.startActivity(Intent(activity, AboutActivity::class.java))
            R.id.nav_app_settings ->
                activity.startActivity(Intent(activity, AppSettingsActivity::class.java))
            R.id.nav_beta_feedback ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.beta_feedback_url))))
            R.id.nav_twitter ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/davdroidapp")))
            R.id.nav_website ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.homepage_url))))
            R.id.nav_faq ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.navigation_drawer_faq_url))))
            R.id.nav_forums ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.homepage_url))
                        .buildUpon().appendEncodedPath("forums/").build()))
            R.id.nav_donate ->
                if (BuildConfig.FLAVOR != App.FLAVOR_GOOGLE_PLAY)
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.homepage_url))
                            .buildUpon().appendEncodedPath("donate/").build()))
            else ->
                return false
        }

        return true
    }

}
