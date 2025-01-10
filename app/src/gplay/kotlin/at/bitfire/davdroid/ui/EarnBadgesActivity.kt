/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.PlayClient
import at.bitfire.davdroid.settings.SettingsManager
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import java.util.logging.Level
import java.util.logging.Logger

@AndroidEntryPoint
class EarnBadgesActivity() : AppCompatActivity() {

    @Inject lateinit var logger: Logger
    @Inject lateinit var playClientFactory: PlayClient.Factory
    @Inject lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show rating API dialog one week after the app has been installed
        if (shouldShowRatingRequest(this, settingsManager))
            showRatingRequest(ReviewManagerFactory.create(this))

        setContent {
            AppTheme {
                val uriHandler = LocalUriHandler.current
                EarnBadgesScreen(
                    playClient = playClientFactory.create(this),
                    onStartRating = { uriHandler.openUri(
                        Uri.parse("market://details?id=$packageName")
                        .buildUpon()
                        .withStatParams("EarnBadgesActivity")
                        .build().toString()
                    ) },
                    onNavUp = ::onNavigateUp
                )
            }
        }
    }

    /**
     * Starts the in-app review API to trigger the review request
     * Once the user has rated the app, it will still trigger, but won't show up anymore.
     */
    fun showRatingRequest(manager: ReviewManager) {
        // Try prompting for review/rating
        manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
            logger.log(Level.INFO, "Launching app rating flow")
            manager.launchReviewFlow(this, reviewInfo)
        }
    }

    companion object {
        internal const val LAST_REVIEW_PROMPT = "lastReviewPrompt"

        /** Time between rating interval prompts in milliseconds */
        private const val RATING_INTERVAL = 2*7*24*60*60*1000 // Two weeks

        /**
         * Determines whether we should show a rating prompt to the user depending on whether
         * - the RATING_INTERVAL has passed once after first installation, or
         * - the last rating prompt is older than RATING_INTERVAL
         *
         * If the return value is `true`, also updates the `LAST_REVIEW_PROMPT` setting to the current time
         * so that the next call won't be `true` again for the time specified in `RATING_INTERVAL`.
         */
        fun shouldShowRatingRequest(context: Context, settings: SettingsManager): Boolean {
            val now = currentTime()
            val firstInstall = installTime(context)
            val lastPrompt = settings.getLongOrNull(LAST_REVIEW_PROMPT) ?: now
            val shouldShowRatingRequest = (now > firstInstall + RATING_INTERVAL) && (now > lastPrompt + RATING_INTERVAL)
            Logger.getGlobal().info("now=$now, firstInstall=$firstInstall, lastPrompt=$lastPrompt, shouldShowRatingRequest=$shouldShowRatingRequest")
            if (shouldShowRatingRequest)
                settings.putLong(LAST_REVIEW_PROMPT, now)
            return shouldShowRatingRequest
        }

        fun currentTime() = System.currentTimeMillis()
        fun installTime(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }

}