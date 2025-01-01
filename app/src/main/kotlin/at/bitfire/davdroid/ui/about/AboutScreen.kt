package at.bitfire.davdroid.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.ui.composable.PixelBoxes
import at.bitfire.davdroid.ui.composition.LocalNavController
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.launch
import java.text.Collator

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AboutScreen(model: AboutModel = hiltViewModel()) {
    val uriHandler = LocalUriHandler.current
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                },
                title = {
                    Text(stringResource(R.string.navigation_drawer_about))
                },
                actions = {
                    IconButton(onClick = {
                        uriHandler.openUri(
                            Constants.HOMEPAGE_URL
                            .buildUpon()
                            .withStatParams("AboutActivity")
                            .build().toString())
                    }) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = stringResource(R.string.navigation_drawer_website)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            val scope = rememberCoroutineScope()
            val state = rememberPagerState(pageCount = { 3 })

            TabRow(state.currentPage) {
                Tab(state.currentPage == 0, onClick = {
                    scope.launch { state.scrollToPage(0) }
                }) {
                    Text(
                        stringResource(R.string.app_name),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Tab(state.currentPage == 1, onClick = {
                    scope.launch { state.scrollToPage(1) }
                }) {
                    Text(
                        stringResource(R.string.about_translations),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Tab(state.currentPage == 2, onClick = {
                    scope.launch { state.scrollToPage(2) }
                }) {
                    Text(
                        stringResource(R.string.about_libraries),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalPager(
                state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) { index ->
                when (index) {
                    0 -> AboutApp(licenseInfoProvider = model.licenseInfoProvider)
                    1 -> {
                        val translations = model.translations.observeAsState(emptyList())
                        TranslatorsGallery(translations.value)
                    }

                    2 -> LibrariesContainer(
                        Modifier.fillMaxSize(),
                        itemContentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        itemSpacing = 8.dp,
                        librariesBlock = { ctx ->
                            Libs.Builder()
                                .withJson(ctx, R.raw.aboutlibraries)
                                .build()
                        })
                }
            }
        }
    }
}

@Composable
fun AboutApp(licenseInfoProvider: AppLicenseInfoProvider? = null) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())) {
        Image(
            UiUtils.adaptiveIconPainterResource(R.mipmap.ic_launcher),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.CenterHorizontally)
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            stringResource(R.string.about_license_info_no_warranty),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        PixelBoxes(
            arrayOf(Color(0xFFFCF434), Color.White, Color(0xFF9C59D1), Color.Black),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp)
        )

        licenseInfoProvider?.LicenseInfo()
    }
}

@Composable
@Preview
fun AboutApp_Preview() {
    AboutApp(licenseInfoProvider = object : AppLicenseInfoProvider {
        @Composable
        override fun LicenseInfo() {
            Text("Some flavored License Info")
        }
    })
}


@Composable
fun TranslatorsGallery(
    translations: List<AboutModel.Translation>
) {
    val collator = Collator.getInstance()
    LazyColumn(Modifier.padding(8.dp)) {
        items(translations) { translation ->
            Text(
                translation.language,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                translation.translators
                    .sortedWith { a, b -> collator.compare(a, b) }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
@Preview
fun TranslatorsGallery_Sample() {
    TranslatorsGallery(listOf(
        AboutModel.Translation("Some Language", setOf("User 1", "User 2")),
        AboutModel.Translation("Another Language", setOf("User 3", "User 4"))
    ))
}
