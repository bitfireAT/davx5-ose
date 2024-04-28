/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.lifecycle.ViewModel

class CollectionModel: ViewModel() {

    /*
    val createCollectionResult = MutableLiveData<Optional<Exception>>()
    /**
     * Creates a WebDAV collection using MKCOL or MKCALENDAR.
     *
     * @param homeSet       home set into which the collection shall be created
     * @param addressBook   *true* if an address book shall be created, *false* if a calendar should be created
     * @param name          name (path segment) of the collection
     */
    fun createCollection(
        homeSet: HomeSet,
        addressBook: Boolean,
        name: String,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timeZoneId: String? = null,
        supportsVEVENT: Boolean? = null,
        supportsVTODO: Boolean? = null,
        supportsVJOURNAL: Boolean? = null
    ) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    // delete on server
                    val url = homeSet.url.newBuilder()
                        .addPathSegment(name)
                        .addPathSegment("")     // trailing slash
                        .build()
                    val dav = DavResource(httpClient.okHttpClient, url)

                    val xml = generateMkColXml(
                        addressBook = addressBook,
                        displayName = displayName,
                        description = description,
                        color = color,
                        timezoneDef = timeZoneId?.let { tzId ->
                            DateUtils.ical4jTimeZone(tzId)?.let { tz ->
                                val cal = Calendar()
                                cal.components += tz.vTimeZone
                                cal.toString()
                            }
                        },
                        supportsVEVENT = supportsVEVENT,
                        supportsVTODO = supportsVTODO,
                        supportsVJOURNAL = supportsVJOURNAL
                    )

                    dav.mkCol(
                        xmlBody = xml,
                        method = if (addressBook) "MKCOL" else "MKCALENDAR"
                    ) {
                        // success, otherwise an exception would have been thrown
                    }

                    // no HTTP error -> create collection locally
                    val collection = Collection(
                        serviceId = homeSet.serviceId,
                        homeSetId = homeSet.id,
                        url = url,
                        type = if (addressBook) Collection.TYPE_ADDRESSBOOK else Collection.TYPE_CALENDAR,
                        displayName = displayName,
                        description = description
                    )
                    db.collectionDao().insert(collection)

                    // trigger service detection (because the collection may actually have other properties than the ones we have inserted)
                    RefreshCollectionsWorker.enqueue(context, homeSet.serviceId)

                    // post success
                    createCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't create collection", e)
                    // post error
                    createCollectionResult.postValue(Optional.of(e))
                }
            }
    }

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

    val deleteCollectionResult = MutableLiveData<Optional<Exception>>()
    /** Deletes the given collection from the database and the server. */
    fun deleteCollection(collection: Collection) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    // delete on server
                    val davResource = DavResource(httpClient.okHttpClient, collection.url)
                    davResource.delete(null) {}

                    // delete in database
                    db.collectionDao().delete(collection)

                    // post success
                    deleteCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't delete collection", e)
                    // post error
                    deleteCollectionResult.postValue(Optional.of(e))
                }
            }
    }
     */

}