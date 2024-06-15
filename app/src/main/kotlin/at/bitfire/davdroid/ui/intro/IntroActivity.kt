/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.intro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(ExperimentalFoundationApi::class)
class IntroActivity : AppCompatActivity() {

    val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pages = model.pages

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState { pages.size }

                BackHandler {
                    if (pagerState.settledPage == 0) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    } else scope.launch {
                        pagerState.animateScrollToPage(pagerState.settledPage - 1)
                    }
                }

                IntroScreen(
                    pages = pages,
                    pagerState = pagerState,
                    onDonePressed = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }


    /**
     * For launching the [IntroActivity]. Result is `true` when the user cancelled the intro.
     */
    object Contract: ActivityResultContract<Unit?, Boolean>() {
        override fun createIntent(context: Context, input: Unit?): Intent =
            Intent(context, IntroActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_CANCELED
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
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

}