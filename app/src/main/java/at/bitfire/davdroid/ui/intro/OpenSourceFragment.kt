package at.bitfire.davdroid.ui.intro

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ObservableBoolean
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.App
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.IntroOpenSourceBinding
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.ui.intro.OpenSourceFragment.Model.Companion.SETTING_NEXT_DONATION_POPUP

class OpenSourceFragment: Fragment() {

    lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this).get(Model::class.java)
    }

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


    class Model(app: Application): AndroidViewModel(app) {

        companion object {
            const val SETTING_NEXT_DONATION_POPUP = "time_nextDonationPopup"
        }

        val dontShow = object: ObservableBoolean() {
            val settings = Settings(getApplication())
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

    class Factory: IIntroFragmentFactory {

        override fun shouldBeShown(context: Context, settings: Settings) =
                if (System.currentTimeMillis() > (settings.getLong(SETTING_NEXT_DONATION_POPUP) ?: 0))
                    IIntroFragmentFactory.ShowMode.SHOW
                else
                    IIntroFragmentFactory.ShowMode.DONT_SHOW

        override fun create() = OpenSourceFragment()

    }

}