/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.annotation.SuppressLint
import android.content.Context
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
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import ezvcard.Ezvcard
import kotlinx.android.synthetic.main.about_component.view.*
import kotlinx.android.synthetic.main.activity_about.*
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.util.*
import java.util.logging.Level

class AboutActivity: AppCompatActivity() {

    private class ComponentInfo(
            val title: String?,
            val version: String?,
            val website: String,
            val copyright: String,
            val licenseInfo: Int?,
            val licenseTextFile: String?
    )

    private lateinit var components: Array<ComponentInfo>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        components = arrayOf(
            ComponentInfo(
                    null, BuildConfig.VERSION_NAME, getString(R.string.homepage_url),
                    "Ricki Hirner, Bernhard Stockmann (bitfire web engineering)",
                    null, null
            ), ComponentInfo(
                    "AmbilWarna", null, "https://github.com/yukuku/ambilwarna",
                    "Yuku", R.string.about_license_info_no_warranty, "apache2.html"
            ), ComponentInfo(
                    "Apache Commons", null, "http://commons.apache.org/",
                    "Apache Software Foundation", R.string.about_license_info_no_warranty, "apache2.html"
            ), ComponentInfo(
                    "dnsjava", null, "http://dnsjava.org/",
                    "Brian Wellington", R.string.about_license_info_no_warranty, "bsd.html"
            ), ComponentInfo(
                    "ez-vcard", Ezvcard.VERSION, "https://github.com/mangstadt/ez-vcard",
                    "Michael Angstadt", R.string.about_license_info_no_warranty, "bsd.html"
            ), ComponentInfo(
                    "ical4j", "2.x", "https://ical4j.github.io/",
                    "Ben Fortuna", R.string.about_license_info_no_warranty, "bsd-3clause.html"
            ), ComponentInfo(
                    "OkHttp", null, "https://square.github.io/okhttp/",
                    "Square, Inc.", R.string.about_license_info_no_warranty, "apache2.html"
            ), ComponentInfo(
                    "Project Lombok", null, "https://projectlombok.org/",
                    "The Project Lombok Authors", R.string.about_license_info_no_warranty, "mit.html"
            )
        )

        setContentView(R.layout.activity_about)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewpager.adapter = TabsAdapter(supportFragmentManager)
        tabs.setupWithViewPager(viewpager)
    }


    private inner class TabsAdapter(
            fm: FragmentManager
    ): FragmentPagerAdapter(fm) {

        override fun getCount() = components.size

        override fun getPageTitle(position: Int) =
                components[position].title ?: getString(R.string.app_name)!!

        override fun getItem(position: Int) = ComponentFragment.instantiate(position)
    }


    class ComponentFragment: Fragment(), LoaderManager.LoaderCallbacks<Spanned> {

        companion object {
            val KEY_POSITION = "position"
            val KEY_FILE_NAME = "fileName"

            fun instantiate(position: Int): ComponentFragment {
                val frag = ComponentFragment()
                val args = Bundle(1)
                args.putInt(KEY_POSITION, position)
                frag.arguments = args
                return frag
            }
        }

        private val licenseFragmentLoader = ServiceLoader.load(ILicenseFragment::class.java)!!.firstOrNull()


        @SuppressLint("SetTextI18n")
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val info = (activity as AboutActivity).components[arguments!!.getInt(KEY_POSITION)]

            val v = inflater.inflate(R.layout.about_component, container, false)

            var title = info.title ?: getString(R.string.app_name)
            info.version?.let { title += " ${info.version}" }
            v.title.text = title

            v.website.autoLinkMask = Linkify.WEB_URLS
            v.website.text = info.website

            v.copyright.text = "© ${info.copyright}"

            if (info.licenseInfo == null && info.licenseTextFile == null) {
                // No license text, so this must be the app's tab. Show the license fragment here, if available.
                licenseFragmentLoader?.let { factory ->
                    fragmentManager!!.beginTransaction()
                            .add(R.id.license_fragment, factory.getFragment())
                            .commit()
                }
                v.license_terms.visibility = View.GONE

            } else {
                // show license info
                if (info.licenseInfo == null)
                    v.license_info.visibility = View.GONE
                else
                    v.license_info.setText(info.licenseInfo)

                // load and format license text
                if (info.licenseTextFile == null) {
                    v.license_header.visibility = View.GONE
                    v.license_text.visibility = View.GONE
                } else {
                    val args = Bundle(1)
                    args.putString(KEY_FILE_NAME, info.licenseTextFile)
                    loaderManager.initLoader(0, args, this)
                }
            }


            return v
        }

        override fun onCreateLoader(id: Int, args: Bundle) =
                LicenseLoader(activity!!, args.getString(KEY_FILE_NAME))

        override fun onLoadFinished(loader: Loader<Spanned>, license: Spanned?) {
            view?.let { v ->
                v.license_text.autoLinkMask = Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS
                v.license_text.text = license
            }
        }

        override fun onLoaderReset(loader: Loader<Spanned>) {}
    }

    class LicenseLoader(
            context: Context,
            val fileName: String
    ): AsyncTaskLoader<Spanned>(context) {

        var content: Spanned? = null

        override fun onStartLoading() {
            if (content == null)
                forceLoad()
            else
                deliverResult(content)
        }

        override fun loadInBackground(): Spanned? {
            Logger.log.fine("Loading license file $fileName")
            try {
                context.resources.assets.open(fileName).use {
                    content = Html.fromHtml(IOUtils.toString(it, Charsets.UTF_8))
                    return content
                }
            } catch(e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't read license file", e)
                return null
            }
        }
    }


    interface ILicenseFragment {

        fun getFragment(): Fragment

    }

}
