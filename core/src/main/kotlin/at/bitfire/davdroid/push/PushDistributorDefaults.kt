/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

/**
 * Allows preferring certain distributors to others.
 */
interface PushDistributorDefaults {

    /**
     * The preferred distributor for handling push notifications.
     * If set, this distributor will be prioritized over others when available.
     * If null, no specific distributor is preferred.
     */
    val preferredDistributor: String?

}
