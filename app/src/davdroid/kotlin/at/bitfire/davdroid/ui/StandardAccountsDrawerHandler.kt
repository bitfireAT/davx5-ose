/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.COMMUNITY_URL
import at.bitfire.davdroid.Constants.FEDIVERSE_URL
import at.bitfire.davdroid.Constants.MANUAL_URL
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import javax.inject.Inject

/**
 * Default menu items control
 */
open class StandardAccountsDrawerHandler @Inject constructor(): BaseAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        val homepageUrl = Constants.HOMEPAGE_URL.buildUpon()
            .withStatParams("StandardAccountsDrawerHandler")

        when (item.itemId) {

            R.id.nav_mastodon ->
                UiUtils.launchUri(
                    activity,
                    FEDIVERSE_URL
                )

            R.id.nav_webdav_mounts ->
                activity.startActivity(Intent(activity, WebdavMountsActivity::class.java))

            R.id.nav_website ->
                UiUtils.launchUri(
                    activity,
                    homepageUrl.build()
                )
            R.id.nav_manual ->
                UiUtils.launchUri(
                    activity,
                    MANUAL_URL
                )
            R.id.nav_faq ->
                UiUtils.launchUri(
                    activity,
                    homepageUrl.appendPath(Constants.HOMEPAGE_PATH_FAQ).build()
                )
            R.id.nav_community ->
                UiUtils.launchUri(activity, COMMUNITY_URL)
            R.id.nav_donate ->
                UiUtils.launchUri(
                    activity,
                    homepageUrl.appendPath(Constants.HOMEPAGE_PATH_OPEN_SOURCE).build()
                )
            R.id.nav_privacy ->
                UiUtils.launchUri(
                    activity,
                    homepageUrl.appendPath(Constants.HOMEPAGE_PATH_PRIVACY).build()
                )

            else ->
                super.onNavigationItemSelected(activity, item)
        }
    }

}