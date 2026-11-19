/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.newaccountsystem.experimental

import at.bitfire.davdroid.R
import com.davx5.feature.featureflags.shared.FeatureFlagExperimentalSetting
import javax.inject.Inject

class NewAccountSystemExperimentalSetting @Inject constructor(
    featureFlag: NewAccountSystemFeatureFlag,
) : FeatureFlagExperimentalSetting(
    featureFlag = featureFlag,
    settingName = R.string.new_account_system_setting_name,
    summaryOn = R.string.new_account_system_setting_summary_on,
    summaryOff = R.string.new_account_system_setting_summary_off,
)
