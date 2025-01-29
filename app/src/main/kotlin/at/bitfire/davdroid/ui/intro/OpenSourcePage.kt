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
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.AppTheme
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
            donationPopupIntervalOptions = model.donationPopupIntervalOptions,
            onChangeDontShowFor = model::setDontShowFor
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

        init {
            // Set default value on page load, in case user skips the page
            setDontShowFor()
        }

        /**
         * Set the next time the donation popup should be shown.
         * @param dontShowFor Number of months (30 days) to hide the donation popup for.
         */
        fun setDontShowFor(dontShowFor: Int? = null) {
            val dontShowFor = dontShowFor ?: donationPopupIntervalOptions.first()
            logger.info("Setting next donation popup to $dontShowFor months")
            val month = 30*86400000L            // 30 days (~ 1 month)
            val nextReminder = month * dontShowFor + System.currentTimeMillis()
            settings.putLong(SETTING_NEXT_DONATION_POPUP, nextReminder)
        }

    }

}

@Composable
fun OpenSourcePage(
    donationPopupIntervalOptions: List<Int>,
    onChangeDontShowFor: (Int?) -> Unit = {}
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
                        Constants.HOMEPAGE_URL.buildUpon()
                            .appendPath(Constants.HOMEPAGE_PATH_OPEN_SOURCE)
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
            val radioOptions = donationPopupIntervalOptions.associate { numberOfMonths ->
                pluralStringResource(
                    R.plurals.intro_open_source_dont_show_months,
                    numberOfMonths,
                    numberOfMonths
                ) to numberOfMonths
            }
            RadioButtons(
                radioOptions = radioOptions.keys.toList(),
                onOptionSelected = { option ->
                    onChangeDontShowFor(radioOptions[option])
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
            donationPopupIntervalOptions = listOf(1, 3, 9)
        )
    }
}