/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.featureflags

import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.api.FeatureFlagsProvider
import com.davx5.feature.featureflags.api.FeatureName
import javax.inject.Inject

class DefaultFeatureFlagsProvider @Inject constructor(
    featureFlags: Set<@JvmSuppressWildcards FeatureFlag>
) : FeatureFlagsProvider {
    private val features: Map<FeatureName, FeatureFlag> = featureFlags.associateBy { it.name }

    override fun getFeatureFlag(name: FeatureName): FeatureFlag {
        return features[name] ?: createAlwaysDisabledFeatureFlag(name)
    }

    private fun createAlwaysDisabledFeatureFlag(name: FeatureName): FeatureFlag {
        return object : FeatureFlag {
            override val name: FeatureName = name
            override val isEnabled: Boolean = false
        }
    }
}
