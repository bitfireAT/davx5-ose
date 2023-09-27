/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import at.bitfire.davdroid.R
import com.google.android.play.core.review.ReviewManagerFactory
import javax.inject.Inject

class GplayAccountsDrawerHandler @Inject constructor() : StandardAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {
            R.id.nav_beta_feedback -> {
                // use In-App Review API to submit private feedback
                val manager = ReviewManagerFactory.create(activity)
                manager.requestReviewFlow()
                    .addOnSuccessListener { reviewInfo ->
                        manager.launchReviewFlow(activity, reviewInfo)
                    }.addOnFailureListener {
                        // fall back to email in case of failure
                        super.onNavigationItemSelected(activity, item)
                    }
            }

            R.id.nav_donate ->
                activity.startActivity(Intent(activity, EarnBadgesActivity::class.java))

            else ->
                // If we did not find a matching menu item, try the base
                super.onNavigationItemSelected(activity, item)
        }
    }

}