/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.CardWithImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

class BackupsPage @Inject constructor(
    val settingsManager: SettingsManager
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy =
        if (Model.backupsAccepted(settingsManager))
            ShowPolicy.DONT_SHOW
        else
            ShowPolicy.SHOW_ALWAYS

    @Composable
    override fun ComposePage() {
        val model = hiltViewModel<Model>()
        val accepted by model.backupsAcceptedFlow.collectAsStateWithLifecycle(false)
        BackupsPage(
            accepted = accepted,
            updateAccepted = model::setBackupsAccepted
        )
    }


    @HiltViewModel
    class Model @Inject constructor(
        private val settings: SettingsManager
    ): ViewModel() {

        val backupsAcceptedFlow = settings.getBooleanFlow(SETTING_BACKUPS_ACCEPTED, false)

        fun setBackupsAccepted(accepted: Boolean) {
            settings.putBoolean(SETTING_BACKUPS_ACCEPTED, accepted)
        }

        companion object {

            /** boolean setting (default: false) */
            const val SETTING_BACKUPS_ACCEPTED = "intro_backups_accepted"

            fun backupsAccepted(settingsManager: SettingsManager): Boolean =
                settingsManager.getBooleanOrNull(SETTING_BACKUPS_ACCEPTED) ?: false

        }

    }

}

@Composable
fun BackupsPage(
    accepted: Boolean,
    updateAccepted: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        CardWithImage(
            title = stringResource(R.string.intro_backups_title),
            icon = Icons.Outlined.Backup,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.intro_backups_important),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.intro_backups_something_wrong, stringResource(R.string.app_name))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            ) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = updateAccepted
                )
                Text(
                    text = stringResource(R.string.intro_backups_accept),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .clickable { updateAccepted(!accepted) }
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun BackupsPagePreview() {
    AppTheme {
        BackupsPage(
            accepted = true,
            updateAccepted = {}
        )
    }
}