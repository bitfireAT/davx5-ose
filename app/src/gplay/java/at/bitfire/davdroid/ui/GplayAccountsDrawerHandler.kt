/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import at.bitfire.davdroid.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import javax.inject.Inject

class GplayAccountsDrawerHandler @Inject constructor() : StandardAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {
            R.id.nav_donate ->
                activity.startActivity(Intent(activity, EarnBadgesActivity::class.java))

            else ->
                // If we did not find a matching menu item, try the base
                super.onNavigationItemSelected(activity, item)
        }
    }

}