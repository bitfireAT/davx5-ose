/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.Spanned
import android.view.*
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import com.mikepenz.aboutlibraries.LibsBuilder
import kotlinx.android.synthetic.main.about_davdroid.*
import kotlinx.android.synthetic.main.activity_about.*
import org.apache.commons.io.IOUtils
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
        val intent = Intent(Intent.ACTION_VIEW, App.homepageUrl(this))
        if (intent.resolveActivity(packageManager) != null)
            startActivity(intent)
    }


    private inner class TabsAdapter(
            fm: FragmentManager
    ): FragmentPagerAdapter(fm) {

        override fun getCount() = 2

        override fun getPageTitle(position: Int) =
                when (position) {
                    1 -> getString(R.string.about_libraries)
                    else -> getString(R.string.app_name)
                }!!

        override fun getItem(position: Int) =
                when (position) {
                    1 -> LibsBuilder()
                            .withAutoDetect(false)
                            .withFields(R.string::class.java.fields)
                            .withLicenseShown(true)
                            .supportFragment()
                    else -> DavdroidFragment()
                }!!
    }


    class DavdroidFragment: Fragment(), LoaderManager.LoaderCallbacks<Spanned> {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about_davdroid, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            app_name.text = getString(R.string.app_name)
            app_version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            build_time.text = getString(R.string.about_build_date, SimpleDateFormat.getDateInstance().format(BuildConfig.buildTime))

            pixels.text = Html.fromHtml(pixelsHtml)

            if (true /* open-source version */) {
                warranty.text = Html.fromHtml(getString(R.string.about_license_info_no_warranty))
                loaderManager.initLoader(0, null, this)
            }
        }

        override fun onCreateLoader(id: Int, args: Bundle?) =
                HtmlAssetLoader(requireActivity(), "gplv3.html")

        override fun onLoadFinished(loader: Loader<Spanned>, license: Spanned?) {
            Logger.log.info("LOAD FINISHED")
            license_text.text = license
        }

        override fun onLoaderReset(loader: Loader<Spanned>) {
        }

    }

    class HtmlAssetLoader(
            context: Context,
            val fileName: String
    ): AsyncTaskLoader<Spanned>(context) {

        override fun onStartLoading() {
            forceLoad()
        }

        override fun loadInBackground(): Spanned =
                context.resources.assets.open(fileName).use {
                    Html.fromHtml(IOUtils.toString(it, Charsets.UTF_8))
                }

    }

}
