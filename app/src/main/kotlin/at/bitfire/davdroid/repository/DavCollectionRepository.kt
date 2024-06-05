/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.app.Application
import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.ical4android.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.model.Component
import okhttp3.HttpUrl
import java.io.StringWriter
import java.util.UUID
import javax.inject.Inject

class DavCollectionRepository @Inject constructor(
    @ApplicationContext val context: Context,
    db: AppDatabase
) {

    private val serviceDao = db.serviceDao()
    private val dao = db.collectionDao()

    suspend fun anyWebcal(serviceId: Long) =
        dao.anyOfType(serviceId, Collection.TYPE_WEBCAL)

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

    suspend fun setForceReadOnly(id: Long, forceReadOnly: Boolean) =
        dao.updateForceReadOnly(id, forceReadOnly)

    suspend fun setSync(id: Long, forceReadOnly: Boolean) =
        dao.updateSync(id, forceReadOnly)

    fun insertOrUpdateByUrl(collection: Collection): Long =
        dao.insertOrUpdateByUrl(collection)

    /**
     * Inserts or updates the collection. On update it will not update flag values ([Collection.sync],
     * [Collection.forceReadOnly]), but use the values of the already existing collection.
     *
     * @param newCollection Collection to be inserted or updated
     */
    fun insertOrUpdateByUrlAndRememberFlags(newCollection: Collection) {
        // remember locally set flags
        dao.getByServiceAndUrl(newCollection.serviceId, newCollection.url.toString())?.let { oldCollection ->
            newCollection.sync = oldCollection.sync
            newCollection.forceReadOnly = oldCollection.forceReadOnly
        }

        // commit to database
        insertOrUpdateByUrl(newCollection)
    }

    fun deleteLocal(collection: Collection) =
        dao.delete(collection)


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

            insertTag(DavResource.SET) {
                insertTag(DavResource.PROP) {
                    insertTag(ResourceType.NAME) {
                        insertTag(ResourceType.COLLECTION)
                        if (addressBook)
                            insertTag(ResourceType.ADDRESSBOOK)
                        else
                            insertTag(ResourceType.CALENDAR)
                    }

                    displayName?.let {
                        insertTag(DisplayName.NAME) {
                            text(it)
                        }
                    }

                    if (addressBook) {
                        // addressbook-specific properties
                        description?.let {
                            insertTag(AddressbookDescription.NAME) {
                                text(it)
                            }
                        }

                    } else {
                        // calendar-specific properties
                        description?.let {
                            insertTag(CalendarDescription.NAME) {
                                text(it)
                            }
                        }
                        color?.let {
                            insertTag(CalendarColor.NAME) {
                                text(DavUtils.ARGBtoCalDAVColor(it))
                            }
                        }
                        timezoneDef?.let {
                            insertTag(CalendarTimezone.NAME) {
                                cdsect(it)
                            }
                        }

                        if (!supportsVEVENT || !supportsVTODO || !supportsVJOURNAL) {
                            insertTag(SupportedCalendarComponentSet.NAME) {
                                // Only if there's at least one not explicitly supported calendar component set,
                                // otherwise don't include the property, which means "supports everything".
                                if (supportsVEVENT)
                                    insertTag(SupportedCalendarComponentSet.COMP) {
                                        attribute(null, "name", Component.VEVENT)
                                    }
                                if (supportsVTODO)
                                    insertTag(SupportedCalendarComponentSet.COMP) {
                                        attribute(null, "name", Component.VTODO)
                                    }
                                if (supportsVJOURNAL)
                                    insertTag(SupportedCalendarComponentSet.COMP) {
                                        attribute(null, "name", Component.VJOURNAL)
                                    }
                            }
                        }
                    }
                }
            }
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