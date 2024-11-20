package at.bitfire.davdroid.ui.intro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.M3ColorScheme
import kotlinx.coroutines.launch

@Composable
fun IntroScreen(
    pages: List<IntroPage>,
    pagerState: PagerState = rememberPagerState { pages.size },
    onDonePressed: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(M3ColorScheme.primaryLight)
                    // consume bottom and side insets of safe drawing area, like BottomAppBar
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .height(90.dp)
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
                        .align(Alignment.CenterEnd),
                    color = M3ColorScheme.tertiaryLight
                ) {
                    if (pagerState.currentPage + 1 == pagerState.pageCount) {
                        onDonePressed()
                    } else scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            HorizontalPager(state = pagerState) { idxPage ->
                val page = pages[idxPage]
                Box(
                    modifier = if (page.customTopInsets)
                        Modifier    // ComposePage() handles insets itself
                    else
                        // consume top and horizontal sides of safe drawing padding (like TopAppBar)
                        // bottom is handled by the bottom bar
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                ) {
                    page.ComposePage()
                }
            }
        }
    }
}

@Preview(
    showSystemUi = true,
    showBackground = true
)
@Composable
fun IntroScreen_Preview() {
    AppTheme {
        IntroScreen(
            listOf(
                object : IntroPage() {
                    override fun getShowPolicy(): ShowPolicy = ShowPolicy.SHOW_ALWAYS

                    @Composable
                    override fun ComposePage() {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Text("Some Text")
                        }
                    }
                },
                object : IntroPage() {
                    override fun getShowPolicy(): ShowPolicy = ShowPolicy.SHOW_ALWAYS

                    @Composable
                    override fun ComposePage() {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Some Text")
                        }
                    }
                }
            ),
            onDonePressed = {}
        )
    }
}


@Composable
fun PositionIndicator(
    index: Int,
    max: Int,
    modifier: Modifier = Modifier,
    selectedIndicatorColor: Color = MaterialTheme.colorScheme.tertiary,
    unselectedIndicatorColor: Color = contentColorFor(selectedIndicatorColor),
    indicatorSize: Float = 20f,
    indicatorPadding: Float = 20f
) {
    val selectedPosition by animateFloatAsState(
        targetValue = index.toFloat(),
        label = "position"
    )

    Canvas(modifier = modifier) {
        // idx * indicatorSize * 2 + idx * indicatorPadding + indicatorSize
        // idx * (indicatorSize * 2 + indicatorPadding) + indicatorSize
        val padding = indicatorSize * 2 + indicatorPadding

        val totalWidth = indicatorSize * 2 * max + indicatorPadding * (max - 1)
        translate(
            left = size.width / 2 - totalWidth / 2
        ) {
            for (idx in 0 until max) {
                drawCircle(
                    color = unselectedIndicatorColor,
                    radius = indicatorSize,
                    center = Offset(
                        x = idx * padding + indicatorSize,
                        y = size.height / 2
                    )
                )
            }

            drawCircle(
                color = selectedIndicatorColor,
                radius = indicatorSize,
                center = Offset(
                    x = selectedPosition * padding + indicatorSize,
                    y = size.height / 2
                )
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xff000000
)
@Composable
fun PositionIndicator_Preview() {
    var index by remember { mutableIntStateOf(0) }

    PositionIndicator(
        index = index,
        max = 5,
        modifier = Modifier
            .width(200.dp)
            .height(50.dp)
            .clickable { if (index == 4) index = 0 else index++ }
    )
}


@Composable
fun ButtonWithIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.tertiary,
    contentColor: Color = contentColorFor(backgroundColor = color),
    onClick: () -> Unit
) {
    Surface(
        color = color,
        contentColor = contentColor,
        modifier = modifier
            .size(size)
            .aspectRatio(1f),
        onClick = onClick,
        shape = CircleShape
    ) {
        AnimatedContent(
            targetState = icon,
            label = "Button Icon"
        ) {
            Icon(
                imageVector = it,
                contentDescription = contentDescription,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview
@Composable
fun ButtonWithIcon_Preview() {
    AppTheme {
        ButtonWithIcon(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null
        ) { }
    }
}
