/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.content.Context
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject

class PushDistributorManager @Inject constructor(
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context
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
     * Sets or removes (disable push) the distributor by updating the UnifiedPush settings.
     *
     * Then invalidates all subscriptions to refresh them.
     *
     * @param pushDistributor  new distributor or `null` to explicitly disable Push
     */
    suspend fun setPushDistributor(pushDistributor: String?) {
        if (pushDistributor != null && getCurrentDistributor() == pushDistributor) {
            // Distributor hasn't changed
            return
        }

        // Disable UnifiedPush and remove all subscriptions
        UnifiedPush.removeDistributor(context)
        invalidateSubscriptions()

        if (pushDistributor != null) {
            // If a distributor was passed, store it and create/register subscriptions
            UnifiedPush.saveDistributor(context, pushDistributor)
        }
    }

    private suspend fun invalidateSubscriptions() = collectionRepository.invalidatePushSubscriptions()

}
