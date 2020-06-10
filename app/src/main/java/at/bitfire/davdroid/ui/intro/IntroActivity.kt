package at.bitfire.davdroid.ui.intro

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.intro.IIntroFragmentFactory.ShowMode
import com.github.paolorotolo.appintro.AppIntro2
import java.util.*

class IntroActivity: AppIntro2() {

    companion object {

        private val serviceLoader = ServiceLoader.load(IIntroFragmentFactory::class.java)!!
        private val introFragmentFactories = serviceLoader.toList()
        init {
            introFragmentFactories.forEach {
                Logger.log.fine("Registered intro fragment ${it::class.java}")
            }
        }

        fun shouldShowIntroActivity(context: Context): Boolean {
            val settings = SettingsManager.getInstance(context)
            return introFragmentFactories.any {
                val show = it.shouldBeShown(context, settings)
                Logger.log.fine("Intro fragment $it: showMode=$show")
                show == ShowMode.SHOW
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsManager.getInstance(this)

        val factoriesWithMode = introFragmentFactories.associate { Pair(it, it.shouldBeShown(this, settings)) }
        val showAll = factoriesWithMode.values.any { it == ShowMode.SHOW }
        for ((factory, mode) in factoriesWithMode)
            if (mode == ShowMode.SHOW || (mode == ShowMode.SHOW_NOT_ALONE && showAll))
                addSlide(factory.create())

        setBarColor(resources.getColor(R.color.primaryDarkColor))
        showSkipButton(false)
    }


    override fun onBackPressed() {
        if (pager.isFirstSlide(fragments.size))
            setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        setResult(Activity.RESULT_OK)
        finish()
    }

}