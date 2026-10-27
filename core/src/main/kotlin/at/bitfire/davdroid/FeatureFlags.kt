/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

@Suppress("SimplifyBooleanWithConstants", "MayBeConstant")
object FeatureFlags {
    // Enable for development builds
    val useNewAccountSystem = BuildConfig.DEBUG
}
