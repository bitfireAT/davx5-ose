/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Binder
import android.support.v4.app.NotificationCompat
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.UrlUtils
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB.*
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.vcard4android.ContactsStorageException
import okhttp3.HttpUrl
import org.apache.commons.collections4.iterators.IteratorChain
import org.apache.commons.collections4.iterators.SingletonIterator
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Level
import kotlin.concurrent.thread

class DavService: Service() {

    companion object {
        @JvmField val ACTION_ACCOUNTS_UPDATED = "accountsUpdated"
        @JvmField val ACTION_REFRESH_COLLECTIONS = "refreshCollections"
        @JvmField val EXTRA_DAV_SERVICE_ID = "davServiceID"
    }

    private val runningRefresh = HashSet<Long>()
    private val refreshingStatusListeners = LinkedList<WeakReference<RefreshingStatusListener>>()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1)

            when (intent.action) {
                ACTION_ACCOUNTS_UPDATED ->
                    cleanupAccounts()
                ACTION_REFRESH_COLLECTIONS ->
                    if (runningRefresh.add(id)) {
                        thread { refreshCollections(id) }
                        refreshingStatusListeners.forEach { it.get()?.onDavRefreshStatusChanged(id, true) }
                    }
            }
        }

        return START_NOT_STICKY
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

        fun addRefreshingStatusListener(listener: RefreshingStatusListener, callImmediate: Boolean) {
            refreshingStatusListeners += WeakReference<RefreshingStatusListener>(listener)
            if (callImmediate)
                runningRefresh.forEach { id -> listener.onDavRefreshStatusChanged(id, true) }
        }

        fun removeRefreshingStatusListener(listener: RefreshingStatusListener) {
            val iter = refreshingStatusListeners.iterator()
            while (iter.hasNext()) {
                val item = iter.next().get()
                if (listener == item)
                    iter.remove()
            }
        }
    }

    override fun onBind(intent: Intent?) = binder



    /* ACTION RUNNABLES
       which actually do the work
     */

    @SuppressLint("MissingPermission")
    fun cleanupAccounts() {
        Logger.log.info("Cleaning up orphaned accounts")

        OpenHelper(this).use { dbHelper ->
            val db = dbHelper.writableDatabase

            val sqlAccountNames = LinkedList<String>()
            val accountNames = HashSet<String>()
            val accountManager = AccountManager.get(this)
            for (account in accountManager.getAccountsByType(getString(R.string.account_type))) {
                sqlAccountNames.add(DatabaseUtils.sqlEscapeString(account.name))
                accountNames += account.name
            }

            // delete orphaned address book accounts
            accountManager.getAccountsByType(getString(R.string.account_type_address_book))
                    .map { LocalAddressBook(this, it, null) }
                    .forEach {
                        try {
                            if (!accountNames.contains(it.getMainAccount().name))
                                it.delete()
                        } catch(e: ContactsStorageException) {
                            Logger.log.log(Level.SEVERE, "Couldn't get address book main account", e)
                        }
                    }

            // delete orphaned services in DB
            if (sqlAccountNames.isEmpty())
                db.delete(Services._TABLE, null, null)
            else
                db.delete(Services._TABLE, "${Services.ACCOUNT_NAME} NOT IN (${sqlAccountNames.joinToString(",")})", null)
        }
    }

    fun refreshCollections(service: Long) {
        OpenHelper(this@DavService).use { dbHelper ->
            val db = dbHelper.writableDatabase

            val serviceType by lazy {
                db.query(Services._TABLE, arrayOf(Services.SERVICE), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return@lazy cursor.getString(0)
                } ?: throw IllegalArgumentException("Service not found")
            }

            val account by lazy {
                db.query(Services._TABLE, arrayOf(Services.ACCOUNT_NAME), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        return@lazy Account(cursor.getString(0), getString(R.string.account_type))
                }
                throw IllegalArgumentException("Account not found")
            }

            val homeSets by lazy {
                val homeSets = mutableSetOf<HttpUrl>()
                db.query(HomeSets._TABLE, arrayOf(HomeSets.URL), "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext())
                        HttpUrl.parse(cursor.getString(0))?.let { homeSets += it }
                }
                homeSets
            }

            val collections by lazy {
                val collections = mutableMapOf<HttpUrl, CollectionInfo>()
                db.query(Collections._TABLE, null, "${Collections.SERVICE_ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues()
                        DatabaseUtils.cursorRowToContentValues(cursor, values)
                        values.getAsString(Collections.URL)?.let { url ->
                            HttpUrl.parse(url)?.let { collections.put(it, CollectionInfo(values)) }
                        }
                    }
                }
                collections
            }

            fun readPrincipal(): HttpUrl? {
                db.query(Services._TABLE, arrayOf(Services.PRINCIPAL), "${Services.ID}=?", arrayOf(service.toString()), null, null, null)?.use { cursor ->
                    if (cursor.moveToNext())
                        cursor.getString(0)?.let { return HttpUrl.parse(it) }
                }
                return null
            }

            /**
             * Checks if the given URL defines home sets and adds them to the home set list.
             * @param dav               DavResource to check
             */
            @Throws(IOException::class, HttpException::class, DavException::class)
            fun queryHomeSets(dav: DavResource) {
                when (serviceType) {
                    Services.SERVICE_CARDDAV -> {
                        dav.propfind(0, AddressbookHomeSet.NAME, GroupMembership.NAME)
                        for ((resource, addressbookHomeSet) in dav.findProperties(AddressbookHomeSet.NAME) as List<Pair<DavResource, AddressbookHomeSet>>)
                            for (href in addressbookHomeSet.hrefs)
                                resource.location.resolve(href)?.let { homeSets += UrlUtils.withTrailingSlash(it) }
                    }
                    Services.SERVICE_CALDAV -> {
                        dav.propfind(0, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME)
                        for ((resource, calendarHomeSet) in dav.findProperties(CalendarHomeSet.NAME) as List<Pair<DavResource, CalendarHomeSet>>)
                            for (href in calendarHomeSet.hrefs)
                                resource.location.resolve(href)?.let { homeSets.add(UrlUtils.withTrailingSlash(it)) }
                    }
                }
            }

            fun saveHomeSets() {
                db.delete(HomeSets._TABLE, "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()))
                for (homeSet in homeSets) {
                    val values = ContentValues(2)
                    values.put(HomeSets.SERVICE_ID, service)
                    values.put(HomeSets.URL, homeSet.toString())
                    db.insertOrThrow(HomeSets._TABLE, null, values)
                }
            }

            fun saveCollections() {
                db.delete(Collections._TABLE, "${HomeSets.SERVICE_ID}=?", arrayOf(service.toString()))
                for ((_,collection) in collections) {
                    val values = collection.toDB()
                    Logger.log.log(Level.FINE, "Saving collection", values)
                    values.put(Collections.SERVICE_ID, service)
                    db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }


            try {
                Logger.log.info("Refreshing $serviceType collections of service #$service")

                Settings.getInstance(this)?.use { settings ->
                    // create authenticating OkHttpClient (credentials taken from account settings)
                    HttpClient.Builder(this, settings, AccountSettings(this, settings, account))
                            .setForeground(true)
                            .build().use { client ->
                        val httpClient = client.okHttpClient

                        // refresh home set list (from principal)
                        readPrincipal()?.let { principalUrl ->
                            Logger.log.fine("Querying principal $principalUrl for home sets")
                            val principal = DavResource(httpClient, principalUrl)
                            queryHomeSets(principal)

                            // refresh home sets: calendar-proxy-read/write-for
                            for ((resource, proxyRead) in principal.findProperties(CalendarProxyReadFor.NAME) as List<Pair<DavResource, CalendarProxyReadFor>>)
                                for (href in proxyRead.hrefs) {
                                    Logger.log.fine("Principal is a read-only proxy for $href, checking for home sets")
                                    resource.location.resolve(href)?.let { queryHomeSets(DavResource(httpClient, it)) }
                                }
                            for ((resource, proxyWrite) in principal.findProperties(CalendarProxyWriteFor.NAME) as List<Pair<DavResource, CalendarProxyWriteFor>>)
                                for (href in proxyWrite.hrefs) {
                                    Logger.log.fine("Principal is a read/write proxy for $href, checking for home sets")
                                    resource.location.resolve(href)?.let { queryHomeSets(DavResource(httpClient, it)) }
                                }

                            // refresh home sets: direct group memberships
                            (principal.properties[GroupMembership.NAME] as GroupMembership?)?.let { groupMembership ->
                                for (href in groupMembership.hrefs) {
                                    Logger.log.fine("Principal is member of group $href, checking for home sets")
                                    principal.location.resolve(href)?.let { url ->
                                        try {
                                            queryHomeSets(DavResource(httpClient, url))
                                        } catch(e: HttpException) {
                                            Logger.log.log(Level.WARNING, "Couldn't query member group", e)
                                        } catch(e: DavException) {
                                            Logger.log.log(Level.WARNING, "Couldn't query member group", e)
                                        }
                                    }
                                }
                            }
                        }

                        // remember selected collections
                        val selectedCollections = HashSet<HttpUrl>()
                        collections.values
                                .filter { it.selected }
                                .forEach { (url,_) -> HttpUrl.parse(url)?.let { selectedCollections.add(it) } }

                        // now refresh collections (taken from home sets)
                        val itHomeSets = homeSets.iterator()
                        while (itHomeSets.hasNext()) {
                            val homeSetUrl = itHomeSets.next()
                            Logger.log.fine("Listing home set $homeSetUrl")

                            val homeSet = DavResource(httpClient, homeSetUrl)
                            try {
                                homeSet.propfind(1, *CollectionInfo.DAV_PROPERTIES)
                                val itCollections = IteratorChain<DavResource>(homeSet.members.iterator(), homeSet.related.iterator(), SingletonIterator(homeSet))
                                while (itCollections.hasNext()) {
                                    val member = itCollections.next()
                                    val info = CollectionInfo(member)
                                    info.confirmed = true
                                    Logger.log.log(Level.FINE, "Found collection", info)

                                    if ((serviceType == Services.SERVICE_CARDDAV && info.type == CollectionInfo.Type.ADDRESS_BOOK) ||
                                            (serviceType == Services.SERVICE_CALDAV && arrayOf(CollectionInfo.Type.CALENDAR, CollectionInfo.Type.WEBCAL).contains(info.type)))
                                        collections[member.location] = info
                                }
                            } catch(e: HttpException) {
                                if (e.status in arrayOf(403, 404, 410))
                                // delete home set only if it was not accessible (40x)
                                    itHomeSets.remove()
                            }
                        }

                        // check/refresh unconfirmed collections
                        val itCollections = collections.entries.iterator()
                        while (itCollections.hasNext()) {
                            val (url, info) = itCollections.next()
                            if (!info.confirmed)
                                try {
                                    val collection = DavResource(httpClient, url)
                                    collection.propfind(0, *CollectionInfo.DAV_PROPERTIES)
                                    val info = CollectionInfo(collection)
                                    info.confirmed = true

                                    // remove unusable collections
                                    if ((serviceType == Services.SERVICE_CARDDAV && info.type != CollectionInfo.Type.ADDRESS_BOOK) ||
                                            (serviceType == Services.SERVICE_CALDAV && !arrayOf(CollectionInfo.Type.CALENDAR, CollectionInfo.Type.WEBCAL).contains(info.type)) ||
                                            (info.type == CollectionInfo.Type.WEBCAL && info.source == null))
                                        itCollections.remove()
                                } catch(e: HttpException) {
                                    if (e.status in arrayOf(403, 404, 410))
                                    // delete collection only if it was not accessible (40x)
                                        itCollections.remove()
                                    else
                                        throw e
                                }
                        }

                        // restore selections
                        for (url in selectedCollections)
                            collections[url]?.let { it.selected = true }
                    }

                }

                db.beginTransactionNonExclusive()
                try {
                    saveHomeSets()
                    saveCollections()
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

            } catch(e: InvalidAccountException) {
                Logger.log.log(Level.SEVERE, "Invalid account", e)
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't refresh collection list", e)

                val debugIntent = Intent(this@DavService, DebugInfoActivity::class.java)
                debugIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e)
                debugIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account)

                val nm = NotificationUtils.createChannels(this)
                val notify = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_SYNC_PROBLEMS)
                        .setSmallIcon(R.drawable.ic_sync_error_notification)
                        .setLargeIcon(App.getLauncherBitmap(this))
                        .setContentTitle(getString(R.string.dav_service_refresh_failed))
                        .setContentText(getString(R.string.dav_service_refresh_couldnt_refresh))
                        .setContentIntent(PendingIntent.getActivity(this, 0, debugIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .build()
                nm.notify(Constants.NOTIFICATION_REFRESH_COLLECTIONS, notify)
            } finally {
                runningRefresh.remove(service)
                refreshingStatusListeners.forEach { it.get()?.onDavRefreshStatusChanged(service, false) }
            }
        }

    }

}
