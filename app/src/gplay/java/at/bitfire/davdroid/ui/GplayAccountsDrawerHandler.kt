/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger

class GplayAccountsDrawerHandler: StandardAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {
            R.id.nav_donate ->
                activity.startActivity(Intent(activity, EarnBadgesActivity::class.java))

            R.id.nav_rate_us ->
                if (!UiUtils.launchUri(activity, Uri.parse("market://details?id=" + activity.packageName)))
                    // no store installed, open Google Play website instead
                    UiUtils.launchUri(
                        activity,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + activity.packageName)
                    )

            else ->
                // If we did not find a matching menu item, try the base
                super.onNavigationItemSelected(activity, item)
        }
    }

}