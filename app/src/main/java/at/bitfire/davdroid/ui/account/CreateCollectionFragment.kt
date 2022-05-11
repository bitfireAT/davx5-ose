/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.davdroid.DavService
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.ExceptionInfoFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Level

class CreateCollectionFragment: DialogFragment() {

    companion object {
        const val ARG_ACCOUNT = "account"
        const val ARG_SERVICE_TYPE = "serviceType"

        const val ARG_TYPE = "type"
        const val ARG_URL = "url"
        const val ARG_DISPLAY_NAME = "displayName"
        const val ARG_DESCRIPTION = "description"

        // CalDAV only
        const val ARG_COLOR = "color"
        const val ARG_TIMEZONE = "timezone"
        const val ARG_SUPPORTS_VEVENT = "supportsVEVENT"
        const val ARG_SUPPORTS_VTODO = "supportsVTODO"
        const val ARG_SUPPORTS_VJOURNAL = "supportsVJOURNAL"
    }

    val model by viewModels<Model>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments ?: throw IllegalArgumentException()

        model.account = args.getParcelable(ARG_ACCOUNT) ?: throw IllegalArgumentException("ARG_ACCOUNT required")
        model.serviceType = args.getString(ARG_SERVICE_TYPE) ?: throw java.lang.IllegalArgumentException("ARG_SERVICE_TYPE required")

        model.collection = Collection(
                type = args.getString(ARG_TYPE) ?: throw IllegalArgumentException("ARG_TYPE required"),
                url = (args.getString(ARG_URL) ?: throw IllegalArgumentException("ARG_URL required")).toHttpUrl(),
                displayName = args.getString(ARG_DISPLAY_NAME),
                description = args.getString(ARG_DESCRIPTION),

                color = args.ifDefined(ARG_COLOR) { it.getInt(ARG_COLOR) },
                timezone = args.getString(ARG_TIMEZONE),
                supportsVEVENT = args.ifDefined(ARG_SUPPORTS_VEVENT) { it.getBoolean(ARG_SUPPORTS_VEVENT) },
                supportsVTODO = args.ifDefined(ARG_SUPPORTS_VTODO) { it.getBoolean(ARG_SUPPORTS_VTODO) },
                supportsVJOURNAL = args.ifDefined(ARG_SUPPORTS_VJOURNAL) { it.getBoolean(ARG_SUPPORTS_VJOURNAL) },

                sync = true     /* by default, sync collections which just have been created */
        )

        model.createCollection().observe(this, Observer { exception ->
            if (exception == null)
                requireActivity().finish()
            else {
                dismiss()
                parentFragmentManager.beginTransaction()
                        .add(ExceptionInfoFragment.newInstance(exception, model.account), null)
                        .commit()
            }
        })
    }

    private fun<T> Bundle.ifDefined(name: String, getter: (Bundle) -> T): T? =
            if (containsKey(name))
                getter(this)
            else
                null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.create_collection, container, false)
        isCancelable = false
        return v
    }


    class Model(
            app: Application
    ): AndroidViewModel(app), KoinComponent {

        lateinit var account: Account
        lateinit var serviceType: String
        lateinit var collection: Collection

        val result = MutableLiveData<Exception>()

        fun createCollection(): LiveData<Exception> {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                HttpClient.Builder(getApplication(), AccountSettings(getApplication(), account))
                        .setForeground(true)
                        .build().use { httpClient ->
                    try {
                        val dav = DavResource(httpClient.okHttpClient, collection.url)

                        // create collection on remote server
                        dav.mkCol(generateXml()) {}

                        // no HTTP error -> create collection locally
                        val db = get<AppDatabase>()
                        db.serviceDao().getByAccountAndType(account.name, serviceType)?.let { service ->
                            collection.serviceId = service.id
                            db.collectionDao().insert(collection)

                            // trigger service detection (because the collection may have other properties than the ones we have inserted)
                            DavService.refreshCollections(getApplication(), service.id)
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

        private fun generateXml(): String {
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
                    if (collection.type == Collection.TYPE_ADDRESSBOOK) {
                        startTag(XmlUtils.NS_CARDDAV, "addressbook")
                        endTag(XmlUtils.NS_CARDDAV, "addressbook")
                    } else if (collection.type == Collection.TYPE_CALENDAR) {
                        startTag(XmlUtils.NS_CALDAV, "calendar")
                        endTag(XmlUtils.NS_CALDAV, "calendar")
                    }
                    endTag(XmlUtils.NS_WEBDAV, "resourcetype")
                    collection.displayName?.let {
                        startTag(XmlUtils.NS_WEBDAV, "displayname")
                        text(it)
                        endTag(XmlUtils.NS_WEBDAV, "displayname")
                    }

                    // addressbook-specific properties
                    if (collection.type == Collection.TYPE_ADDRESSBOOK) {
                        collection.description?.let {
                            startTag(XmlUtils.NS_CARDDAV, "addressbook-description")
                            text(it)
                            endTag(XmlUtils.NS_CARDDAV, "addressbook-description")
                        }
                    }

                    // calendar-specific properties
                    if (collection.type == Collection.TYPE_CALENDAR) {
                        collection.description?.let {
                            startTag(XmlUtils.NS_CALDAV, "calendar-description")
                            text(it)
                            endTag(XmlUtils.NS_CALDAV, "calendar-description")
                        }

                        collection.color?.let {
                            startTag(XmlUtils.NS_APPLE_ICAL, "calendar-color")
                            text(DavUtils.ARGBtoCalDAVColor(it))
                            endTag(XmlUtils.NS_APPLE_ICAL, "calendar-color")
                        }

                        collection.timezone?.let {
                            startTag(XmlUtils.NS_CALDAV, "calendar-timezone")
                            cdsect(it)
                            endTag(XmlUtils.NS_CALDAV, "calendar-timezone")
                        }

                        if (collection.supportsVEVENT != null || collection.supportsVTODO != null || collection.supportsVJOURNAL != null) {
                            // only if there's at least one explicitly supported calendar component set, otherwise don't include the property
                            startTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set")
                            if (collection.supportsVEVENT != false) {
                                startTag(XmlUtils.NS_CALDAV, "comp")
                                attribute(null, "name", "VEVENT")
                                endTag(XmlUtils.NS_CALDAV, "comp")
                            }
                            if (collection.supportsVTODO != false) {
                                startTag(XmlUtils.NS_CALDAV, "comp")
                                attribute(null, "name", "VTODO")
                                endTag(XmlUtils.NS_CALDAV, "comp")
                            }
                            if (collection.supportsVJOURNAL != false) {
                                startTag(XmlUtils.NS_CALDAV, "comp")
                                attribute(null, "name", "VJOURNAL")
                                endTag(XmlUtils.NS_CALDAV, "comp")
                            }
                            endTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set")
                        }
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
