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
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.StringWriter
import java.util.UUID
import javax.inject.Inject

class DavCollectionRepository @Inject constructor(
    val context: Application,
    db: AppDatabase
) {

    private val dao = db.collectionDao()

    suspend fun anyWebcal(serviceId: Long) =
        dao.anyWebcal(serviceId)

    suspend fun createAddressBook(
        account: Account,
        homeSet: HomeSet,
        displayName: String,
        description: String
    ) {
        val folderName = UUID.randomUUID().toString()

        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    val url = homeSet.url.newBuilder()
                        .addPathSegment(folderName)
                        .addPathSegment("")     // trailing slash
                        .build()
                    val dav = DavResource(httpClient.okHttpClient, url)

                    val xml = generateMkColXml(
                        addressBook = true,
                        displayName = displayName,
                        description = description
                    )

                    withContext(Dispatchers.IO) {
                        runInterruptible {
                            dav.mkCol(
                                xmlBody = xml,
                                method = "MKCOL"    //if (addressBook) "MKCOL" else "MKCALENDAR"
                            ) {
                                // success, otherwise an exception would have been thrown
                            }
                        }
                    }

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

                    // trigger service detection (because the collection may actually have other properties than the ones we have inserted)
                    RefreshCollectionsWorker.enqueue(context, homeSet.serviceId)

                    // post success
                    //createCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    //Logger.log.log(Level.SEVERE, "Couldn't create collection", e)
                    // post error
                    //createCollectionResult.postValue(Optional.of(e))
                }
            }
    }

    suspend fun setCollectionSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
    }


    // helpers

    private fun generateMkColXml(
        addressBook: Boolean,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timezoneDef: String? = null,
        supportsVEVENT: Boolean? = null,
        supportsVTODO: Boolean? = null,
        supportsVJOURNAL: Boolean? = null
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

                if (supportsVEVENT != null || supportsVTODO != null || supportsVJOURNAL != null) {
                    // only if there's at least one explicitly supported calendar component set, otherwise don't include the property
                    if (supportsVEVENT != false) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VEVENT")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVTODO != false) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VTODO")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVJOURNAL != false) {
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

}