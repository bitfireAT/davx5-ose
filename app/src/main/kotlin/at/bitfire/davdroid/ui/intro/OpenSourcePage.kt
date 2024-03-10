/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.widget.CardWithImage
import at.bitfire.davdroid.ui.widget.SafeAndroidUriHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class OpenSourcePage : IntroPage {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OpenSourcePageEntryPoint {
        fun settingsManager(): SettingsManager
    }

    override fun getShowPolicy(application: Application): IntroPage.ShowPolicy {
        val settingsManager = EntryPointAccessors.fromApplication(application, OpenSourcePageEntryPoint::class.java).settingsManager()

        return if (System.currentTimeMillis() > (settingsManager.getLongOrNull(Model.SETTING_NEXT_DONATION_POPUP) ?: 0))
            IntroPage.ShowPolicy.SHOW_ALWAYS
        else
            IntroPage.ShowPolicy.DONT_SHOW
    }

    @Composable
    override fun ComposePage() {
        Page()
    }

    @Composable
    private fun Page(model: Model = viewModel()) {
        var dontShow by remember { mutableStateOf(model.dontShow.get()) }

        val uriHandler = SafeAndroidUriHandler(LocalContext.current)
        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
            PageContent(
                dontShow = dontShow,
                onChangeDontShow = {
                    model.dontShow.set(it)
                    dontShow = it
                }
            )
        }
    }

    @Preview(
        showBackground = true,
        showSystemUi = true
    )
    @Composable
    fun PageContent(
        dontShow: Boolean = false,
        onChangeDontShow: (Boolean) -> Unit = {}
    ) {
        val context = LocalContext.current
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShow,
                        onCheckedChange = onChangeDontShow
                    )
                    Text(
                        text = stringResource(R.string.intro_open_source_dont_show),
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.clickable { onChangeDontShow(!dontShow) }
                    )
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        val settings: SettingsManager
    ): ViewModel() {

        companion object {
            const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"
        }

        val dontShow = object: ObservableBoolean() {
            override fun set(dontShowAgain: Boolean) {
                if (dontShowAgain) {
                    val nextReminder = System.currentTimeMillis() + 90*86400000L     // 90 days (~ 3 months)
                    settings.putLong(SETTING_NEXT_DONATION_POPUP, nextReminder)
                } else
                    settings.remove(SETTING_NEXT_DONATION_POPUP)
                super.set(dontShowAgain)
            }
        }

    }

}