/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import javax.inject.Inject

class PushCoordinator @Inject constructor(
    private val pushDistributorManager: PushDistributorManager,
    private val pushRegistrationManager: PushRegistrationManager
) {
    /**
     * Sets or removes (disable push) the distributor by updating the UnifiedPush settings.
     *
     * Then invalidates all subscriptions to refresh them and re-registers subscriptions.
     *
     * @param pushDistributor  new distributor or `null` to explicitly disable Push
     */
    suspend fun setPushDistributor(pushDistributor: String?) {
        @Suppress("DEPRECATION")
        pushDistributorManager.setPushDistributor(pushDistributor)
        pushRegistrationManager.update()
    }
}
