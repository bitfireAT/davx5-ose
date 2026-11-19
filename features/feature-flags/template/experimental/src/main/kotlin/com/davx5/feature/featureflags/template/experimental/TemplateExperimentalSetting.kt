/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.experimental

import at.bitfire.davdroid.R
import com.davx5.feature.featureflags.shared.FeatureFlagExperimentalSetting
import javax.inject.Inject

class TemplateExperimentalSetting @Inject constructor(
    featureFlag: TemplateFeatureFlag,
) : FeatureFlagExperimentalSetting(
    featureFlag = featureFlag,
    // TODO: Add appropriate strings to /core/src/main/res/values/strings.xml and reference them here
    settingName = R.string.app_settings_logging,
    summaryOn = R.string.app_settings_logging_on,
    summaryOff = R.string.app_settings_logging_off,
)
