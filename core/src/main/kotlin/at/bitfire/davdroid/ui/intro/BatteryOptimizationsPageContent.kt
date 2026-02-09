/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.composable.AppTheme
import java.util.Locale

@Composable
fun BatteryOptimizationsPageContent(
    model: BatteryOptimizationsPageModel = viewModel()
) {
    val ignoreBatteryOptimizationsResultLauncher = rememberLauncherForActivityResult(
        BatteryOptimizationsPage.IgnoreBatteryOptimizationsContract
    ) {
        model.checkBatteryOptimizations()
    }

    val hintBatteryOptimizations by model.hintBatteryOptimizations.collectAsStateWithLifecycle(false)
    val uiState = model.uiState
    val context = LocalContext.current
    LaunchedEffect(uiState) {
        if (uiState.shouldBeExempted && !uiState.isExempted)
            ignoreBatteryOptimizationsResultLauncher.launch(context.packageName)
    }

    val hintAutostartPermission by model.hintAutostartPermission.collectAsStateWithLifecycle(false)
    BatteryOptimizationsPageContent(
        dontShowBattery = hintBatteryOptimizations == false,
        onChangeDontShowBattery = model::updateHintBatteryOptimizations,
        isExempted = uiState.isExempted,
        shouldBeExempted = uiState.shouldBeExempted,
        onChangeShouldBeExempted = model::updateShouldBeExempted,
        dontShowAutostart = hintAutostartPermission == false,
        onChangeDontShowAutostart = model::updateHintAutostartPermission,
        manufacturerWarning = BatteryOptimizationsPageModel.manufacturerWarning
    )
}

@Composable
fun BatteryOptimizationsPageContent(
    dontShowBattery: Boolean,
    onChangeDontShowBattery: (Boolean) -> Unit = {},
    isExempted: Boolean,
    shouldBeExempted: Boolean,
    onChangeShouldBeExempted: (Boolean) -> Unit = {},
    dontShowAutostart: Boolean,
    onChangeDontShowAutostart: (Boolean) -> Unit = {},
    manufacturerWarning: Boolean
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.intro_battery_title),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = shouldBeExempted,
                        onCheckedChange = {
                            // Only accept click events if not whitelisted
                            if (!isExempted) {
                                onChangeShouldBeExempted(it)
                            }
                        },
                        enabled = !dontShowBattery
                    )
                }
                Text(
                    text = stringResource(
                        R.string.intro_battery_text,
                        stringResource(R.string.app_name)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                AnimatedVisibility(visible = !isExempted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowBattery,
                            onCheckedChange = { onChangeDontShowBattery(dontShowBattery) },
                            enabled = !isExempted
                        )
                        Text(
                            text = stringResource(R.string.intro_battery_dont_show),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { onChangeDontShowBattery(dontShowBattery) }
                        )
                    }
                }
            }
        }
        if (manufacturerWarning) {
            Card(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.intro_autostart_title,
                            Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.intro_autostart_text),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            uriHandler.openUri(
                                ExternalUris.Homepage.baseUrl.buildUpon()
                                    .appendPath(ExternalUris.Homepage.PATH_FAQ)
                                    .appendPath(ExternalUris.Homepage.PATH_FAQ_SYNC_NOT_RUN)
                                    .appendQueryParameter(
                                        "manufacturer",
                                        Build.MANUFACTURER.lowercase(Locale.ROOT)
                                    )
                                    .withStatParams(context, "BatteryOptimizationsPage")
                                    .build().toString()
                            )
                        }
                    ) {
                        Text(stringResource(R.string.intro_more_info))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAutostart,
                            onCheckedChange = { onChangeDontShowAutostart(dontShowAutostart) }
                        )
                        Text(
                            text = stringResource(R.string.intro_autostart_dont_show),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { onChangeDontShowAutostart(dontShowAutostart) }
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(
                R.string.intro_leave_unchecked,
                stringResource(R.string.app_settings_reset_hints)
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun BatteryOptimizationsContent_Preview() {
    AppTheme {
        BatteryOptimizationsPageContent(
            dontShowBattery = true,
            isExempted = false,
            shouldBeExempted = true,
            dontShowAutostart = false,
            manufacturerWarning = true
        )
    }
}
