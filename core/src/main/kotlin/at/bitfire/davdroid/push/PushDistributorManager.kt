/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * Manages the selection and configuration of UnifiedPush distributors.
 */
class PushDistributorManager @Inject constructor(
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    private val distributorDefaults: Optional<PushDistributorDefaults>,
    private val logger: Logger,
    private val settings: SettingsManager
) {

    /**
     * Get the distributor registered by the user.
     *
     * @return The distributor package name if any, else `null`. Will also return `null`
     * if the previously selected distributor isn't installed anymore.
     */
    fun getCurrentDistributor() = UnifiedPush.getSavedDistributor(context)

    /**
     * Get a list of available distributors installed on the system.
     * @return The list of distributor's package name.
     */
    fun getDistributors() = UnifiedPush.getDistributors(context)

    /**
     * Sets or removes (disable push) the distributor by updating the settings
     * and then calling [updateDistributor].
     *
     * @param pushDistributor  new distributor or `null` to explicitly disable Push
     */
    suspend fun setPushDistributor(pushDistributor: String?) {
        // Update "disable push" setting
        if (pushDistributor == null)
            settings.putBoolean(Settings.PUSH_DISABLE, true)
        else {
            settings.remove(Settings.PUSH_DISABLE)

            if (pushDistributor != getCurrentDistributor()) {
                // if the distributor is changed, invalidate registered subscriptions
                // because they may have been registered to the old endpoint
                invalidateSubscriptions()

                // store new distributor
                UnifiedPush.saveDistributor(context, pushDistributor)
            }
        }

        updateDistributor()
    }

    /**
     * Ensures that the UnifiedPush distributor is set according to the settings:
     *
     * - [Settings.PUSH_DISABLE] to disable push,
     * - preferred push distributor for initial selection from [distributorDefaults].
     */
    suspend fun updateDistributor() {
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

                    // invalidate subscription registrations because they may have been registered to the old endpoint
                    invalidateSubscriptions()

                    logger.fine("Automatically selecting Push distributor: $distributor")
                    UnifiedPush.saveDistributor(context, distributor)

                } else
                    logger.fine("Can't select a push distributor because none are available.")
            }
        }
    }

    private suspend fun invalidateSubscriptions() =
        collectionRepository.invalidatePushSubscriptions()

}
