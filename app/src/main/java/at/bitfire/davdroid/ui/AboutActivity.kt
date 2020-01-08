/*
 * Copyright © Ricki Hirner (bitfire web engineering).
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import com.mikepenz.aboutlibraries.LibsBuilder
import kotlinx.android.synthetic.main.about.*
import kotlinx.android.synthetic.main.about_languages.*
import kotlinx.android.synthetic.main.about_translation.view.*
import kotlinx.android.synthetic.main.activity_about.*
import org.apache.commons.io.IOUtils
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

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
                    1 -> getString(R.string.about_languages)
                    else -> getString(R.string.about_libraries)
                }

        override fun getItem(position: Int) =
                when (position) {
                    0 -> AppFragment()
                    1 -> LanguagesFragment()
                    else -> LibsBuilder()
                            .withAutoDetect(false)
                            .withFields(R.string::class.java.fields)
                            .withLicenseShown(true)
                            .supportFragment()
                }
    }


    class AppFragment: Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            app_name.text = getString(R.string.app_name)
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

                /*for (locale in Locale.getAvailableLocales()) {
                    text.append(locale.toLanguageTag()).append("<br/>")
                }
                for (langCode in languages.keys()) {
                    text.append(langCode).append("<br/>")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        text.append(langCode).append("<br/>")
                        val langTag = langCode.replace('_', '-')

                        text.append(Locale.forLanguageTag(langTag).displayName).append("<br/>")
                    }

                    val translators = langCode.getJSONArray("translators")
                    for (j in 0 until translators.length()) {
                        val translator = translators.getString(j)
                        text.append("@").append(translator).append(" (Transifex)<br/>")
                    }
                    text.append("<br/>")
                }
                translators.setText(HtmlCompat.fromHtml(text.toString(), HtmlCompat.FROM_HTML_MODE_COMPACT))*/
            })

            translators.layoutManager = LinearLayoutManager(requireActivity())
        }

        class Translation(
                val langCode: String,
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
                    translations += Translation(langCode, translators)
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val tv = LayoutInflater.from(parent.context).inflate(R.layout.about_translation, parent, false) as CardView
                return ViewHolder(tv)
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val translation = translations[position]
                holder.cardView.apply {
                    languageCode.text = translation.langCode

                    val langTag = translation.langCode.replace('_', '-')
                    language.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                         Locale.forLanguageTag(langTag).displayName
                    else
                        langTag

                    translators.text = translation.translators.joinToString(" · ")
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

            thread {
                getApplication<Application>().resources.assets.open(assetName).use {
                    val raw = IOUtils.toString(it, Charsets.UTF_8)
                    if (html) {
                        val spanned = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
                        htmlText.postValue(spanned)
                    } else
                        plainText.postValue(raw)
                }
            }

            initialized = true
        }

    }

}
