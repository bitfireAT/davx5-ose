package at.bitfire.davdroid.ui.intro

import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.log.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class IntroModel @Inject constructor(
    introPageFactory: IntroPageFactory
): ViewModel() {

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
            Logger.log.fine("Found intro page ${page::class.java} with order ${page.getShowPolicy()}")

        val activePages: Map<IntroPage, IntroPage.ShowPolicy> = introPages
            .associateWith { page ->
                page.getShowPolicy().also { policy ->
                    Logger.log.fine("IntroActivity: found intro page ${page::class.java} with $policy")
                }
            }
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
