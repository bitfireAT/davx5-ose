/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.featureflags

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.api.FeatureName
import org.junit.Test

class DefaultFeatureFlagsProviderTest {
    private val knownFeatureFlag = object : FeatureFlag {
        override val name
            get() = FeatureName("knownFeature")

        override val isEnabled = true
    }

    private val provider = DefaultFeatureFlagsProvider(
        featureFlags = setOf(knownFeatureFlag),
    )

    @Test
    fun `should return known feature flag`() {
        val result = provider.getFeatureFlag(knownFeatureFlag.name)

        assertThat(result).isSameInstanceAs(knownFeatureFlag)
    }

    @Test
    fun `should return disabled FeatureFlag instance for unknown feature name`() {
        val result = provider.getFeatureFlag(FeatureName("unknownFeature"))

        assertThat(result.isEnabled).isFalse()
    }
}
