package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.SettingsManager
import kotlinx.android.synthetic.main.intro_welcome.view.*


class WelcomeFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.intro_welcome, container, false)

        v.logo.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(300)
        }
        v.yourDataYourChoice.apply {
            translationX = -1000f
            animate()
                .translationX(0f)
                .setDuration(300)
        }
        v.takeControl.apply {
            translationX = 1000f
            animate()
                .translationX(0f)
                .setDuration(300)
        }

        return v
    }


    class Factory : IIntroFragmentFactory {

        override fun shouldBeShown(context: Context, settingsManager: SettingsManager) = IIntroFragmentFactory.ShowMode.SHOW_NOT_ALONE

        override fun create() = WelcomeFragment()

    }

}