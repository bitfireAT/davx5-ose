/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.databinding.ObservableBoolean
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.OpenSourceFragment.Model.Companion.SETTING_NEXT_DONATION_POPUP
import at.bitfire.davdroid.ui.widget.CardWithImage
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class OpenSourceFragment: Fragment() {

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MdcTheme {
                    var dontShow by remember { mutableStateOf(model.dontShow.get()) }

                    FragmentContent(
                        dontShow = dontShow,
                        onChangeDontShow = {
                            model.dontShow.set(it)
                            dontShow = it
                        }
                    )
                }
            }
        }
    }


    @Preview(
        showBackground = true,
        showSystemUi = true
    )
    @Composable
    fun FragmentContent(
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
                            App.homepageUrl(requireActivity())
                                .buildUpon()
                                .appendPath("donate")
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
                        text = stringResource(R.string.intro_battery_dont_show),
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


    class Factory @Inject constructor(
        val settingsManager: SettingsManager
    ): IntroFragmentFactory {

        override fun getOrder(context: Context) =
            if (System.currentTimeMillis() > (settingsManager.getLongOrNull(SETTING_NEXT_DONATION_POPUP) ?: 0))
                500
            else
                0

        override fun create() = OpenSourceFragment()

    }

}