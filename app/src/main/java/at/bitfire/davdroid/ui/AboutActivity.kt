/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.app.Application
import android.os.Bundle
import android.text.Spanned
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import com.mikepenz.aboutlibraries.LibsBuilder
import kotlinx.android.synthetic.main.about.*
import kotlinx.android.synthetic.main.activity_about.*
import org.apache.commons.io.IOUtils
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
    ): FragmentPagerAdapter(fm) {

        override fun getCount() = 2

        override fun getPageTitle(position: Int): String =
                when (position) {
                    1 -> getString(R.string.about_libraries)
                    else -> getString(R.string.app_name)
                }

        override fun getItem(position: Int) =
                when (position) {
                    1 -> LibsBuilder()
                            .withAutoDetect(false)
                            .withFields(R.string::class.java.fields)
                            .withLicenseShown(true)
                            .supportFragment()
                    else -> AppFragment()
                }!!
    }


    class AppFragment: Fragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            app_name.text = getString(R.string.app_name)
            app_version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            build_time.text = getString(R.string.about_build_date, SimpleDateFormat.getDateInstance().format(BuildConfig.buildTime))

            pixels.text = HtmlCompat.fromHtml(pixelsHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

            if (true /* open-source version */) {
                warranty.setText(R.string.about_license_info_no_warranty)

                val model = ViewModelProviders.of(this).get(LicenseModel::class.java)
                model.htmlText.observe(this, Observer { spanned ->
                    license_text.text = spanned
                })
            }
        }

    }

    class LicenseModel(
            application: Application
    ): AndroidViewModel(application) {

        val htmlText = MutableLiveData<Spanned>()

        init {
            thread {
                getApplication<Application>().resources.assets.open("gplv3.html").use {
                    val spanned = HtmlCompat.fromHtml(IOUtils.toString(it, Charsets.UTF_8), HtmlCompat.FROM_HTML_MODE_LEGACY)
                    htmlText.postValue(spanned)
                }
            }
        }

    }

}
