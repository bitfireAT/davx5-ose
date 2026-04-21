/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PushDistributorManager @Inject constructor(
    private val settings: SettingsManager
) {
    /**
     * Provides a flow for the current push distributor preference.
     */
    fun getPushDistributorPreferenceFlow(): Flow<PushDistributorPreference> {
        return settings.getStringFlow(Settings.PUSH_DISTRIBUTOR)
            .map { it?.let(PushDistributorPreference::valueOf) ?: PushDistributorPreference.FCM }
    }

    /**
     * Updates the user's push distributor preference.
     */
    fun setPushDistributorPreference(preference: PushDistributorPreference) {
        settings.putString(Settings.PUSH_DISTRIBUTOR, preference.name)
    }
}
