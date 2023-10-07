/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ObservableBoolean
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.IntroOpenSourceBinding
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.ui.intro.OpenSourceFragment.Model.Companion.SETTING_NEXT_DONATION_POPUP
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@AndroidEntryPoint
class OpenSourceFragment: Fragment() {

    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = IntroOpenSourceBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.model = model

        binding.text.text = getString(R.string.intro_open_source_text, getString(R.string.app_name))
        binding.moreInfo.setOnClickListener {
            UiUtils.launchUri(requireActivity(), App.homepageUrl(requireActivity()).buildUpon()
                    .appendPath("donate")
                    .build())
        }

        return binding.root
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
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
                100
            else
                0

        override fun create() = OpenSourceFragment()

    }

}