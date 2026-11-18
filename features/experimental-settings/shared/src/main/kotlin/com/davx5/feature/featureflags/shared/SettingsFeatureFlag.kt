/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.shared

import at.bitfire.davdroid.settings.SettingsManager
import com.davx5.feature.featureflags.api.FeatureName
import kotlinx.coroutines.flow.Flow

abstract class SettingsFeatureFlag(
    private val settingsManager: SettingsManager,
    override val name: FeatureName,
    private val isEnabledByDefault: Boolean,
) : MutableFeatureFlag {
    private val settingsKey = "featureFlags.${name.value}"

    override var isEnabled: Boolean
        get() = settingsManager.getBooleanOrNull(settingsKey) ?: isEnabledByDefault
        set(value) = settingsManager.putBoolean(settingsKey, value)

    override val isEnabledFlow: Flow<Boolean> = settingsManager.getBooleanFlow(settingsKey, isEnabledByDefault)
}
