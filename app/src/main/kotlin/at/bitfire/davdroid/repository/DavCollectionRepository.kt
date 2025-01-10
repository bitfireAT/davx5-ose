/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.CalendarTimezoneId
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
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.component.VTimeZone
import okhttp3.HttpUrl
import java.io.StringWriter
import java.util.Collections
import java.util.UUID
import javax.inject.Inject

/**
 * Repository for managing collections.
 *
 * Implements an observer pattern that can be used to listen for changes of collections.
 */
class DavCollectionRepository @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    defaultListeners: Lazy<Set<@JvmSuppressWildcards OnChangeListener>>,
    private val serviceRepository: DavServiceRepository
) {

    private val listeners by lazy { Collections.synchronizedSet(defaultListeners.get().toMutableSet()) }

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

        notifyOnChangeListeners()
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

        notifyOnChangeListeners()
    }

    /** Deletes the given collection from the server and the database. */
    suspend fun deleteRemote(collection: Collection) {
        val service = serviceRepository.get(collection.serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        HttpClient.Builder(context, accountSettingsFactory.create(account))
            .setForeground(true)
            .build().use { httpClient ->
                withContext(Dispatchers.IO) {
                    runInterruptible {
                        DavResource(httpClient.okHttpClient, collection.url).delete() {
                            // success, otherwise an exception would have been thrown → delete locally, too
                            delete(collection)
                        }
                    }
                }
            }
    }

    fun getSyncableByTopic(topic: String) = dao.getSyncableByPushTopic(topic)

    fun get(id: Long) = dao.get(id)

    fun getFlow(id: Long) = dao.getFlow(id)

    fun getByService(serviceId: Long) = dao.getByService(serviceId)

    fun getByServiceAndUrl(serviceId: Long, url: String) = dao.getByServiceAndUrl(serviceId, url)

    fun getByServiceAndSync(serviceId: Long) = dao.getByServiceAndSync(serviceId)

    fun getSyncCalendars(serviceId: Long) = dao.getSyncCalendars(serviceId)

    fun getSyncJtxCollections(serviceId: Long) = dao.getSyncJtxCollections(serviceId)

    fun getSyncTaskLists(serviceId: Long) = dao.getSyncTaskLists(serviceId)

    /** Returns all collections that are both selected for synchronization and push-capable. */
    suspend fun getPushCapableAndSyncable(): List<Collection> =
        dao.getPushCapableSyncCollections()

    suspend fun getPushRegisteredAndNotSyncable(): List<Collection> =
        dao.getPushRegisteredAndNotSyncable()

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
    fun insertOrUpdateByUrlAndRememberFlags(newCollection: Collection) {
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
        notifyOnChangeListeners()
    }

    fun pageByServiceAndType(serviceId: Long, type: String) =
        dao.pageByServiceAndType(serviceId, type)

    fun pagePersonalByServiceAndType(serviceId: Long, type: String) =
        dao.pagePersonalByServiceAndType(serviceId, type)

    /**
     * Sets the flag for whether read-only should be enforced on the local collection
     */
    suspend fun setForceReadOnly(id: Long, forceReadOnly: Boolean) {
        dao.updateForceReadOnly(id, forceReadOnly)
        notifyOnChangeListeners()
    }

    /**
     * Whether or not the local collection should be synced with the server
     */
    suspend fun setSync(id: Long, forceReadOnly: Boolean) {
        dao.updateSync(id, forceReadOnly)
        notifyOnChangeListeners()
    }

    fun updatePushSubscription(id: Long, subscriptionUrl: String?, expires: Long?) {
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
        notifyOnChangeListeners()
    }


    // helpers

    private suspend fun createOnServer(account: Account, url: HttpUrl, method: String, xmlBody: String) {
        HttpClient.Builder(context, accountSettingsFactory.create(account))
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
                        timezoneId?.let { id ->
                            insertTag(CalendarTimezoneId.NAME) {
                                text(id)
                            }
                            getVTimeZone(id)?.let { vTimezone ->
                                insertTag(CalendarTimezone.NAME) {
                                    text(
                                        // spec requires "an iCalendar object with exactly one VTIMEZONE component"
                                        Calendar(ComponentList(listOf(vTimezone))).toString()
                                    )
                                }
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

    private fun getVTimeZone(tzId: String): VTimeZone? = DateUtils.ical4jTimeZone(tzId)?.vTimeZone


    /*** OBSERVERS ***/

    /**
     * Notifies registered listeners about changes in the collections.
     */
    private fun notifyOnChangeListeners() = synchronized(listeners) {
        listeners.forEach { listener ->
            listener.onCollectionsChanged()
        }
    }

    fun interface OnChangeListener {
        /**
         * Will be called when collections have changed. Will run in the coroutine context/thread
         * of the data-modifying method. For instance, if [delete] is called, [onCollectionsChanged]
         * will be called in the context/thread that called [delete].
         */
        fun onCollectionsChanged()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class DavCollectionRepositoryModule {
        // Provides empty set of listeners
        @Multibinds abstract fun defaultOnChangeListeners(): Set<OnChangeListener>
    }

}