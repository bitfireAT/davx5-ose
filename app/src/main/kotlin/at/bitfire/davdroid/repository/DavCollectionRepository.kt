/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.content.Context
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.exception.GoneException
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.okhttp.exception.NotFoundException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.CollectionType
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.util.DavUtils
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Version
import okhttp3.HttpUrl
import java.io.StringWriter
import java.util.UUID
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Repository for managing collections.
 */
class DavCollectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val logger: Logger,
    private val httpClientBuilder: Provider<HttpClientBuilder>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val productIds: Lazy<ProductIds>,
    private val serviceRepository: DavServiceRepository
) {

    private val dao = db.collectionDao()

    /**
     * Whether there are any collections that are registered for push.
     */
    suspend fun anyPushCapable() = dao.anyPushCapable()

    /**
     * Creates address book collection on server and locally
     */
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
            type = Collection.TYPE_ADDRESSBOOK,
            displayName = displayName,
            description = description
        )
        dao.insertAsync(collection)
    }

    /**
     * Create calendar collection on server and locally
     */
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
                timezoneId = timeZoneId,
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
            timezoneId = timeZoneId,
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
    suspend fun deleteRemote(collection: Collection) {
        val service = serviceRepository.getBlocking(collection.serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        val httpClient = httpClientBuilder.get().fromAccount(account).build()
        runInterruptible(ioDispatcher) {
            try {
                DavResource(httpClient, collection.url).delete {
                    // success, otherwise an exception would have been thrown → delete locally, too
                    delete(collection)
                }
            } catch (e: HttpException) {
                if (e is NotFoundException || e is GoneException) {
                    // HTTP 404 Not Found or 410 Gone (collection is not there anymore) -> delete locally, too
                    logger.info("Collection ${collection.url} not found on server, deleting locally")
                    delete(collection)
                } else
                    throw e
            }
        }
    }

    suspend fun getSyncableByTopic(topic: String) = dao.getSyncableByPushTopic(topic)

    fun get(id: Long) = dao.get(id)
    suspend fun getAsync(id: Long) = dao.getAsync(id)

    fun getFlow(id: Long) = dao.getFlow(id)

    suspend fun getByService(serviceId: Long) = dao.getByService(serviceId)

    fun getByServiceAndUrl(serviceId: Long, url: String) = dao.getByServiceAndUrl(serviceId, url)

    fun getByServiceAndSync(serviceId: Long) = dao.getByServiceAndSync(serviceId)

    fun getSyncCalendars(serviceId: Long) = dao.getSyncCalendars(serviceId)

    fun getSyncJtxCollections(serviceId: Long) = dao.getSyncJtxCollections(serviceId)

    fun getSyncTaskLists(serviceId: Long) = dao.getSyncTaskLists(serviceId)

    /** Returns all collections that are both selected for synchronization and push-capable. */
    suspend fun getPushCapableAndSyncable(serviceId: Long) = dao.getPushCapableSyncCollections(serviceId)

    suspend fun getPushRegistered(serviceId: Long) = dao.getPushRegistered(serviceId)
    suspend fun getPushRegisteredAndNotSyncable(serviceId: Long) = dao.getPushRegisteredAndNotSyncable(serviceId)

    suspend fun getVapidKey(serviceId: Long) = dao.getFirstVapidKey(serviceId)

    /**
     * Inserts or updates the collection.
     *
     * On update, it will _not_ update the flags
     *  - [Collection.sync] and
     *  - [Collection.forceReadOnly],
     *  but use the values of the already existing collection.
     *
     * @param newCollection Collection to be inserted or updated
     */
    fun insertOrUpdateByUrlRememberSync(newCollection: Collection) {
        db.runInTransaction {
            // remember locally set flags
            val oldCollection = dao.getByServiceAndUrl(newCollection.serviceId, newCollection.url.toString())
            val newCollectionWithFlags =
                if (oldCollection != null)
                    newCollection.copy(sync = oldCollection.sync, forceReadOnly = oldCollection.forceReadOnly)
                else
                    newCollection

            // commit new collection to database
            insertOrUpdateByUrl(newCollectionWithFlags)
        }
    }

    /**
     * Creates or updates the existing collection if it exists (URL)
     */
    fun insertOrUpdateByUrl(collection: Collection) {
        dao.insertOrUpdateByUrl(collection)
    }

    /**
     * Returns paging source to retrieve collections for given service, of given collection type and
     * depending on whether they are considered personal or not (see [HomeSet.personal]).
     */
    fun pageByServiceAndType(serviceId: Long, @CollectionType type: String, onlyPersonal: Boolean) =
        if (onlyPersonal)
            dao.pagePersonalByServiceAndType(serviceId, type)
        else
            dao.pageByServiceAndType(serviceId, type)

    /**
     * Sets the flag for whether read-only should be enforced on the local collection
     */
    suspend fun setForceReadOnly(id: Long, forceReadOnly: Boolean) {
        dao.updateForceReadOnly(id, forceReadOnly)
    }

    /**
     * Whether or not the local collection should be synced with the server
     */
    suspend fun setSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
    }

    suspend fun updatePushSubscription(id: Long, subscriptionUrl: String?, expires: Long?) {
        dao.updatePushSubscription(
            id = id,
            pushSubscription = subscriptionUrl,
            pushSubscriptionExpires = expires
        )
    }

    /**
     * Deletes the collection locally
     */
    fun delete(collection: Collection) {
        dao.delete(collection)
    }


    // helpers

    private suspend fun createOnServer(account: Account, url: HttpUrl, method: String, xmlBody: String) {
        val httpClient = httpClientBuilder.get()
            .fromAccount(account)
            .build()
        runInterruptible(ioDispatcher) {
            DavResource(httpClient, url).mkCol(
                xmlBody = xmlBody,
                method = method
            ) {
                // success, otherwise an exception would have been thrown
            }
        }
    }

    private fun generateMkColXml(
        addressBook: Boolean,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timezoneId: String? = null,
        supportsVEVENT: Boolean = true,
        supportsVTODO: Boolean = true,
        supportsVJOURNAL: Boolean = true
    ): String {
        val writer = StringWriter()
        val serializer = XmlUtils.newSerializer()
        serializer.apply {
            setOutput(writer)

            startDocument("UTF-8", null)
            setPrefix("", WebDAV.NS_WEBDAV)
            setPrefix("CAL", CalDAV.NS_CALDAV)
            setPrefix("CARD", CardDAV.NS_CARDDAV)

            if (addressBook)
                startTag(WebDAV.NS_WEBDAV, "mkcol")
            else
                startTag(CalDAV.NS_CALDAV, "mkcalendar")

            insertTag(WebDAV.Set) {
                insertTag(WebDAV.Prop) {
                    insertTag(WebDAV.ResourceType) {
                        insertTag(WebDAV.Collection)
                        if (addressBook)
                            insertTag(CardDAV.Addressbook)
                        else
                            insertTag(CalDAV.Calendar)
                    }

                    displayName?.let {
                        insertTag(WebDAV.DisplayName) {
                            text(it)
                        }
                    }

                    if (addressBook) {
                        // addressbook-specific properties
                        description?.let {
                            insertTag(CardDAV.AddressbookDescription) {
                                text(it)
                            }
                        }

                    } else {
                        // calendar-specific properties
                        description?.let {
                            insertTag(CalDAV.CalendarDescription) {
                                text(it)
                            }
                        }
                        color?.let {
                            insertTag(CalDAV.CalendarColor) {
                                text(DavUtils.ARGBtoCalDAVColor(it))
                            }
                        }
                        timezoneId?.let { id ->
                            insertTag(CalDAV.CalendarTimezoneId) {
                                text(id)
                            }
                            getVTimeZone(id)?.let { vTimezone ->
                                insertTag(CalDAV.CalendarTimezone) {
                                    text(
                                        // spec requires "an iCalendar object with exactly one VTIMEZONE component"
                                        Calendar(
                                            PropertyList<Property>().apply {
                                                add(Version.VERSION_2_0)
                                                add(ProdId(productIds.get().iCalProdId))
                                            },
                                            ComponentList(
                                                listOf(vTimezone)
                                            )
                                        ).toString()
                                    )
                                }
                            }
                        }

                        if (!supportsVEVENT || !supportsVTODO || !supportsVJOURNAL) {
                            insertTag(CalDAV.SupportedCalendarComponentSet) {
                                // Only if there's at least one not explicitly supported calendar component set,
                                // otherwise don't include the property, which means "supports everything".
                                if (supportsVEVENT)
                                    insertTag(CalDAV.Comp) {
                                        attribute(null, "name", Component.VEVENT)
                                    }
                                if (supportsVTODO)
                                    insertTag(CalDAV.Comp) {
                                        attribute(null, "name", Component.VTODO)
                                    }
                                if (supportsVJOURNAL)
                                    insertTag(CalDAV.Comp) {
                                        attribute(null, "name", Component.VJOURNAL)
                                    }
                            }
                        }
                    }
                }
            }
            if (addressBook)
                endTag(WebDAV.NS_WEBDAV, "mkcol")
            else
                endTag(CalDAV.NS_CALDAV, "mkcalendar")
            endDocument()
        }
        return writer.toString()
    }

    private fun getVTimeZone(tzId: String): VTimeZone? {
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        return tzRegistry.getTimeZone(tzId)?.vTimeZone
    }

}