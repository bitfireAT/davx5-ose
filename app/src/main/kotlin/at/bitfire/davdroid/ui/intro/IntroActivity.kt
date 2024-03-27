/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.annotation.WorkerThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.ThemeColors
import com.github.appintro.AppIntro2
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class IntroActivity : AppIntro2() {

    companion object {

        @EntryPoint
        @InstallIn(ActivityComponent::class)
        interface IntroActivityEntryPoint {
            fun introPageFactory(): IntroPageFactory
        }

        @WorkerThread
        fun shouldShowIntroActivity(activity: Activity): Boolean {
            val introPageFactory = EntryPointAccessors.fromActivity(activity, IntroActivityEntryPoint::class.java).introPageFactory()
            return introPageFactory.introPages.any {
                it.getShowPolicy(activity.application) == IntroPage.ShowPolicy.SHOW_ALWAYS
            }
        }

    }

    val model by viewModels<Model>()
    private var currentSlide = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.pages.forEachIndexed { idx, _ ->
            addSlide(PageFragment().apply {
                arguments = Bundle(1).apply {
                    putInt(PageFragment.ARG_PAGE_IDX, idx)
                }
            })
        }

        setBarColor(ThemeColors.primaryDark.toArgb())
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


    @AndroidEntryPoint
    class PageFragment: Fragment() {

        companion object {
            const val ARG_PAGE_IDX = "page"
        }

        val model by activityViewModels<Model>()
        val page by lazy { model.pages[requireArguments().getInt(ARG_PAGE_IDX)] }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
            ComposeView(requireActivity()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AppTheme {
                        Box(Modifier.padding(bottom = dimensionResource(com.github.appintro.R.dimen.appintro2_bottombar_height))) {
                            page.ComposePage()
                        }
                    }
                }
            }

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


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        introPageFactory: IntroPageFactory
    ): AndroidViewModel(application) {

        private val introPages = introPageFactory.introPages

        private var _pages: List<IntroPage>? = null
        val pages: List<IntroPage>
            @Synchronized
            get() {
                _pages?.let { return it }

                val newPages = calculatePages()
                _pages = newPages

                return newPages
            }

        private fun calculatePages(): List<IntroPage> {
            for (page in introPages)
                Logger.log.fine("Found intro page ${page::class.java} with order ${page.getShowPolicy(getApplication())}")

            val activePages: Map<IntroPage, IntroPage.ShowPolicy> = introPages
                .associateWith { it.getShowPolicy(getApplication()) }
                .filterValues { it != IntroPage.ShowPolicy.DONT_SHOW }

            val anyShowAlways = activePages.values.any { it == IntroPage.ShowPolicy.SHOW_ALWAYS }
            return if (anyShowAlways) {
                val pages = mutableListOf<IntroPage>()
                activePages.filterValues { it != IntroPage.ShowPolicy.DONT_SHOW }.forEach { page, _ ->
                    pages += page
                }
                pages
            } else
                emptyList()
        }

    }

}