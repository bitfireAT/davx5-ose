/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.shared

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import at.bitfire.davdroid.ui.composable.SwitchSetting
import com.davx5.feature.experimentalsettings.ui.ExperimentalSetting
import com.davx5.feature.featureflags.api.FeatureFlagsProvider
import com.davx5.feature.featureflags.api.FeatureName

abstract class FeatureFlagExperimentalSetting(
    featureFlagsProvider: FeatureFlagsProvider,
    featureName: FeatureName,
    @StringRes private val settingName: Int,
    @StringRes private val summaryOn: Int?,
    @StringRes private val summaryOff: Int?,
) : ExperimentalSetting {
    private val featureFlag = featureFlagsProvider.getFeatureFlag(featureName) as MutableFeatureFlag

    @Composable
    override fun Show() {
        val isFeatureEnabled = featureFlag.isEnabledFlow.collectAsState(featureFlag.isEnabled)

        SwitchSetting(
            checked = isFeatureEnabled.value,
            name = stringResource(settingName),
            summaryOn = summaryOn?.let { stringResource(it) },
            summaryOff = summaryOff?.let { stringResource(it) },
            onCheckedChange = { checked ->
                featureFlag.isEnabled = checked
            }
        )
    }
}
