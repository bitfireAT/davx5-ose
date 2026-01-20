/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.composable.PixelBoxes
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.LinkedList
import java.util.Locale
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@AndroidEntryPoint
class AboutActivity: AppCompatActivity() {

    val model by viewModels<Model>()

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
                                    uriHandler.openUri(ExternalUris.Homepage.baseUrl
                                        .buildUpon()
                                        .withStatParams(javaClass.simpleName)
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
                                0 -> AboutApp(licenseInfoProvider = licenseInfoProvider.getOrNull())
                                1 -> {
                                    val weblateTranslators = model.weblateTranslators.collectAsStateWithLifecycle(emptyList())
                                    val transifexTranslators = model.transifexTranslators.collectAsStateWithLifecycle(emptyList())
                                    TranslatorsGallery(weblateTranslators.value, transifexTranslators.value)
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


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger
    ): ViewModel() {

        data class Translation(
            val language: String,
            val translators: Set<String>
        )

        val transifexTranslators: Flow<List<Translation>> = flow {
            val translators = loadTransifexTranslators()
            emit(translators)
        }

        val weblateTranslators: Flow<List<Translation>> = flow {
            val translators = loadWeblateTranslators()
            emit(translators)
        }

        @VisibleForTesting
        suspend fun loadWeblateTranslators(): List<Translation> = withContext(ioDispatcher) {
            try {
                context.resources.assets.open("credits.json").use { stream ->
                    val jsonTranslations = JSONArray(stream.readBytes().decodeToString())
                    val result = LinkedList<Translation>()
                    for (i in 0 until jsonTranslations.length()) {
                        val jsonObject = jsonTranslations.getJSONObject(i)
                        val language = jsonObject.keys().takeIf { it.hasNext() }?.next() ?: continue
                        val jsonTranslators = jsonObject.getJSONArray(language)
                        val translators = (0 until jsonTranslators.length()).mapNotNull { idx ->
                            val obj = jsonTranslators.getJSONObject(idx)
                            val fullName = obj.getString("full_name")
                            val username = obj.getString("username")

                            // Ricki did the migration from Weblate to Transifex, so the user is shown as contributor for the language. Filter it out
                            if (username == "rfc2822") return@mapNotNull null

                            "$fullName (@$username)"
                        }.toSet()

                        result += Translation(language, translators)
                    }

                    // sort translations by localized language name
                    val collator = Collator.getInstance()
                    result.sortWith { o1, o2 ->
                        collator.compare(o1.language, o2.language)
                    }

                    result
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't load translators", e)
                emptyList()
            }
        }

        private suspend fun loadTransifexTranslators(): List<Translation> = withContext(ioDispatcher) {
            try {
                context.resources.assets.open("translators.json").use { stream ->
                    val jsonTranslations = JSONObject(stream.readBytes().decodeToString())
                    val result = LinkedList<Translation>()
                    for (langCode in jsonTranslations.keys()) {
                        val jsonTranslators = jsonTranslations.getJSONArray(langCode)
                        val translators = Array<String>(jsonTranslators.length()) { idx ->
                            jsonTranslators.getString(idx)
                        }

                        val langTag = langCode.replace('_', '-')
                        val language = Locale.forLanguageTag(langTag).displayName
                        result += Translation(language, translators.toSet())
                    }

                    // sort translations by localized language name
                    val collator = Collator.getInstance()
                    result.sortWith { o1, o2 ->
                        collator.compare(o1.language, o2.language)
                    }
                    result
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't load translators", e)
                emptyList()
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


@Composable
fun AboutApp(licenseInfoProvider: AboutActivity.AppLicenseInfoProvider? = null) {
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
    AboutApp(licenseInfoProvider = object : AboutActivity.AppLicenseInfoProvider {
        @Composable
        override fun LicenseInfo() {
            Text("Some flavored License Info")
        }
    })
}


@Composable
fun TranslatorsGallery(
    translations: List<AboutActivity.Model.Translation>,
    transifexTranslations: List<AboutActivity.Model.Translation>
) {
    val collator = Collator.getInstance()
    LazyColumn(Modifier.padding(8.dp)) {
        items(translations) { translation ->
            val transifexTranslation = transifexTranslations.find { it.language == translation.language }

            if (translation.translators.isEmpty() && (transifexTranslation == null || transifexTranslation.translators.isEmpty())) {
                // skip empty entries
                return@items
            }

            Text(
                text = translation.language,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (translation.translators.isNotEmpty()) {
                Text(
                    translation.translators
                        .sortedWith { a, b -> collator.compare(a, b) }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge
                )

                // Since it's possible that we have translators from Transifex but not from the old credits.json,
                // only add spacing if we also have old translators, which would be the same as adding top padding
                // to the Transifex section.
                if (transifexTranslation != null) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (transifexTranslation != null) {
                Text(
                    "Transifex:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    transifexTranslation.translators
                        .sortedWith { a, b -> collator.compare(a, b) }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
@Preview
fun TranslatorsGallery_Sample() {
    TranslatorsGallery(listOf(
        AboutActivity.Model.Translation("Some Language", setOf("User 1", "User 2")),
        AboutActivity.Model.Translation("Another Language", setOf("User 3", "User 4"))
    ), listOf(
        AboutActivity.Model.Translation("Some Language", setOf("User 5", "User 6")),
    ))
}
