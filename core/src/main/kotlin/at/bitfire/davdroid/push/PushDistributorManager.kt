/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import java.util.logging.Logger
import javax.inject.Inject

class PushDistributorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
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
     * Provides a flow for the current push distributor preference.
     */
    fun getPushDistributorPreference(): PushDistributorPreference {
        return settings.getString(Settings.PUSH_DISTRIBUTOR)
            ?.let(PushDistributorPreference::valueOf)
            // By default, if none set, try to use FCM
            ?: PushDistributorPreference.FCM
    }

    /**
     * Updates the user's push distributor preference.
     */
    fun setPushDistributorPreference(preference: PushDistributorPreference) {
        settings.putString(Settings.PUSH_DISTRIBUTOR, preference.name)
    }

    /**
     * Checks whether there's a distributor available or not.
     * If the user has disabled Push manually, this function will always return `false`.
     */
    fun distributorAvailable(): Boolean {
        if (getPushDistributorPreference() == PushDistributorPreference.Disabled) return false
        return UnifiedPush.getSavedDistributor(context) != null
    }

    fun resolveDistributor() {
        when (val res = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found ->  {
                logger.info("Found default distributor")
                UnifiedPush.saveDistributor(context, res.packageName)
                // TODO: Must run update in the registration manager
            }
            ResolvedDistributor.NoneAvailable -> {
                logger.info( "No default distributor")
                setPushDistributorPreference(PushDistributorPreference.Disabled)
            }
            ResolvedDistributor.ToSelect -> {
                logger.info("Default distributor to select")
                // TODO: Request user to select distributor
            }
        }
    }
}
