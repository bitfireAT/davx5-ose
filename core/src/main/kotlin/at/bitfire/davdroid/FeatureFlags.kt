/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

@Suppress("SimplifyBooleanWithConstants")
object FeatureFlags {
    val useNewAccountSystem = BuildConfig.DEBUG || BuildConfig.PRE_RELEASE
}
