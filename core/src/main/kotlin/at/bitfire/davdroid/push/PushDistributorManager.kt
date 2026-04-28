/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Allows to manage (get/set) the UnifiedPush distributor.
 */
class PushDistributorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val settingsManager: SettingsManager
) {

    // methods with logic to choose the distributor

    /**
     * Gets the UnifiedPush distributor to use for updating the subscriptions,
     * according to this logic:
     *
     * - If [isPushEnabled] is true: return the current UnifiedPush distributor (if any).
     * - If [isPushEnabled] is false: return `null`.
     *
     * If the currently set UnifiedPush distributor is not available, this method
     * returns `null` and UnifiedPush also calls `MessagingReceiver.onUnregistered`
     * (**side effect**).
     *
     * @return package name of the current distributor, or `null` if push support is disabled or no distributor is set
     */
    fun getDistributorToUse(): String? {
        val pushEnabled = isPushEnabled()
        if (!pushEnabled) {
            logger.fine("Push is disabled. Unregistering all instance, and unsubscribing from all services")
            return null
        }

        // get ACK distributor: saved distributor, which is correctly configured
        val savedDistributor = UnifiedPush.getAckDistributor(context)
        if (savedDistributor != null) return savedDistributor

        // there's no distributor saved, try to resolve it with UP
        when (val res = UnifiedPush.resolveDefaultDistributor(context)) {
            // If Found is returned, the new distributor has been saved, and getAckDistributor will fetch it in the next call
            is ResolvedDistributor.Found -> return res.packageName

            // There are multiple distributors available, the user must choose one
            ResolvedDistributor.ToSelect -> {
                logger.warning("There's multiple distributors available, but no distributor is preferred.")
                return null
            }

            // There's no custom distributor installed, and FCM is not available
            ResolvedDistributor.NoneAvailable -> {
                logger.warning("There's no distributor available, push is enabled, and there are servers advertising push support.")
                return null
            }
        }
    }


    // plain UnifiedPush access methods

    fun getDistributors() = UnifiedPush.getDistributors(context)

    /**
     * Sets the UnifiedPush distributor and enables push in app settings.
     *
     * Note: This method does _not_ update the actual subscriptions. You
     * may want to call [PushRegistrationManager.update] after calling this method.
     *
     * @param pushDistributor  package name of the new distributor
     */
    fun setPushDistributorAndEnablePush(pushDistributor: String) {
        // Store the new distributor
        UnifiedPush.saveDistributor(context, pushDistributor)

        // Enable push
        settingsManager.putBoolean(Settings.PUSH_ENABLED, true)
    }


    // methods to access settings

    /**
     * Checks whether push notifications are enabled in settings.
     *
     * @return `true` if push notifications are enabled (default), `false` if disabled.
     */
    fun isPushEnabled(): Boolean =
        settingsManager.getBooleanOrNull(Settings.PUSH_ENABLED) ?: true

    /**
     * Sets whether push support is enabled or disabled (like from a switch in the UI).
     *
     * 1. Updates the respective setting ([Settings.PUSH_ENABLED]).
     * 2. Updates the push distributor.
     *
     *    - When [enabled] is true: we don't know which distributor should be used, so it can't be set.
     *    - When [enabled] is false: removes the current UnifiedPush distributor (if any).
     *
     * Note: This method does _not_ update the actual subscriptions. You
     * may want to call [PushRegistrationManager.update] after calling this method.
     *
     * @param enabled  whether push notifications should be enabled
     */
    fun setPushEnabled(enabled: Boolean) {
        settingsManager.putBoolean(Settings.PUSH_ENABLED, enabled)

        if (!enabled)
            UnifiedPush.removeDistributor(context)
    }

}
