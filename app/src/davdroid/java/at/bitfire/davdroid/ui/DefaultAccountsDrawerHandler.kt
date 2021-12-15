/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity

class DefaultAccountsDrawerHandler: IAccountsDrawerHandler {

    companion object {
        private const val BETA_FEEDBACK_URI = "mailto:play@bitfire.at?subject=${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} feedback (${BuildConfig.VERSION_CODE})"
    }


    override fun initMenu(context: Context, menu: Menu) {
        if (BuildConfig.VERSION_NAME.contains("-alpha") || BuildConfig.VERSION_NAME.contains("-beta") || BuildConfig.VERSION_NAME.contains("-rc"))
            menu.findItem(R.id.nav_beta_feedback).isVisible = true
        if (/* ose */ false)
            menu.findItem(R.id.nav_donate).isVisible = true
    }

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_about ->
                activity.startActivity(Intent(activity, AboutActivity::class.java))
            R.id.nav_beta_feedback ->
                if (!UiUtils.launchUri(activity, Uri.parse(BETA_FEEDBACK_URI), Intent.ACTION_SENDTO, false))
                    Toast.makeText(activity, R.string.install_email_client, Toast.LENGTH_LONG).show()
            R.id.nav_app_settings ->
                activity.startActivity(Intent(activity, AppSettingsActivity::class.java))

            R.id.nav_twitter ->
                UiUtils.launchUri(activity,
                        Uri.parse("https://twitter.com/" + activity.getString(R.string.twitter_handle)))
            R.id.nav_website ->
                UiUtils.launchUri(activity,
                        App.homepageUrl(activity))

            R.id.nav_webdav_mounts ->
                activity.startActivity(Intent(activity, WebdavMountsActivity::class.java))

            R.id.nav_manual ->
                UiUtils.launchUri(activity,
                        App.homepageUrl(activity).buildUpon().appendPath("manual").build())
            R.id.nav_faq ->
                UiUtils.launchUri(activity,
                        App.homepageUrl(activity).buildUpon().appendPath("faq").build())
            R.id.nav_forums ->
                UiUtils.launchUri(activity,
                        App.homepageUrl(activity).buildUpon().appendPath("forums").build())
            R.id.nav_donate ->
                if (BuildConfig.FLAVOR != App.FLAVOR_GOOGLE_PLAY)
                    UiUtils.launchUri(activity,
                            App.homepageUrl(activity).buildUpon().appendPath("donate").build())
            R.id.nav_privacy ->
                UiUtils.launchUri(activity,
                        App.homepageUrl(activity).buildUpon().appendPath("privacy").build())

            else ->
                return false
        }

        return true
    }

}