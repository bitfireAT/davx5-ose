/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.settings.AccountSettings
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Level
import kotlin.concurrent.thread

class CreateCollectionFragment: DialogFragment() {

    companion object {

        const val ARG_ACCOUNT = "account"
        const val ARG_COLLECTION_INFO = "collectionInfo"

        fun newInstance(account: Account, info: CollectionInfo): CreateCollectionFragment {
            val frag = CreateCollectionFragment()
            val args = Bundle(2)
            args.putParcelable(ARG_ACCOUNT, account)
            args.putParcelable(ARG_COLLECTION_INFO, info)
            frag.arguments = args
            return frag
        }

    }

    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProviders.of(this).get(Model::class.java)
        model.account = arguments?.getParcelable(ARG_ACCOUNT) ?: throw IllegalArgumentException()
        model.info = arguments?.getParcelable(ARG_COLLECTION_INFO) ?: throw IllegalArgumentException()

        model.createCollection().observe(this, Observer { exception ->
            if (exception != null)
                requireFragmentManager().beginTransaction()
                        .add(ExceptionInfoFragment.newInstance(exception, model.account), null)
                        .commit()
            else
                requireActivity().finish()
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.create_collection, container, false)
        isCancelable = false
        return v
    }


    class Model(
            application: Application
    ): AndroidViewModel(application) {

        lateinit var account: Account
        lateinit var info: CollectionInfo

        val result = MutableLiveData<Exception>()

        fun createCollection(): LiveData<Exception> {
            thread {
                HttpClient.Builder(getApplication(), AccountSettings(getApplication(), account))
                        .setForeground(true)
                        .build().use { httpClient ->
                    try {
                        val collection = DavResource(httpClient.okHttpClient, info.url)

                        // create collection on remote server
                        collection.mkCol(generateXml()) {}

                        // no HTTP error -> create collection locally
                        ServiceDB.OpenHelper(getApplication()).use { dbHelper ->
                            val db = dbHelper.writableDatabase

                            // 1. find service ID
                            val serviceType = when (info.type) {
                                CollectionInfo.Type.ADDRESS_BOOK -> ServiceDB.Services.SERVICE_CARDDAV
                                CollectionInfo.Type.CALENDAR -> ServiceDB.Services.SERVICE_CALDAV
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

                        // post success
                        result.postValue(null)
                    } catch (e: Exception) {
                        // post error
                        result.postValue(e)
                    }
                }
            }
            return result
        }

        fun generateXml(): String {
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
                        if (info.supportsVJOURNAL) {
                            startTag(XmlUtils.NS_CALDAV, "comp")
                            attribute(null, "name", "VJOURNAL")
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

            return writer.toString()
        }

    }

}
