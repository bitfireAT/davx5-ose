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
import javax.inject.Inject

/**
 * Default menu items control
 */
class OseAccountsDrawerHandler @Inject constructor(): BaseAccountsDrawerHandler() {

    companion object {
        const val COMMUNITY_URL = "https://github.com/bitfireAT/davx5-ose/discussions"
        const val MANUAL_URL = "https://manual.davx5.com"
    }

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {

            R.id.nav_mastodon ->
                UiUtils.launchUri(
                    activity,
                    Uri.parse("https://fosstodon.org/@davx5app")
                )

            R.id.nav_webcal_subscriptions ->
                activity.startActivity(Intent(activity, WebcalSubscriptionsActivity::class.java))
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
                    Uri.parse(MANUAL_URL)
                )
            R.id.nav_faq ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity, "faq")
                )
            R.id.nav_community ->
                UiUtils.launchUri(activity, Uri.parse(COMMUNITY_URL))
            R.id.nav_donate ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity, "donate")
                )
            R.id.nav_privacy ->
                UiUtils.launchUri(
                    activity,
                    App.homepageUrl(activity, App.HOMEPAGE_PRIVACY)
                )

            else ->
                super.onNavigationItemSelected(activity, item)
        }
    }

}