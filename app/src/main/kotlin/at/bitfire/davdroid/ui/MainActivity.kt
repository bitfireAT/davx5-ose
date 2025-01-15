/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.ui.navigation.Destination
import at.bitfire.davdroid.ui.navigation.Navigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Navigation(
                deepLinkUri = intent.data
            )
        }
    }


    companion object {

        fun intentWithDestination(context: Context, uri: Uri) =
            Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
                // Create a new activity, do not allow going back.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Starts [MainActivity] as a redirect of a legacy activity.
         *
         * @param activity  The activity that is requesting the redirection.
         * @param uri       The URI to launch. Should have schema of [Destination.APP_BASE_URI].
         */
        @UiThread
        fun legacyRedirect(activity: Activity, uri: Uri) {
            activity.startActivity(intentWithDestination(activity, uri))
        }

    }

}