/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.XmlUtils
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import okhttp3.HttpUrl
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Level

@Suppress("DEPRECATION")
class CreateCollectionFragment: DialogFragment(), LoaderManager.LoaderCallbacks<Exception> {

    companion object {

        val ARG_ACCOUNT = "account"
        val ARG_COLLECTION_INFO = "collectionInfo"

        fun newInstance(account: Account, info: CollectionInfo): CreateCollectionFragment {
            val frag = CreateCollectionFragment()
            val args = Bundle(2)
            args.putParcelable(ARG_ACCOUNT, account)
            args.putSerializable(ARG_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }

    }

    private lateinit var account: Account
    private lateinit var info: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments!!.getParcelable(ARG_ACCOUNT)
        info = arguments!!.getSerializable(ARG_COLLECTION_INFO) as CollectionInfo

        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.create_collection_creating)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }


    override fun onCreateLoader(id: Int, args: Bundle?) = CreateCollectionLoader(activity!!, account, info)

    override fun onLoadFinished(loader: Loader<Exception>, exception: Exception?) {
        dismissAllowingStateLoss()

        activity?.let { parent ->
            if (exception != null)
                fragmentManager!!.beginTransaction()
                        .add(ExceptionInfoFragment.newInstance(exception, account), null)
                        .commitAllowingStateLoss()
            else
                parent.finish()
        }

    }

    override fun onLoaderReset(loader: Loader<Exception>) {}


    class CreateCollectionLoader(
            context: Context,
            val account: Account,
            val info: CollectionInfo
    ): AsyncTaskLoader<Exception>(context) {

        override fun onStartLoading() = forceLoad()

        override fun loadInBackground(): Exception? {
            val writer = StringWriter()
            try {
                val serializer = XmlUtils.newSerializer()
                with(serializer) {
                    setOutput(writer)
                    startDocument("UTF-8", null)
                    setPrefix("", XmlUtils.NS_WEBDAV)
                    setPrefix("CAL", XmlUtils.NS_CALDAV)
                    setPrefix("CARD", XmlUtils.NS_CARDDAV)
    
                    startTag(XmlUtils.NS_WEBDAV, "mkcol")
                        startTag(XmlUtils.NS_WEBDAV, "set")
                            startTag(XmlUtils.NS_WEBDAV, "prop")
                                startTag(XmlUtils.NS_WEBDAV, "resourcetype")
                                    startTag(XmlUtils.NS_WEBDAV, "collection")
                                    endTag(XmlUtils.NS_WEBDAV, "collection")
                                    if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                                        startTag(XmlUtils.NS_CARDDAV, "addressbook")
                                        endTag(XmlUtils.NS_CARDDAV, "addressbook")
                                    } else if (info.type == CollectionInfo.Type.CALENDAR) {
                                        startTag(XmlUtils.NS_CALDAV, "calendar")
                                        endTag(XmlUtils.NS_CALDAV, "calendar")
                                    }
                                endTag(XmlUtils.NS_WEBDAV, "resourcetype")
                                info.displayName?.let {
                                    startTag(XmlUtils.NS_WEBDAV, "displayname")
                                        text(it)
                                    endTag(XmlUtils.NS_WEBDAV, "displayname")
                                }
    
                                // addressbook-specific properties
                                if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                                    info.description?.let {
                                        startTag(XmlUtils.NS_CARDDAV, "addressbook-description")
                                        text(it)
                                        endTag(XmlUtils.NS_CARDDAV, "addressbook-description")
                                    }
                                }
    
                                // calendar-specific properties
                                if (info.type == CollectionInfo.Type.CALENDAR) {
                                    info.description?.let {
                                        startTag(XmlUtils.NS_CALDAV, "calendar-description")
                                        text(it)
                                        endTag(XmlUtils.NS_CALDAV, "calendar-description")
                                    }

                                    info.color?.let {
                                        startTag(XmlUtils.NS_APPLE_ICAL, "calendar-color")
                                        text(DavUtils.ARGBtoCalDAVColor(it))
                                        endTag(XmlUtils.NS_APPLE_ICAL, "calendar-color")
                                    }

                                    info.timeZone?.let {
                                        startTag(XmlUtils.NS_CALDAV, "calendar-timezone")
                                        cdsect(it)
                                        endTag(XmlUtils.NS_CALDAV, "calendar-timezone")
                                    }
    
                                    startTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set")
                                    if (info.supportsVEVENT) {
                                        startTag(XmlUtils.NS_CALDAV, "comp")
                                        attribute(null, "name", "VEVENT")
                                        endTag(XmlUtils.NS_CALDAV, "comp")
                                    }
                                    if (info.supportsVTODO) {
                                        startTag(XmlUtils.NS_CALDAV, "comp")
                                        attribute(null, "name", "VTODO")
                                        endTag(XmlUtils.NS_CALDAV, "comp")
                                    }
                                    endTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set")
                                }
    
                            endTag(XmlUtils.NS_WEBDAV, "prop")
                        endTag(XmlUtils.NS_WEBDAV, "set")
                    endTag(XmlUtils.NS_WEBDAV, "mkcol")
                    endDocument()
                }
            } catch(e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't assemble Extended MKCOL request", e)
            }

            var httpClient: HttpClient? = null
            try {
                httpClient = HttpClient.Builder(context, account)
                        .setForeground(true)
                        .build()
                val collection = DavResource(httpClient.okHttpClient, HttpUrl.parse(info.url)!!)

                // create collection on remote server
                collection.mkCol(writer.toString())

                // now insert collection into database:
                ServiceDB.OpenHelper(context).use { dbHelper ->
                    val db = dbHelper.writableDatabase

                    // 1. find service ID
                    val serviceType = when (info.type) {
                        CollectionInfo.Type.ADDRESS_BOOK -> ServiceDB.Services.SERVICE_CARDDAV
                        CollectionInfo.Type.CALENDAR     -> ServiceDB.Services.SERVICE_CALDAV
                        else -> throw IllegalArgumentException("Collection must be an address book or calendar")
                    }
                    db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                            "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                            arrayOf(account.name, serviceType), null, null, null).use { c ->

                        assert(c.moveToNext())
                        val serviceID = c.getLong(0)

                        // 2. add collection to service
                        val values = info.toDB()
                        values.put(ServiceDB.Collections.SERVICE_ID, serviceID)
                        db.insert(ServiceDB.Collections._TABLE, null, values)
                    }
                }
            } catch(e: Exception) {
                return e
            } finally {
                httpClient?.close()
            }
            return null
        }
    }

}
