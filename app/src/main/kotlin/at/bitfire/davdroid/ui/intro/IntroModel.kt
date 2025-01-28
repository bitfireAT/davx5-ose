/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.logging.Logger
import javax.inject.Inject

@HiltViewModel
class IntroModel @Inject constructor(
    introPageFactory: IntroPageFactory,
    private val logger: Logger
): ViewModel() {

    private val introPages = introPageFactory.introPages

    val pages: List<IntroPage> by lazy {
        calculatePages()
    }


    private fun calculatePages(): List<IntroPage> {
        for (page in introPages)
            logger.fine("Found intro page ${page::class.java} with order ${page.getShowPolicy()}")

        // Calculate which intro pages shall be shown
        val activePages: Map<IntroPage, IntroPage.ShowPolicy> = introPages
            .associateWith { page ->
                page.getShowPolicy().also { policy ->
                    logger.fine("IntroActivity: found intro page ${page::class.java} with $policy")
                }
            }
            .filterValues { it != IntroPage.ShowPolicy.DONT_SHOW }

        // Show intro screen when there's at least one page that shall [always] be shown
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