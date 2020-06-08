/*
 * Copyright © Ricki Hirner (bitfire web engineering) and other contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.*
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.LibsBuilder
import kotlinx.android.synthetic.main.about.*
import kotlinx.android.synthetic.main.about_languages.*
import kotlinx.android.synthetic.main.about_translation.view.*
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.json.JSONObject
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.*

class AboutActivity: AppCompatActivity() {

    companion object {

        const val pixelsHtml = "<font color=\"#fff433\">■</font>" +
                "<font color=\"#ffffff\">■</font>" +
                "<font color=\"#9b59d0\">■</font>" +
                "<font color=\"#000000\">■</font>"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewpager.adapter = TabsAdapter(supportFragmentManager)
        tabs.setupWithViewPager(viewpager, false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.about_davdroid, menu)
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


    class AppFragment: Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            app_name.setText(R.string.app_name)
            app_version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            build_time.text = getString(R.string.about_build_date, SimpleDateFormat.getDateInstance().format(BuildConfig.buildTime))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                icon.setImageDrawable(resources.getDrawableForDensity(R.mipmap.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH))

            pixels.text = HtmlCompat.fromHtml(pixelsHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            if (true /* open-source version */) {
                warranty.setText(R.string.about_license_info_no_warranty)

                val model = ViewModelProvider(this).get(TextFileModel::class.java)
                model.initialize("gplv3.html", true)
                model.htmlText.observe(viewLifecycleOwner, Observer { spanned ->
                    license_text.text = spanned
                })
            }
        }

    }

    class LanguagesFragment: Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about_languages, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val model = ViewModelProvider(this).get(TextFileModel::class.java)
            model.initialize("translators.json", false)
            model.plainText.observe(viewLifecycleOwner, Observer { json ->
                val jsonTranslations = JSONObject(json)
                translators.adapter = TranslationsAdapter(jsonTranslations)
            })

            translators.layoutManager = LinearLayoutManager(requireActivity())
        }

        class Translation(
                val language: String,
                val translators: Array<String>
        )

        class TranslationsAdapter(
                jsonTranslations: JSONObject
        ): RecyclerView.Adapter<TranslationsAdapter.ViewHolder>() {
            class ViewHolder(val cardView: CardView): RecyclerView.ViewHolder(cardView)

            private val translations = LinkedList<Translation>()

            init {
                for (langCode in jsonTranslations.keys()) {
                    val jsonTranslators = jsonTranslations.getJSONArray(langCode)
                    val translators = Array<String>(jsonTranslators.length()) {
                        idx -> jsonTranslators.getString(idx)
                    }

                    val langTag = langCode.replace('_', '-')
                    val language = Locale.forLanguageTag(langTag).displayName
                    translations += Translation(language, translators)
                }

                // sort translations by localized language name
                val collator = Collator.getInstance()
                translations.sortWith(object: Comparator<Translation> {
                    override fun compare(o1: Translation, o2: Translation) =
                            collator.compare(o1.language, o2.language)
                })
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val tv = LayoutInflater.from(parent.context).inflate(R.layout.about_translation, parent, false) as CardView
                return ViewHolder(tv)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val translation = translations[position]
                holder.cardView.apply {
                    language.text = translation.language
                    val profiles = translation.translators.map { "<a href='https://www.transifex.com/user/profile/$it'>$it</a>" }
                    translators.text = HtmlCompat.fromHtml(
                            context.getString(R.string.about_translations_thanks, profiles.joinToString(", ")),
                            HtmlCompat.FROM_HTML_MODE_COMPACT)
                    translators.movementMethod = LinkMovementMethod.getInstance()
                }
            }

            override fun getItemCount() = translations.size
        }

    }


    class TextFileModel(
            application: Application
    ): AndroidViewModel(application) {

        var initialized = false
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

}
