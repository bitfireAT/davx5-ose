/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import javax.inject.Inject

class DistributorPreferencesProvider @Inject constructor() : PushRegistrationManager.DistributorPreferences {
    // No special preferences for OSE flavor, select the first distributor available
    override val packageNames: List<String> = emptyList()
}
