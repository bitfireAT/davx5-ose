/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

/**
 * Allows to manage (get/set) the UnifiedPush distributor.
 */
class PushDistributorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {

    /**
     * Gets the current UnifiedPush distributor, if push support is enabled.
     *
     * @return package name of the current distributor, or `null` if push support is disabled or no distributor is set
     */
    fun getCurrentDistributor() =
        if (isPushEnabled())
            UnifiedPush.getSavedDistributor(context)
        else
            null

    fun getDistributors() = UnifiedPush.getDistributors(context)

    /**
     * Sets the UnifiedPush distributor.
     *
     * Note: This method does _not_ update the actual subscriptions because that task
     * belongs to [PushRegistrationManager]. You probably want to call
     * [PushRegistrationManager.update] after calling this method.
     *
     * @param pushDistributor  package name of the new distributor
     */
    fun setPushDistributor(pushDistributor: String) {
        // Store the new distributor
        UnifiedPush.saveDistributor(context, pushDistributor)
    }

    fun isPushEnabled(): Boolean =
        settingsManager.getBooleanOrNull(Settings.PUSH_ENABLED) ?: true

    /**
     * Sets whether push support is enabled or disabled.
     *
     * - Updates the respective setting ([Settings.PUSH_ENABLED]).
     * - If disabled: removes the current UnifiedPush distributor (if any).
     *
     * Note: This method does _not_ update the actual subscriptions because that task
     * belongs to [PushRegistrationManager]. You probably want to call
     * [PushRegistrationManager.update] after calling this method.
     *
     * @param enabled  whether push notifications should be enabled
     */
    fun setPushEnabled(enabled: Boolean) {
        settingsManager.putBoolean(Settings.PUSH_ENABLED, enabled)

        if (!enabled)
            UnifiedPush.removeDistributor(context)
    }

}
