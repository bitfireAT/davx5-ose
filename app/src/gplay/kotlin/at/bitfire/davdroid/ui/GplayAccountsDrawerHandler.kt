/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.logging.Level
import javax.inject.Inject

/**
 * Overrides some navigationd drawer actions for Google Play
 */
class GplayAccountsDrawerHandler @Inject constructor() : StandardAccountsDrawerHandler() {

    @Composable
    override fun Contribute(onContribute: () -> Unit) {
        val context = LocalContext.current
        MenuEntry(
            icon = Icons.Default.VolunteerActivism,
            title = stringResource(R.string.earn_badges),
            onClick = {
                context.startActivity(Intent(context, EarnBadgesActivity::class.java))
            }
        )
    }

    @Composable
    @Preview
    fun MenuEntries_Gplay_Preview() {
        Column {
            MenuEntries(SnackbarHostState())
        }
    }


    override fun onBetaFeedback(
        context: Context,
        onShowSnackbar: (message: String, actionLabel: String, action: () -> Unit) -> Unit
    ) {
        // use In-App Review API to submit private feedback
        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Logger.log.info("Launching in-app review flow")

                if (context is Activity)
                    manager.launchReviewFlow(context, task.result)

                // provide alternative for the case that the in-app review flow didn't show up
                onShowSnackbar(
                    context.getString(R.string.nav_feedback_inapp_didnt_appear),
                    context.getString(R.string.nav_feedback_google_play),
                    {
                        if (!openInStore(context))
                        // couldn't open in store, fall back to email
                            super.onBetaFeedback(context, onShowSnackbar)
                    }
                )
            } else {
                Logger.log.log(Level.WARNING, "Couldn't start in-app review flow", task.exception)
                openInStore(context)
            }
        }
    }

    private fun openInStore(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
            setPackage("com.android.vending")   // Google Play only (this is only for the gplay flavor)
        }
        return try {
            context.startActivity(intent)
            Toast.makeText(context, R.string.nav_feedback_scroll_to_reviews, Toast.LENGTH_LONG).show()
            true
        } catch (e: ActivityNotFoundException) {
            // fall back to email
            false
        }
    }

}