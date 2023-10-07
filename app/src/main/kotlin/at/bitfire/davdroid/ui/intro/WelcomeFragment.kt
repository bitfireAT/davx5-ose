/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.databinding.IntroWelcomeBinding
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class WelcomeFragment: Fragment() {

    private var _binding: IntroWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = IntroWelcomeBinding.inflate(inflater, container, false)

        if (true /* ose build */) {
            binding.logo.apply {
                alpha = 0f
                animate()
                    .alpha(1f)
                    .setDuration(300)
            }
            binding.yourDataYourChoice.apply {
                translationX = -1000f
                animate()
                    .translationX(0f)
                    .setDuration(300)
            }
            binding.takeControl.apply {
                translationX = 1000f
                animate()
                    .translationX(0f)
                    .setDuration(300)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    @Module
    @InstallIn(ActivityComponent::class)
    abstract class WelcomeFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: WelcomeFragment.Factory): IntroFragmentFactory
    }

    class Factory @Inject constructor() : IntroFragmentFactory {

        override fun getOrder(context: Context) = -1000

        override fun create() = WelcomeFragment()

    }

}
