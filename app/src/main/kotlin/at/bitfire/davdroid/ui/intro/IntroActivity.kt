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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.M3ColorScheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {

    val model by viewModels<IntroModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pages = model.pages

        setContent {
            AppTheme(
                statusBarColorProvider = { M3ColorScheme.primaryLight },
                statusBarDarkColorProvider = { M3ColorScheme.onPrimaryDark },
                navigationBarColorProvider = { M3ColorScheme.primaryLight },
                navigationBarDarkColorProvider = { M3ColorScheme.onPrimaryDark }
            ) {
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
            Intent(context, IntroActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == Activity.RESULT_CANCELED
        }
    }

}