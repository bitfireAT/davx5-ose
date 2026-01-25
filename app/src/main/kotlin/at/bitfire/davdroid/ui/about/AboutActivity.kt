/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.launch
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@AndroidEntryPoint
class AboutActivity: AppCompatActivity() {

    val model by viewModels<AboutModel>()

    @Inject
    lateinit var licenseInfoProvider: Optional<AppLicenseInfoProvider>


    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val uriHandler = LocalUriHandler.current

                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
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
                                        at.bitfire.davdroid.ui.ExternalUris.Homepage.baseUrl
                                            .buildUpon()
                                            .withStatParams(javaClass.simpleName)
                                            .build().toString()
                                    )
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
                                0 -> AboutApp(licenseInfoProvider = licenseInfoProvider.getOrNull())
                                1 -> {
                                    val weblateTranslators by model.weblateTranslators.collectAsStateWithLifecycle("")
                                    val transifexTranslators by model.transifexTranslators.collectAsStateWithLifecycle("")
                                    TranslationsTab(
                                        weblateTranslators = weblateTranslators,
                                        transifexTranslators = transifexTranslators
                                    )
                                }

                                2 -> LibrariesContainer(
                                    modifier = Modifier.fillMaxSize(),
                                    padding = LibraryDefaults.libraryPadding(
                                        contentPadding = PaddingValues(8.dp)
                                    ),
                                    dimensions = LibraryDefaults.libraryDimensions(
                                        itemSpacing = 8.dp
                                    ),
                                    libraries = Libs.Builder()
                                        .withContext(LocalContext.current)
                                        .build()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    interface AppLicenseInfoProvider {
        @Composable
        fun LicenseInfo()
    }

    @Module
    @InstallIn(ActivityComponent::class)
    interface AppLicenseInfoProviderModule {
        @BindsOptionalOf
        fun appLicenseInfoProvider(): AppLicenseInfoProvider
    }

}