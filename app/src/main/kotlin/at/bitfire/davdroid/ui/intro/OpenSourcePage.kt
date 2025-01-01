/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.composable.CardWithImage
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private fun Page(model: Model = hiltViewModel()) {
        val dontShow by model.dontShow.collectAsStateWithLifecycle(false)
        OpenSourcePage(
            dontShow = dontShow,
            onChangeDontShow = {
                model.setDontShow(it)
            }
        )
    }

    @HiltViewModel
    class Model @Inject constructor(
        val settings: SettingsManager
    ): ViewModel() {

        companion object {
            const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"
        }

        val dontShow = settings.containsKeyFlow(SETTING_NEXT_DONATION_POPUP)

        fun setDontShow(dontShowAgain: Boolean) {
            if (dontShowAgain) {
                val nextReminder = System.currentTimeMillis() + 90*86400000L     // 90 days (~ 3 months)
                settings.putLong(SETTING_NEXT_DONATION_POPUP, nextReminder)
            } else
                settings.remove(SETTING_NEXT_DONATION_POPUP)
        }

    }

}

@Preview
@Composable
fun OpenSourcePage(
    dontShow: Boolean = false,
    onChangeDontShow: (Boolean) -> Unit = {}
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
            )
        ) {
            OutlinedButton(
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
                Text(stringResource(R.string.intro_open_source_details))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = dontShow,
                    onCheckedChange = onChangeDontShow
                )
                Text(
                    text = stringResource(R.string.intro_open_source_dont_show),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { onChangeDontShow(!dontShow) }
                        .weight(1f)
                )
            }
        }
    }
}