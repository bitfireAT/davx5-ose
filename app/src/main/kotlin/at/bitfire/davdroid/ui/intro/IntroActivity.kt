/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.addCallback
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import com.github.appintro.AppIntro2
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import javax.inject.Inject

@AndroidEntryPoint
class IntroActivity: AppIntro2() {

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface IntroActivityEntryPoint {
        fun introFragmentFactories(): Set<@JvmSuppressWildcards IntroFragmentFactory>
    }

    companion object {

        fun shouldShowIntroActivity(activity: Activity): Boolean {
            val factories = EntryPointAccessors.fromActivity(activity, IntroActivityEntryPoint::class.java).introFragmentFactories()
            return factories.any {
                val order = it.getOrder(activity)
                Logger.log.fine("Found intro fragment factory ${it::class.java} with order $order")
                order > 0
            }
        }

    }

    private var currentSlide = 0

    @Inject lateinit var introFragmentFactories: Set<@JvmSuppressWildcards IntroFragmentFactory>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factoriesWithOrder = introFragmentFactories
            .associateBy { it.getOrder(this) }
            .filterKeys { it != IntroFragmentFactory.DONT_SHOW }

        val anyPositiveOrder = factoriesWithOrder.keys.any { it > 0 }
        if (anyPositiveOrder) {
            for ((_, factory) in factoriesWithOrder.toSortedMap())
                addSlide(factory.create())
        }

        setBarColor(ResourcesCompat.getColor(resources, R.color.primaryDarkColor, null))
        isSkipButtonEnabled = false

        onBackPressedDispatcher.addCallback(this) {
            if (currentSlide == 0) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                goToPreviousSlide()
            }
        }
    }

    override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        currentSlide = position
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        setResult(Activity.RESULT_OK)
        finish()
    }


    /**
     * For launching the [IntroActivity]. Result is `true` when the user cancelled the intro.
     */
    object Contract: ActivityResultContract<Unit?, Boolean>() {
        override fun createIntent(context: Context, input: Unit?): Intent =
            Intent(context, IntroActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_CANCELED
        }
    }

}