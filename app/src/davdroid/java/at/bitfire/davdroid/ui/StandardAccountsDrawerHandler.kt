/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import javax.inject.Inject

/**
 * Default menu items control
 */
open class StandardAccountsDrawerHandler @Inject constructor(): BaseAccountsDrawerHandler() {

    companion object {
        const val COMMUNITY_URL = "https://github.com/bitfireAT/davx5-ose/discussions"
    }

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {

            R.id.nav_twitter ->
                UiUtils.launchUri(
                    activity,
                    Uri.parse("https://twitter.com/" + activity.getString(R.string.twitter_handle))
                )

            R.id.nav_webdav_mounts ->
                activity.startActivity(Intent(activity, WebdavMountsActivity::class.java))

            R.id.nav_website ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity)
                )
            R.id.nav_manual ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity).buildUpon().appendPath("manual").build()
                )
            R.id.nav_faq ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity).buildUpon().appendPath("faq").build()
                )
            R.id.nav_community ->
                UiUtils.launchUri(activity, Uri.parse(COMMUNITY_URL))
            R.id.nav_donate ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity).buildUpon().appendPath("donate").build()
                )
            R.id.nav_privacy ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity).buildUpon().appendPath("privacy").build()
                )

            else ->
                super.onNavigationItemSelected(activity, item)
        }
    }

}