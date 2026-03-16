/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.push.PushRegistrationManager.DistributorPreferences
import at.bitfire.davdroid.settings.Settings.EXPLICIT_PUSH_DISABLE
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import java.util.logging.Logger
import javax.inject.Inject

class PushDistributorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val settings: SettingsManager,
    private val distributorPreferences: DistributorPreferences,
) {
    /**
     * Get the distributor registered by the user.
     * @return The distributor package name if any, else `null`.
     */
    fun getCurrentDistributor() = UnifiedPush.getSavedDistributor(context)

    /**
     * Get a list of available distributors installed on the system.
     * @return The list of distributor's package name.
     */
    fun getDistributors() = UnifiedPush.getDistributors(context)

    /**
     * Sets or removes (disable push) the distributor.
     *
     * @param pushDistributor  new distributor or `null` to disable Push
     */
    fun setPushDistributor(pushDistributor: String?) {
        // Disable UnifiedPush and remove all subscriptions
        UnifiedPush.removeDistributor(context)
        update()

        if (pushDistributor != null) {
            // If a distributor was passed, store it and create/register subscriptions
            UnifiedPush.saveDistributor(context, pushDistributor)
            update()
        }
    }

    /**
     * Makes sure a distributor is selected if Push is enabled.
     *
     * Uses preferences from [distributorPreferences].
     */
    fun update() {
        val currentDistributor = getCurrentDistributor()
        val isPushDisabled = settings.getBooleanOrNull(EXPLICIT_PUSH_DISABLE)
        if (currentDistributor == null) {
            if (isPushDisabled == true) {
                logger.info("Push is explicitly disabled, no distributor will be selected.")
            } else {
                val availableDistributors = getDistributors()
                if (availableDistributors.isNotEmpty()) {
                    logger.fine("No Push distributor selected, but ${availableDistributors.size} distributors are available.")
                    // select preferred distributor if available, otherwise first available
                    val distributor = distributorPreferences.packageNames.firstNotNullOfOrNull { preferredPackageName ->
                        availableDistributors.find { it == preferredPackageName }
                    } ?: availableDistributors.first()
                    logger.fine("Automatically selecting Push distributor: $distributor")
                    UnifiedPush.saveDistributor(context, distributor)
                } else {
                    logger.fine("No Push distributor selected and no distributors are available.")
                }
            }
        }
    }
}
