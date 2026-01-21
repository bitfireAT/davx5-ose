/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.settings.SettingsManager
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
@Preview
fun BackupsPage(
    accepted: Boolean = false,
    updateAccepted: (Boolean) -> Unit = {}
) {
    // TODO
}