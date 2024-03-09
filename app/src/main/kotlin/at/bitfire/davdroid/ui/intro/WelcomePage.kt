/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.intro

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bitfire.davdroid.R

class WelcomePage: IntroPage {

    override fun getShowPolicy(application: Application) = IntroPage.ShowPolicy.SHOW_ONLY_WITH_OTHERS

    @Composable
    override fun ComposePage() {
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ContentLandscape()
        else
            ContentPortrait()
    }


    @Preview(
        device = "id:3.7in WVGA (Nexus One)",
        showSystemUi = true
    )
    @Composable
    private fun ContentPortrait() {
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
                    .weight(2f)
            )

            Text(
                text = stringResource(R.string.intro_slogan1),
                color = Color.White,
                style = MaterialTheme.typography.subtitle1.copy(fontSize = 34.sp),
                lineHeight = 38.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(R.string.intro_slogan2),
                color = Color.White,
                style = MaterialTheme.typography.h5.copy(fontSize = 48.sp),
                lineHeight = 52.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 32.dp)
                    .padding(
                        bottom = dimensionResource(
                            com.github.appintro.R.dimen.appintro2_bottombar_height
                        )
                    )
            )

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }

    @Preview(
        showSystemUi = true,
        device = "id:medium_tablet"
    )
    @Composable
    private fun ContentLandscape() {
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
                    .weight(1f)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .weight(2f)
            ) {
                Text(
                    text = stringResource(R.string.intro_slogan1),
                    color = Color.White,
                    style = MaterialTheme.typography.subtitle1.copy(fontSize = 34.sp),
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.intro_slogan2),
                    color = Color.White,
                    style = MaterialTheme.typography.h5.copy(fontSize = 48.sp),
                    lineHeight = 52.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )
            }
        }
    }

}