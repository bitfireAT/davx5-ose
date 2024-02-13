/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.logging.Level
import javax.inject.Inject

class GplayAccountsDrawerHandler @Inject constructor() : StandardAccountsDrawerHandler() {

    override fun onNavigationItemSelected(activity: Activity, item: MenuItem) {
        when (item.itemId) {
            R.id.nav_beta_feedback -> {
                // use In-App Review API to submit private feedback
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.log.info("Launching in-app review flow")
                        manager.launchReviewFlow(activity, task.result)

                        // provide alternative if in-app review flow didn't show up
                        Snackbar.make(ActivityCompat.requireViewById<View>(activity, android.R.id.content), R.string.nav_feedback_inapp_didnt_appear, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.nav_feedback_google_play) {
                                fallbackToStore(activity, item)
                            }
                            .show()
                    } else {
                        Logger.log.log(Level.WARNING, "Couldn't start in-app review flow", task.exception)
                        fallbackToStore(activity, item)
                    }
                }
            }

            R.id.nav_donate ->
                activity.startActivity(Intent(activity, EarnBadgesActivity::class.java))

            else ->
                // If we did not find a matching menu item, try the base
                super.onNavigationItemSelected(activity, item)
        }
    }

    private fun fallbackToStore(activity: Activity, item: MenuItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
            setPackage("com.android.vending")   // Google Play only (this is only for the gplay flavor)
        }
        try {
            activity.startActivity(intent)
            Toast.makeText(activity, R.string.nav_feedback_scroll_to_reviews, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            // fall back to email
            super.onNavigationItemSelected(activity, item)
        }
    }

}