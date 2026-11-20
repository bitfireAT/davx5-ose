/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.newaccountsystem.experimental

import at.bitfire.davdroid.settings.SettingsManager
import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.newaccountsystem.api.NewAccountSystem
import com.davx5.feature.featureflags.shared.SettingsFeatureFlag
import javax.inject.Inject

class NewAccountSystemFeatureFlag @Inject constructor(
    settingsManager: SettingsManager,
) : SettingsFeatureFlag(
    settingsManager = settingsManager,
    name = FeatureFlags.NewAccountSystem,
    isEnabledByDefault = false,
)
