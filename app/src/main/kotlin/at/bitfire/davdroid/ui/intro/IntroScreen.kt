package at.bitfire.davdroid.ui.intro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.ButtonWithIcon
import at.bitfire.davdroid.ui.composable.PositionIndicator
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun IntroScreen(
    pages: List<IntroPage>,
    pagerState: PagerState = rememberPagerState { pages.size },
    onDonePressed: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pages[it].ComposePage() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            PositionIndicator(
                index = pagerState.currentPage,
                max = pages.size,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 128.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                selectedIndicatorColor = MaterialTheme.colorScheme.onPrimary,
                unselectedIndicatorColor = MaterialTheme.colorScheme.tertiary,
                indicatorSize = 15f
            )

            ButtonWithIcon(
                icon = if (pagerState.currentPage + 1 == pagerState.pageCount) {
                    Icons.Default.Check
                } else {
                    Icons.AutoMirrored.Default.ArrowForward
                },
                contentDescription = stringResource(R.string.intro_next),
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterEnd)
            ) {
                if (pagerState.currentPage + 1 == pagerState.pageCount) {
                    onDonePressed()
                } else scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }
}

@Preview(
    showSystemUi = true
)
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun IntroScreen_Preview() {
    AppTheme {
        IntroScreen(
            listOf(
                object : IntroPage {
                    override fun getShowPolicy(): IntroPage.ShowPolicy =
                        IntroPage.ShowPolicy.SHOW_ALWAYS

                    @Composable
                    override fun ComposePage() {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }
                },
                object : IntroPage {
                    override fun getShowPolicy(): IntroPage.ShowPolicy =
                        IntroPage.ShowPolicy.SHOW_ALWAYS

                    @Composable
                    override fun ComposePage() {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            ),
            onDonePressed = {}
        )
    }
}
