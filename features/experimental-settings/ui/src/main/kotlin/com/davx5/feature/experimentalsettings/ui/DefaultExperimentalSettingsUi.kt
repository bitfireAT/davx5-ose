/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.experimentalsettings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.SettingsHeader
import at.bitfire.davdroid.ui.composable.SwitchSetting
import com.davx5.feature.experimentalsettings.api.ExperimentalSettingsUi
import javax.inject.Inject

class DefaultExperimentalSettingsUi @Inject constructor(
    settings: Set<@JvmSuppressWildcards ExperimentalSetting>,
) : ExperimentalSettingsUi {
    private val settings = settings.toList()

    @Composable
    override fun Show() {
        if (settings.isNotEmpty()) {
            ExperimentalSettingsSection(settings)
        }
    }
}

@Composable
private fun ExperimentalSettingsSection(settings: List<ExperimentalSetting>) {
    SettingsHeader(divider = true) {
        Text(stringResource(R.string.app_settings_experimental_title))
    }

    for (setting in settings) {
        setting.Show()
    }
}


@Composable
@Preview
private fun DefaultExperimentalSettingsUi_Preview() {
    AppTheme {
        Surface {
            Column {
                ExperimentalSettingsSection(
                    settings = listOf(experimentalSetting1, experimentalSetting2),
                )
            }
        }
    }
}

private val experimentalSetting1 = object : ExperimentalSetting {
    @Composable
    override fun Show() {
        SwitchSetting(
            checked = true,
            name = "Feature name",
            summaryOn = "Do fancy new thing"
        )
    }
}
private val experimentalSetting2 = object : ExperimentalSetting {
    @Composable
    override fun Show() {
        SwitchSetting(
            checked = false,
            name = "Another experimental feature",
            summaryOn = "Are you brave enough to enable this?"
        )
    }
}
