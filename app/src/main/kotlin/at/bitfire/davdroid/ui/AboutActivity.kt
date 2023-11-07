/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.os.Bundle
import android.view.*
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.widget.PixelBoxes
import com.google.accompanist.themeadapter.material.MdcTheme
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.json.JSONObject
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.logging.Level
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

@AndroidEntryPoint
class AboutActivity: AppCompatActivity() {

    val model by viewModels<Model>()

    @Inject
    lateinit var licenseInfoProvider: Optional<AppLicenseInfoProvider>


    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onNavigateUp() }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = stringResource(R.string.navigate_up)
                                    )
                                }
                            },
                            title = {
                                Text(stringResource(R.string.navigation_drawer_about))
                            },
                            actions = {
                                IconButton(onClick = {
                                    val context = this@AboutActivity
                                    UiUtils.launchUri(context, App.homepageUrl(context))
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

                        HorizontalPager(state, modifier = Modifier.padding(8.dp)) { index ->
                            when (index) {
                                0 -> AboutApp(licenseInfoProvider = licenseInfoProvider.getOrNull())
                                1 -> {
                                    val translations = model.translations.observeAsState(emptyList())
                                    TranslatorsGallery(translations.value)
                                }

                                2 -> LibrariesContainer(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }


    class Model(application: Application) : AndroidViewModel(application) {

        data class Translation(
            val language: String,
            val translators: Set<String>
        )

        val translations = MutableLiveData<List<Translation>>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                loadTranslations()
            }
        }

        private fun loadTranslations() {
            val context = getApplication<Application>()
            try {
                context.resources.assets.open("translators.json").use { stream ->
                    val jsonTranslations = JSONObject(IOUtils.toString(stream, Charsets.UTF_8))
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

                    translations.postValue(result)
                }
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't load translators", e)
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
        Modifier
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
            style = MaterialTheme.typography.h5,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val buildTime = LocalDateTime.ofEpochSecond(BuildConfig.buildTime / 1000, 0, ZoneOffset.UTC)
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        Text(
            stringResource(R.string.about_build_date, dateFormatter.format(buildTime)),
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            stringResource(R.string.about_copyright),
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        PixelBoxes(
            arrayOf(Color(0xFFFCF434), Color.White, Color(0xFF9C59D1), Color.Black),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp)
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
    modifier: Modifier = Modifier
) {
    val collator = Collator.getInstance()
    LazyColumn (modifier) {
        items(translations) { translation ->
            Text(
                translation.language,
                style = MaterialTheme.typography.h6
            )
            Text(
                translation.translators
                    .sortedWith { a, b -> collator.compare(a, b) }
                    .joinToString(" · "),
                style = MaterialTheme.typography.body1
            )
            Divider(Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
@Preview
fun TranslatorsGallery_Sample() {
    TranslatorsGallery(listOf(
        AboutActivity.Model.Translation("Some Language", setOf("User 1", "User 2")),
        AboutActivity.Model.Translation("Another Language", setOf("User 3", "User 4"))
    ))
}