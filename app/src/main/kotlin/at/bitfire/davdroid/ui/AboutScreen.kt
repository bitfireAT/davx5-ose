package at.bitfire.davdroid.ui

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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AboutActivity.AppLicenseInfoProvider
import at.bitfire.davdroid.ui.composable.PixelBoxes
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

@Composable
fun AboutScreen(
    viewModel: AboutModel,
    licenseInfoProvider: AppLicenseInfoProvider?,
    onBackRequested: () -> Unit
) {
    val translations by viewModel.translations.observeAsState(emptyList())

    AboutScreen(translations, licenseInfoProvider, onBackRequested)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    translations: List<AboutModel.Translation>,
    licenseInfoProvider: AppLicenseInfoProvider?,
    onBackRequested: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val scope = rememberCoroutineScope()
    val state = rememberPagerState(pageCount = { 3 })

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackRequested) {
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
                        uriHandler.openUri(Constants.HOMEPAGE_URL
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
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = state.currentPage == 0,
                    icon = {
                        Icon(
                            Icons.Default.Info,
                            stringResource(R.string.app_name)
                        )
                    },
                    label = { Text(stringResource(R.string.app_name)) },
                    onClick = { scope.launch { state.animateScrollToPage(0) } }
                )
                NavigationBarItem(
                    selected = state.currentPage == 1,
                    icon = {
                        Icon(
                            Icons.Default.Language,
                            stringResource(R.string.about_translations)
                        )
                    },
                    label = { Text(stringResource(R.string.about_translations)) },
                    onClick = { scope.launch { state.animateScrollToPage(1) } }
                )
                NavigationBarItem(
                    selected = state.currentPage == 2,
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Default.LibraryBooks,
                            stringResource(R.string.about_libraries)
                        )
                    },
                    label = { Text(stringResource(R.string.about_libraries)) },
                    onClick = { scope.launch { state.animateScrollToPage(2) } }
                )
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            HorizontalPager(
                state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) { index ->
                when (index) {
                    0 -> AboutApp(licenseInfoProvider = licenseInfoProvider)
                    1 -> TranslatorsGallery(translations)

                    2 -> LibrariesContainer(
                        modifier = Modifier.fillMaxSize(),
                        itemContentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        itemSpacing = 8.dp,
                        librariesBlock = { ctx ->
                            Libs.Builder()
                                .withJson(ctx, R.raw.aboutlibraries)
                                .build()
                        }
                    )
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun AboutScreen_Preview() {
    AboutScreen(
        translations = listOf(
            AboutModel.Translation(Locale.ENGLISH.displayName, setOf("Translator 1", "Translator 2")),
            AboutModel.Translation(Locale.GERMAN.displayName, setOf("Translator 3"))
        ),
        licenseInfoProvider = object : AppLicenseInfoProvider {
            @Composable
            override fun LicenseInfo() {
                Text("Some flavored License Info")
            }
        },
        onBackRequested = {}
    )
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
