/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import at.bitfire.davdroid.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class WelcomeFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val configuration = LocalConfiguration.current
                when (configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> {
                        ContentLandscape()
                    }
                    else -> {
                        ContentPortrait()
                    }
                }
            }
        }
    }

    @Preview(showSystemUi = true)
    @Composable
    private fun ContentPortrait() {
        val isPreview = LocalInspectionMode.current
        var animate by remember { mutableStateOf(false) }
        val logoAlpha by animateFloatAsState(
            targetValue = if (isPreview) 1f else if (animate) 1f else 0f,
            label = "Animate the alpha of the DAVx5 logo",
            animationSpec = tween(300)
        )
        val offset1 by animateDpAsState(
            targetValue = if (isPreview) 0.dp else if (animate) 0.dp else (-1000).dp,
            label = "Animate the offset of the 'Your data, your choice' text",
            animationSpec = tween(300)
        )
        val offset2 by animateDpAsState(
            targetValue = if (isPreview) 0.dp else if (animate) 0.dp else 1000.dp,
            label = "Animate the offset of the 'Take control of your data' text",
            animationSpec = tween(300)
        )

        LaunchedEffect(Unit) {
            animate = true // Trigger the animation
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(R.color.primaryDarkColor)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp)
                    .weight(2f),
                alpha = logoAlpha
            )

            val textStyleSubtitle1 = MaterialTheme.typography.subtitle1.copy(fontSize = 34.sp)
            var textStyle1 by remember { mutableStateOf(textStyleSubtitle1) }
            var readyToDraw1 by remember { mutableStateOf(false) }
            Text(
                text = stringResource(R.string.intro_slogan1),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                style = textStyle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp)
                    .drawWithContent {
                        if (readyToDraw1) drawContent()
                    }
                    .wrapContentHeight(Alignment.Bottom)
                    .offset(x = offset1),
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.didOverflowWidth) {
                        textStyle1 = textStyle1.copy(fontSize = textStyle1.fontSize * 0.9)
                    } else {
                        readyToDraw1 = true
                    }
                }
            )

            val textStyleH5 = MaterialTheme.typography.h5.copy(fontSize = 48.sp)
            var textStyle2 by remember { mutableStateOf(textStyleSubtitle1) }
            var readyToDraw2 by remember { mutableStateOf(false) }
            Text(
                text = stringResource(R.string.intro_slogan2),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                style = textStyleH5,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp)
                    .padding(
                        bottom = dimensionResource(
                            com.github.appintro.R.dimen.appintro2_bottombar_height
                        )
                    )
                    .drawWithContent {
                        if (readyToDraw2) drawContent()
                    }
                    .offset(x = offset2),
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.didOverflowWidth) {
                        textStyle2 = textStyle2.copy(fontSize = textStyle2.fontSize * 0.9)
                    } else {
                        readyToDraw2 = true
                    }
                }
            )
        }
    }

    @Preview(
        showSystemUi = true,
        device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
    )
    @Composable
    private fun ContentLandscape() {
        val isPreview = LocalInspectionMode.current
        var animate by remember { mutableStateOf(false) }
        val logoAlpha by animateFloatAsState(
            targetValue = if (isPreview) 1f else if (animate) 1f else 0f,
            label = "Animate the alpha of the DAVx5 logo",
            animationSpec = tween(300)
        )
        val offset1 by animateDpAsState(
            targetValue = if (isPreview) 0.dp else if (animate) 0.dp else (-1000).dp,
            label = "Animate the offset of the 'Your data, your choice' text",
            animationSpec = tween(300)
        )
        val offset2 by animateDpAsState(
            targetValue = if (isPreview) 0.dp else if (animate) 0.dp else 1000.dp,
            label = "Animate the offset of the 'Take control of your data' text",
            animationSpec = tween(300)
        )

        LaunchedEffect(Unit) {
            animate = true // Trigger the animation
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(color = colorResource(R.color.primaryDarkColor))
                .padding(
                    bottom = dimensionResource(
                        com.github.appintro.R.dimen.appintro2_bottombar_height
                    )
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 48.dp)
                    .weight(1f),
                alpha = logoAlpha
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp)
            ) {
                val textStyleSubtitle1 = MaterialTheme.typography.subtitle1.copy(fontSize = 34.sp)
                var textStyle1 by remember { mutableStateOf(textStyleSubtitle1) }
                var readyToDraw1 by remember { mutableStateOf(false) }
                Text(
                    text = stringResource(R.string.intro_slogan1),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    style = textStyle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .drawWithContent {
                            if (readyToDraw1) drawContent()
                        }
                        .wrapContentHeight(Alignment.Bottom)
                        .offset(x = offset1),
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.didOverflowWidth) {
                            textStyle1 = textStyle1.copy(fontSize = textStyle1.fontSize * 0.9)
                        } else {
                            readyToDraw1 = true
                        }
                    }
                )

                val textStyleH5 = MaterialTheme.typography.h5.copy(fontSize = 48.sp)
                var textStyle2 by remember { mutableStateOf(textStyleSubtitle1) }
                var readyToDraw2 by remember { mutableStateOf(false) }
                Text(
                    text = stringResource(R.string.intro_slogan2),
                    color = Color.White,
                    softWrap = false,
                    maxLines = 1,
                    style = textStyleH5,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .drawWithContent {
                            if (readyToDraw2) drawContent()
                        }
                        .offset(x = offset2),
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.didOverflowWidth) {
                            textStyle2 = textStyle2.copy(fontSize = textStyle2.fontSize * 0.9)
                        } else {
                            readyToDraw2 = true
                        }
                    }
                )
            }
        }
    }


    @Module
    @InstallIn(ActivityComponent::class)
    abstract class WelcomeFragmentModule {
        @Binds @IntoSet
        abstract fun getFactory(factory: Factory): IntroFragmentFactory
    }

    class Factory @Inject constructor() : IntroFragmentFactory {

        override fun getOrder(context: Context) = -1000

        override fun create() = WelcomeFragment()

    }

}
