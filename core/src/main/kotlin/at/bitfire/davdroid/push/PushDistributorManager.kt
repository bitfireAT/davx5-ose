/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class PushDistributorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val distributorDefaults: Optional<PushDistributorDefaults>,
    private val logger: Logger,
    private val settings: SettingsManager
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
     * @param pushDistributor  new distributor or `null` to explicitly disable Push
     */
    fun setPushDistributor(pushDistributor: String?) {
        // Update "disable push" setting
        if (pushDistributor == null)
            settings.putBoolean(Settings.PUSH_DISABLE, true)
        else {
            settings.remove(Settings.PUSH_DISABLE)

            // If a distributor was passed, store it and create/register subscriptions
            UnifiedPush.saveDistributor(context, pushDistributor)
        }

        update()
    }

    /**
     * Makes sure a distributor is selected if Push is enabled
     * (takes [Settings.PUSH_DISABLE] into account).
     *
     * Uses preferred push distributor from [distributorDefaults].
     */
    fun update() {
        val pushDisabled = settings.getBooleanOrNull(Settings.PUSH_DISABLE) ?: false
        if (pushDisabled) {
            // push has been disabled by user
            logger.info("Push is explicitly disabled, no distributor will be selected.")
            UnifiedPush.removeDistributor(context)

        } else {
            // push has not been disabled by user
            val currentDistributor = getCurrentDistributor()

            // If no distributor is selected yet, select the preferred (or first available) one
            if (currentDistributor == null) {
                val availableDistributors = getDistributors()
                if (availableDistributors.isNotEmpty()) {
                    logger.fine("No Push distributor selected, but ${availableDistributors.size} distributors are available.")

                    // preferred distributor (varies by build variant, may be null)
                    val preferredDistributor = distributorDefaults.getOrNull()?.preferredDistributor

                    // select preferred distributor if available, otherwise first available
                    val distributor =
                        preferredDistributor.takeIf { availableDistributors.contains(preferredDistributor) }
                            ?: availableDistributors.first()

                    logger.fine("Automatically selecting Push distributor: $distributor")
                    UnifiedPush.saveDistributor(context, distributor)
                } else
                    logger.fine("Can't select a push distributor because none are available.")
            }
        }
    }

}
