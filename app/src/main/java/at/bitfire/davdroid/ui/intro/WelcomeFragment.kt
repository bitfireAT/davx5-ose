package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.Settings

class WelcomeFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.intro_welcome, container, false)


    class Factory : IIntroFragmentFactory {

        override fun shouldBeShown(context: Context, settings: Settings) = IIntroFragmentFactory.ShowMode.SHOW_NOT_ALONE

        override fun create() = WelcomeFragment()

    }

}