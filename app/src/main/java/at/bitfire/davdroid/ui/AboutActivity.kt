/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.*
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.AboutBinding
import at.bitfire.davdroid.databinding.AboutLanguagesBinding
import at.bitfire.davdroid.databinding.AboutTranslationBinding
import at.bitfire.davdroid.databinding.ActivityAboutBinding
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Qualifier

@AndroidEntryPoint
class AboutActivity: AppCompatActivity() {

    companion object {

        const val pixelsHtml = "<font color=\"#fff433\">■</font>" +
                "<font color=\"#ffffff\">■</font>" +
                "<font color=\"#9b59d0\">■</font>" +
                "<font color=\"#000000\">■</font>"

    }

    private lateinit var binding: ActivityAboutBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.viewpager.adapter = TabsAdapter(supportFragmentManager)
        binding.tabs.setupWithViewPager(binding.viewpager, false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_about, menu)
        return true
    }

    fun showWebsite(item: MenuItem) {
        UiUtils.launchUri(this, App.homepageUrl(this))
    }


    private inner class TabsAdapter(
            fm: FragmentManager
    ): FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getCount() = 3

        override fun getPageTitle(position: Int): String =
                when (position) {
                    0 -> getString(R.string.app_name)
                    1 -> getString(R.string.about_translations)
                    else -> getString(R.string.about_libraries)
                }

        override fun getItem(position: Int) =
                when (position) {
                    0 -> AppFragment()
                    1 -> LanguagesFragment()
                    else -> {
                        LibsBuilder()
                                .withFields(R.string::class.java.fields)        // mandatory for non-standard build flavors
                                .withLicenseShown(true)
                                .withAboutIconShown(false)

                                // https://github.com/mikepenz/AboutLibraries/issues/490
                                .withLibraryModification("org_brotli__dec", Libs.LibraryFields.LIBRARY_NAME, "Brotli")
                                .withLibraryModification("org_brotli__dec", Libs.LibraryFields.AUTHOR_NAME, "Google")

                                .supportFragment()
                    }
                }
    }


    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class LicenseFragment

    @Module
    @InstallIn(ActivityComponent::class)
    abstract class LicenseFragmentModule {
        @BindsOptionalOf
        @LicenseFragment
        abstract fun licenseFragment(): Fragment
    }

    @AndroidEntryPoint
    class AppFragment: Fragment() {

        private var _binding: AboutBinding? = null
        private val binding get() = _binding!!
        val model by viewModels<TextFileModel>()

        @Inject
        @LicenseFragment
        lateinit var licenseFragment: Optional<Fragment>

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = AboutBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            binding.appName.setText(R.string.app_name)
            binding.appVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            binding.buildTime.text = getString(R.string.about_build_date, SimpleDateFormat.getDateInstance().format(BuildConfig.buildTime))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                binding.icon.setImageDrawable(resources.getDrawableForDensity(R.mipmap.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH, null))

            binding.pixels.text = HtmlCompat.fromHtml(pixelsHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            if (true /* open-source version */) {
                binding.warranty.setText(R.string.about_license_info_no_warranty)

                model.initialize("gplv3.html", true)
                model.htmlText.observe(viewLifecycleOwner, { spanned ->
                    binding.licenseText.text = spanned
                })
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

    }

    class LanguagesFragment: Fragment() {

        private var _binding: AboutLanguagesBinding? = null
        private val binding get() = _binding!!
        val model by viewModels<TranslationsModel>()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = AboutLanguagesBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            model.initialize("translators.json", false)
            model.translations.observe(viewLifecycleOwner, { translations ->
                binding.translators.adapter = TranslationsAdapter(translations)
            })

            binding.translators.layoutManager = LinearLayoutManager(requireActivity())
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }


        class TranslationsAdapter(
                val translations: List<TranslationsModel.Translation>
        ): RecyclerView.Adapter<TranslationsAdapter.ViewHolder>() {

            private lateinit var binding: AboutTranslationBinding

            class ViewHolder(
                val context: Context, val binding: AboutTranslationBinding
                ): RecyclerView.ViewHolder(binding.root)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                binding = AboutTranslationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ViewHolder(parent.context, binding)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val translation = translations[position]
                holder.binding.apply {
                    language.text = translation.language
                    val profiles = translation.translators.map { "<a href='https://www.transifex.com/user/profile/$it'>$it</a>" }
                    translators.text = HtmlCompat.fromHtml(
                            holder.context.getString(R.string.about_translations_thanks, profiles.joinToString(", ")),
                            HtmlCompat.FROM_HTML_MODE_COMPACT)
                    translators.movementMethod = LinkMovementMethod.getInstance()
                }
            }

            override fun getItemCount() = translations.size
        }

    }


    open class TextFileModel(
            application: Application
    ): AndroidViewModel(application) {

        private var initialized = false
        val htmlText = MutableLiveData<Spanned>()
        val plainText = MutableLiveData<String>()

        @UiThread
        fun initialize(assetName: String, html: Boolean) {
            if (initialized) return
            initialized = true

            viewModelScope.launch(Dispatchers.IO) {
                getApplication<Application>().resources.assets.open(assetName).use {
                    val raw = IOUtils.toString(it, Charsets.UTF_8)
                    if (html) {
                        val spanned = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        htmlText.postValue(spanned)
                    } else
                        plainText.postValue(raw)
                }
            }
        }

    }

    class TranslationsModel(
            application: Application
    ): TextFileModel(application) {

        class Translation(
                val language: String,
                val translators: Array<String>
        )

        val translations = object: MediatorLiveData<List<Translation>>() {
            init {
                addSource(plainText) { rawJson ->
                    // parse JSON
                    val jsonTranslations = JSONObject(rawJson)
                    val result = LinkedList<Translation>()
                    for (langCode in jsonTranslations.keys()) {
                        val jsonTranslators = jsonTranslations.getJSONArray(langCode)
                        val translators = Array<String>(jsonTranslators.length()) {
                            idx -> jsonTranslators.getString(idx)
                        }

                        val langTag = langCode.replace('_', '-')
                        val language = Locale.forLanguageTag(langTag).displayName
                        result += Translation(language, translators)
                    }

                    // sort translations by localized language name
                    val collator = Collator.getInstance()
                    result.sortWith { o1, o2 ->
                        collator.compare(o1.language, o2.language)
                    }

                    postValue(result)
                }
            }
        }

    }

}