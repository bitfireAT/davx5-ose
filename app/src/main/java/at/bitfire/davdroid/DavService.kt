/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import android.accounts.Account
import android.app.IntentService
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.db.*
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level
import javax.inject.Inject
import kotlin.collections.*

@Suppress("DEPRECATION")
@AndroidEntryPoint
class DavService: IntentService("DavService") {

    companion object {

        const val ACTION_REFRESH_COLLECTIONS = "refreshCollections"
        const val EXTRA_DAV_SERVICE_ID = "davServiceID"

        /** Initialize a forced synchronization. Expects intent data
            to be an URI of this format:
            contents://<authority>/<account.type>/<account name>
         **/
        const val ACTION_FORCE_SYNC = "forceSync"

        val DAV_COLLECTION_PROPERTIES = arrayOf(
                ResourceType.NAME,
                CurrentUserPrivilegeSet.NAME,
                DisplayName.NAME,
                Owner.NAME,
                AddressbookDescription.NAME, SupportedAddressData.NAME,
                CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
                Source.NAME
        )

        fun refreshCollections(context: Context, serviceId: Long) {
            val intent = Intent(context, DavService::class.java)
            intent.action = DavService.ACTION_REFRESH_COLLECTIONS
            intent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceId)
            context.startService(intent)
        }

    }

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var settings: SettingsManager

    /**
     * List of [Service] IDs for which the collections are currently refreshed
     */
    private val runningRefresh = Collections.synchronizedSet(HashSet<Long>())

    /**
     * Currently registered [RefreshingStatusListener]s, which will be notified
     * when a collection refresh status changes
     */
    private val refreshingStatusListeners = Collections.synchronizedList(LinkedList<WeakReference<RefreshingStatusListener>>())

    @WorkerThread
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null)
            return

        val id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1)

        when (intent.action) {
            ACTION_REFRESH_COLLECTIONS ->
                if (runningRefresh.add(id)) {
                    refreshingStatusListeners.forEach { listener ->
                        listener.get()?.onDavRefreshStatusChanged(id, true)
                    }

                    refreshCollections(id)
                }

            ACTION_FORCE_SYNC -> {
                val uri = intent.data!!
                val authority = uri.authority!!
                val account = Account(
                        uri.pathSegments[1],
                        uri.pathSegments[0]
                )
                forceSync(authority, account)
            }

        }
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    interface RefreshingStatusListener {
        fun onDavRefreshStatusChanged(id: Long, refreshing: Boolean)
    }

    private val binder = InfoBinder()

    inner class InfoBinder: Binder() {
        fun isRefreshing(id: Long) = runningRefresh.contains(id)

        fun addRefreshingStatusListener(listener: RefreshingStatusListener, callImmediateIfRunning: Boolean) {
            refreshingStatusListeners += WeakReference<RefreshingStatusListener>(listener)
            if (callImmediateIfRunning)
                synchronized(runningRefresh) {
                    for (id in runningRefresh)
                        listener.onDavRefreshStatusChanged(id, true)
                }
        }

        fun removeRefreshingStatusListener(listener: RefreshingStatusListener) {
            val iter = refreshingStatusListeners.iterator()
            while (iter.hasNext()) {
                val item = iter.next().get()
                if (item == listener || item == null)
                    iter.remove()
            }
        }
    }

    override fun onBind(intent: Intent?) = binder



    /* ACTION RUNNABLES
       which actually do the work
     */

    private fun forceSync(authority: String, account: Account) {
        Logger.log.info("Forcing $authority synchronization of $account")
        val extras = Bundle(2)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
        ContentResolver.requestSync(account, authority, extras)
    }

    private fun refreshCollections(serviceId: Long) {
        val syncAllCollections = settings.getBoolean(Settings.SYNC_ALL_COLLECTIONS)

        val homeSetDao = db.homeSetDao()
        val collectionDao = db.collectionDao()

        val service = db.serviceDao().get(serviceId) ?: throw IllegalArgumentException("Service not found")
        val account = Account(service.accountName, getString(R.string.account_type))

        val homeSets = homeSetDao.getByService(serviceId).associateBy { it.url }.toMutableMap()
        val collections = collectionDao.getByService(serviceId).associateBy { it.url }.toMutableMap()

        /**
         * Checks if the given URL defines home sets and adds them to the home set list.
         *
         * @param personal Whether this is the "outer" call of the recursion.
         *
         * *true* = found home sets belong to the current-user-principal; recurse if
         * calendar proxies or group memberships are found
         *
         * *false* = found home sets don't directly belong to the current-user-principal; don't recurse
         *
         * @throws java.io.IOException
         * @throws HttpException
         * @throws at.bitfire.dav4jvm.exception.DavException
         */
        fun queryHomeSets(client: OkHttpClient, url: HttpUrl, personal: Boolean = true) {
            val related = mutableSetOf<HttpUrl>()

            fun findRelated(root: HttpUrl, dav: Response) {
                // refresh home sets: calendar-proxy-read/write-for
                dav[CalendarProxyReadFor::class.java]?.let {
                    for (href in it.hrefs) {
                        Logger.log.fine("Principal is a read-only proxy for $href, checking for home sets")
                        root.resolve(href)?.let { proxyReadFor ->
                            related += proxyReadFor
                        }
                    }
                }
                dav[CalendarProxyWriteFor::class.java]?.let {
                    for (href in it.hrefs) {
                        Logger.log.fine("Principal is a read/write proxy for $href, checking for home sets")
                        root.resolve(href)?.let { proxyWriteFor ->
                            related += proxyWriteFor
                        }
                    }
                }

                // refresh home sets: direct group memberships
                dav[GroupMembership::class.java]?.let {
                    for (href in it.hrefs) {
                        Logger.log.fine("Principal is member of group $href, checking for home sets")
                        root.resolve(href)?.let { groupMembership ->
                            related += groupMembership
                        }
                    }
                }
            }

            val dav = DavResource(client, url)
            when (service.type) {
                Service.TYPE_CARDDAV ->
                    try {
                        dav.propfind(0, DisplayName.NAME, AddressbookHomeSet.NAME, GroupMembership.NAME) { response, _ ->
                            response[AddressbookHomeSet::class.java]?.let { homeSet ->
                                for (href in homeSet.hrefs)
                                    dav.location.resolve(href)?.let {
                                        val foundUrl = UrlUtils.withTrailingSlash(it)
                                        homeSets[foundUrl] = HomeSet(0, service.id, personal, foundUrl)
                                    }
                            }

                            if (personal)
                                findRelated(dav.location, response)
                        }
                    } catch (e: HttpException) {
                        if (e.code/100 == 4)
                            Logger.log.log(Level.INFO, "Ignoring Client Error 4xx while looking for addressbook home sets", e)
                        else
                            throw e
                    }
                Service.TYPE_CALDAV -> {
                    try {
                        dav.propfind(0, DisplayName.NAME, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME) { response, _ ->
                            response[CalendarHomeSet::class.java]?.let { homeSet ->
                                for (href in homeSet.hrefs)
                                    dav.location.resolve(href)?.let {
                                        val foundUrl = UrlUtils.withTrailingSlash(it)
                                        homeSets[foundUrl] = HomeSet(0, service.id, personal, foundUrl)
                                    }
                            }

                            if (personal)
                                findRelated(dav.location, response)
                        }
                    } catch (e: HttpException) {
                        if (e.code/100 == 4)
                            Logger.log.log(Level.INFO, "Ignoring Client Error 4xx while looking for calendar home sets", e)
                        else
                            throw e
                    }
                }
            }

            // query related homesets (those that do not belong to the current-user-principal)
            for (resource in related)
                queryHomeSets(client, resource, false)
        }

        fun saveHomesets() {
            // syncAll sets the ID of the new homeset to the ID of the old one when the URLs are matching
            DaoTools(homeSetDao).syncAll(
                    homeSetDao.getByService(serviceId),
                    homeSets,
                    { it.url })
        }

        fun saveCollections() {
            // syncAll sets the ID of the new collection to the ID of the old one when the URLs are matching
            DaoTools(collectionDao).syncAll(
                    collectionDao.getByService(serviceId),
                    collections, { it.url }) { new, old ->
                // use old settings of "force read only" and "sync", regardless of detection results
                new.forceReadOnly = old.forceReadOnly
                new.sync = old.sync
            }
        }

        try {
            Logger.log.info("Refreshing ${service.type} collections of service #$service")

            // cancel previous notification
            NotificationManagerCompat.from(this)
                    .cancel(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS)

            // create authenticating OkHttpClient (credentials taken from account settings)
            HttpClient.Builder(this, AccountSettings(this, account))
                    .setForeground(true)
                    .build().use { client ->
                val httpClient = client.okHttpClient

                // refresh home set list (from principal)
                service.principal?.let { principalUrl ->
                    Logger.log.fine("Querying principal $principalUrl for home sets")
                    queryHomeSets(httpClient, principalUrl)
                }

                // now refresh homesets and their member collections
                val itHomeSets = homeSets.iterator()
                while (itHomeSets.hasNext()) {
                    val (homeSetUrl, homeSet) = itHomeSets.next()
                    Logger.log.fine("Listing home set $homeSetUrl")

                    try {
                        DavResource(httpClient, homeSetUrl).propfind(1, *DAV_COLLECTION_PROPERTIES) { response, relation ->
                            if (!response.isSuccess())
                                return@propfind

                            if (relation == Response.HrefRelation.SELF) {
                                // this response is about the homeset itself
                                homeSet.displayName = response[DisplayName::class.java]?.displayName
                                homeSet.privBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind ?: true
                            }

                            // in any case, check whether the response is about a useable collection
                            val info = Collection.fromDavResponse(response) ?: return@propfind
                            info.serviceId = serviceId
                            info.refHomeSet = homeSet
                            info.confirmed = true

                            // whether new collections are selected for synchronization by default (controlled by managed setting)
                            info.sync = syncAllCollections

                            info.owner = response[Owner::class.java]?.href?.let { response.href.resolve(it) }
                            Logger.log.log(Level.FINE, "Found collection", info)

                            // remember usable collections
                            if ((service.type == Service.TYPE_CARDDAV && info.type == Collection.TYPE_ADDRESSBOOK) ||
                                    (service.type == Service.TYPE_CALDAV && arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(info.type)))
                                collections[response.href] = info
                        }
                    } catch(e: HttpException) {
                        if (e.code in arrayOf(403, 404, 410))
                            // delete home set only if it was not accessible (40x)
                            itHomeSets.remove()
                    }
                }

                // check/refresh unconfirmed collections
                val collectionsIter = collections.entries.iterator()
                while (collectionsIter.hasNext()) {
                    val currentCollection = collectionsIter.next()
                    val (url, info) = currentCollection
                    if (!info.confirmed)
                        try {
                            // this collection doesn't belong to a homeset anymore, otherwise it would have been confirmed
                            info.homeSetId = null

                            DavResource(httpClient, url).propfind(0, *DAV_COLLECTION_PROPERTIES) { response, _ ->
                                if (!response.isSuccess())
                                    return@propfind

                                val collection = Collection.fromDavResponse(response) ?: return@propfind
                                collection.serviceId = info.serviceId       // use same service ID as previous entry
                                collection.confirmed = true

                                // remove unusable collections
                                if ((service.type == Service.TYPE_CARDDAV && collection.type != Collection.TYPE_ADDRESSBOOK) ||
                                    (service.type == Service.TYPE_CALDAV && !arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(collection.type)) ||
                                    (collection.type == Collection.TYPE_WEBCAL && collection.source == null))
                                    collectionsIter.remove()
                                else
                                    // update this collection in list
                                    currentCollection.setValue(collection)
                            }
                        } catch(e: HttpException) {
                            if (e.code in arrayOf(403, 404, 410))
                                // delete collection only if it was not accessible (40x)
                                collectionsIter.remove()
                            else
                                throw e
                        }
                }
            }

            db.runInTransaction {
                saveHomesets()

                // use refHomeSet (if available) to determine homeset ID
                for (collection in collections.values)
                    collection.refHomeSet?.let { homeSet ->
                        collection.homeSetId = homeSet.id
                    }
                saveCollections()
            }

        } catch(e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Invalid account", e)
        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't refresh collection list", e)

            val debugIntent = DebugInfoActivity.IntentBuilder(this)
                .withCause(e)
                .withAccount(account)
                .build()
            val notify = NotificationUtils.newBuilder(this, NotificationUtils.CHANNEL_GENERAL)
                    .setSmallIcon(R.drawable.ic_sync_problem_notify)
                    .setContentTitle(getString(R.string.dav_service_refresh_failed))
                    .setContentText(getString(R.string.dav_service_refresh_couldnt_refresh))
                    .setContentIntent(PendingIntent.getActivity(this, 0, debugIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .setSubText(account.name)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build()
            NotificationManagerCompat.from(this)
                    .notify(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS, notify)
        } finally {
            runningRefresh.remove(serviceId)
            refreshingStatusListeners.mapNotNull { it.get() }.forEach {
                it.onDavRefreshStatusChanged(serviceId, false)
            }
        }

    }

}