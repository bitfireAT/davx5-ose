/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.app.Application
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.caldav.NS_APPLE_ICAL
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.ical4android.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.StringWriter
import java.util.UUID
import javax.inject.Inject

class DavCollectionRepository @Inject constructor(
    val context: Application,
    db: AppDatabase
) {

    private val serviceDao = db.serviceDao()
    private val dao = db.collectionDao()

    suspend fun anyWebcal(serviceId: Long) =
        dao.anyWebcal(serviceId)

    suspend fun createAddressBook(
        account: Account,
        homeSet: HomeSet,
        displayName: String,
        description: String?
    ) {
        val folderName = UUID.randomUUID().toString()
        val url = homeSet.url.newBuilder()
            .addPathSegment(folderName)
            .addPathSegment("")     // trailing slash
            .build()

        // create collection on server
        createOnServer(
            account = account,
            url = url,
            method = "MKCOL",
            xmlBody = generateMkColXml(
                addressBook = true,
                displayName = displayName,
                description = description
            )
        )

        // no HTTP error -> create collection locally
        val collection = Collection(
            serviceId = homeSet.serviceId,
            homeSetId = homeSet.id,
            url = url,
            type = Collection.TYPE_ADDRESSBOOK, //if (addressBook) Collection.TYPE_ADDRESSBOOK else Collection.TYPE_CALENDAR,
            displayName = displayName,
            description = description
        )
        dao.insertAsync(collection)
    }

    suspend fun createCalendar(
        account: Account,
        homeSet: HomeSet,
        color: Int?,
        displayName: String,
        description: String?,
        timeZoneId: String?,
        supportVEVENT: Boolean,
        supportVTODO: Boolean,
        supportVJOURNAL: Boolean
    ) {
        val folderName = UUID.randomUUID().toString()
        val url = homeSet.url.newBuilder()
            .addPathSegment(folderName)
            .addPathSegment("")     // trailing slash
            .build()

        // create collection on server
        createOnServer(
            account = account,
            url = url,
            method = "MKCALENDAR",
            xmlBody = generateMkColXml(
                addressBook = false,
                displayName = displayName,
                description = description,
                color = color,
                timezoneDef = timeZoneId,
                supportsVEVENT = supportVEVENT,
                supportsVTODO = supportVTODO,
                supportsVJOURNAL = supportVJOURNAL
            )
        )

        // no HTTP error -> create collection locally
        val collection = Collection(
            serviceId = homeSet.serviceId,
            homeSetId = homeSet.id,
            url = url,
            type = Collection.TYPE_CALENDAR,
            displayName = displayName,
            description = description,
            color = color,
            timezone = timeZoneId?.let { getVTimeZone(it) },
            supportsVEVENT = supportVEVENT,
            supportsVTODO = supportVTODO,
            supportsVJOURNAL = supportVJOURNAL
        )
        dao.insertAsync(collection)

        // Trigger service detection (because the collection may actually have other properties than the ones we have inserted).
        // Some servers are known to change the supported components (VEVENT, …) after creation.
        RefreshCollectionsWorker.enqueue(context, homeSet.serviceId)
    }

    /** Deletes the given collection from the server and the database. */
    suspend fun delete(collection: Collection) {
        val service = serviceDao.get(collection.serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                withContext(Dispatchers.IO) {
                    runInterruptible {
                        DavResource(httpClient.okHttpClient, collection.url).delete() {
                            // success, otherwise an exception would have been thrown
                            dao.delete(collection)
                        }
                    }
                }
            }
    }

    fun getFlow(id: Long) = dao.getFlow(id)

    suspend fun setForceReadOnly(id: Long, forceReadOnly: Boolean) {
        dao.updateForceReadOnly(id, forceReadOnly)
    }

    suspend fun setSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
    }


    // helpers

    private suspend fun createOnServer(account: Account, url: HttpUrl, method: String, xmlBody: String) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                withContext(Dispatchers.IO) {
                    runInterruptible {
                        DavResource(httpClient.okHttpClient, url).mkCol(
                            xmlBody = xmlBody,
                            method = method
                        ) {
                            // success, otherwise an exception would have been thrown
                        }
                    }
                }
            }
    }

    private fun generateMkColXml(
        addressBook: Boolean,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timezoneDef: String? = null,
        supportsVEVENT: Boolean = true,
        supportsVTODO: Boolean = true,
        supportsVJOURNAL: Boolean = true
    ): String {
        val writer = StringWriter()
        val serializer = XmlUtils.newSerializer()
        serializer.apply {
            setOutput(writer)

            startDocument("UTF-8", null)
            setPrefix("", NS_WEBDAV)
            setPrefix("CAL", NS_CALDAV)
            setPrefix("CARD", NS_CARDDAV)

            if (addressBook)
                startTag(NS_WEBDAV, "mkcol")
            else
                startTag(NS_CALDAV, "mkcalendar")
            startTag(NS_WEBDAV, "set")
            startTag(NS_WEBDAV, "prop")

            startTag(NS_WEBDAV, "resourcetype")
            startTag(NS_WEBDAV, "collection")
            endTag(NS_WEBDAV, "collection")
            if (addressBook) {
                startTag(NS_CARDDAV, "addressbook")
                endTag(NS_CARDDAV, "addressbook")
            } else {
                startTag(NS_CALDAV, "calendar")
                endTag(NS_CALDAV, "calendar")
            }
            endTag(NS_WEBDAV, "resourcetype")

            displayName?.let {
                startTag(NS_WEBDAV, "displayname")
                text(it)
                endTag(NS_WEBDAV, "displayname")
            }

            if (addressBook) {
                // addressbook-specific properties
                description?.let {
                    startTag(NS_CARDDAV, "addressbook-description")
                    text(it)
                    endTag(NS_CARDDAV, "addressbook-description")
                }

            } else {
                // calendar-specific properties
                description?.let {
                    startTag(NS_CALDAV, "calendar-description")
                    text(it)
                    endTag(NS_CALDAV, "calendar-description")
                }
                color?.let {
                    startTag(NS_APPLE_ICAL, "calendar-color")
                    text(DavUtils.ARGBtoCalDAVColor(it))
                    endTag(NS_APPLE_ICAL, "calendar-color")
                }
                timezoneDef?.let {
                    startTag(NS_CALDAV, "calendar-timezone")
                    cdsect(it)
                    endTag(NS_CALDAV, "calendar-timezone")
                }

                if (!supportsVEVENT || !supportsVTODO || !supportsVJOURNAL) {
                    // Only if there's at least one not explicitly supported calendar component set,
                    // otherwise don't include the property, which means "supports everything".
                    if (supportsVEVENT) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VEVENT")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVTODO) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VTODO")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVJOURNAL) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VJOURNAL")
                        endTag(NS_CALDAV, "comp")
                    }
                }
            }

            endTag(NS_WEBDAV, "prop")
            endTag(NS_WEBDAV, "set")
            if (addressBook)
                endTag(NS_WEBDAV, "mkcol")
            else
                endTag(NS_CALDAV, "mkcalendar")
            endDocument()
        }
        return writer.toString()
    }

    private fun getVTimeZone(tzId: String): String? =
        DateUtils.ical4jTimeZone(tzId)?.toString()

}