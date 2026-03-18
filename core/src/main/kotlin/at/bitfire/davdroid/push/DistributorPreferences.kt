/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

/**
 * Allows preferring certain distributors over others.
 */
interface DistributorPreferences {
    /**
     * A list of package names ordered by preference.
     * If any of those is available, it will be automatically selected.
     * Otherwise, another available distributor will be chosen automatically.
     */
    val packageNames: List<String>
}
