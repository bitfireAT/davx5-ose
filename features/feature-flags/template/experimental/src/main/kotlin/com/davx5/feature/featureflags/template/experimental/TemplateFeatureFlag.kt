/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.experimental

import at.bitfire.davdroid.settings.SettingsManager
import com.davx5.feature.featureflags.api.FeatureFlags
import com.davx5.feature.featureflags.shared.SettingsFeatureFlag
import com.davx5.feature.featureflags.template.api.Template
import javax.inject.Inject

class TemplateFeatureFlag @Inject constructor(
    settingsManager: SettingsManager,
) : SettingsFeatureFlag(
    settingsManager = settingsManager,
    name = FeatureFlags.Template,
    isEnabledByDefault = false,
)
