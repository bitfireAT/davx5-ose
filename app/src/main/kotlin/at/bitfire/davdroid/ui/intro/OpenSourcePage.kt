/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.RadioButtons
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.logging.Logger
import javax.inject.Inject

class OpenSourcePage @Inject constructor(
    private val settingsManager: SettingsManager
): IntroPage() {

    override fun getShowPolicy(): ShowPolicy {
        return if (System.currentTimeMillis() > (settingsManager.getLongOrNull(Model.SETTING_NEXT_DONATION_POPUP) ?: 0))
            ShowPolicy.SHOW_ALWAYS
        else
            ShowPolicy.DONT_SHOW
    }

    @Composable
    override fun ComposePage() {
        Page()
    }

    @Composable
    private fun Page(model: Model = viewModel()) {
        OpenSourcePage(
            dontShowForMonthsOptions = model.donationPopupIntervalOptions,
            onDontShowForMonths = model::setDontShowForMonths
        )
    }

    @HiltViewModel
    class Model @Inject constructor(
        private val settings: SettingsManager,
        private val logger: Logger
    ): ViewModel() {

        companion object {
            const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"
        }

        /**
         * Possible number of months (30 days) to hide the donation popup for.
         */
        val donationPopupIntervalOptions = listOf(1, 3, 9)

        /**
         * Set the next time the donation popup should be shown.
         *
         * @param months Number of months (30 days) to hide the donation popup for.
         */
        fun setDontShowForMonths(months: Int) {
            logger.info("Setting next donation popup to $months months")
            val oneMonth = 30*86400000L            // 30 days (~ 1 month)
            val nextReminder = oneMonth * months + System.currentTimeMillis()
            settings.putLong(SETTING_NEXT_DONATION_POPUP, nextReminder)
        }

    }

}

@Composable
fun OpenSourcePage(
    dontShowForMonthsOptions: List<Int>,
    onDontShowForMonths: (Int) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        CardWithImage(
            title = stringResource(R.string.intro_open_source_title),
            image = painterResource(R.drawable.intro_open_source),
            imageContentScale = ContentScale.Inside,
            message = stringResource(
                R.string.intro_open_source_text,
                stringResource(R.string.app_name)
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                onClick = {
                    uriHandler.openUri(
                        ExternalUris.Homepage.baseUrl.buildUpon()
                            .appendPath(ExternalUris.Homepage.PATH_OPEN_SOURCE)
                            .withStatParams("OpenSourcePage")
                            .build()
                            .toString()
                    )
                }
            ) {
                Text(
                    stringResource(R.string.intro_open_source_details)
                )
            }

            Text(
                text = stringResource(R.string.intro_open_source_dont_show),
                style = MaterialTheme.typography.bodyLarge
            )
            RadioButtons(
                options = dontShowForMonthsOptions.map { months ->
                    pluralStringResource(R.plurals.intro_open_source_dont_show_months, months, months)
                },
                onOptionSelected = { idx ->
                    val months = dontShowForMonthsOptions[idx]
                    onDontShowForMonths(months)
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )

        }
    }
}

@Preview
@Composable
fun OpenSourcePagePreview() {
    AppTheme {
        OpenSourcePage(
            dontShowForMonthsOptions = listOf(1, 3, 9)
        )
    }
}