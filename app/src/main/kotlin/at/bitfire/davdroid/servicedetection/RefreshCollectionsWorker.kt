/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.servicedetection

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.map
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.AddressbookDescription
import at.bitfire.dav4jvm.property.AddressbookHomeSet
import at.bitfire.dav4jvm.property.CalendarColor
import at.bitfire.dav4jvm.property.CalendarDescription
import at.bitfire.dav4jvm.property.CalendarHomeSet
import at.bitfire.dav4jvm.property.CalendarProxyReadFor
import at.bitfire.dav4jvm.property.CalendarProxyWriteFor
import at.bitfire.dav4jvm.property.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GroupMembership
import at.bitfire.dav4jvm.property.HrefListProperty
import at.bitfire.dav4jvm.property.Owner
import at.bitfire.dav4jvm.property.ResourceType
import at.bitfire.dav4jvm.property.Source
import at.bitfire.dav4jvm.property.SupportedAddressData
import at.bitfire.dav4jvm.property.SupportedCalendarComponentSet
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker.Companion.ARG_SERVICE_ID
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.account.SettingsActivity
import at.bitfire.davdroid.util.DavUtils.parent
import com.google.common.util.concurrent.ListenableFuture
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.logging.Level
import kotlin.collections.*

/**
 * Refreshes list of home sets and their respective collections of a service type (CardDAV or CalDAV).
 * Called from UI, when user wants to refresh all collections of a service ([at.bitfire.davdroid.ui.account.CollectionsFragment]).
 *
 * Input data:
 *
 *  - [ARG_SERVICE_ID]: service ID
 *
 * It queries all existing homesets and/or collections and then:
 *  - updates resources with found properties (overwrites without comparing)
 *  - adds resources if new ones are detected
 *  - removes resources if not found 40x (delete locally)
 *
 * @throws IllegalArgumentException when there's no service with the given service ID
 */
@HiltWorker
class RefreshCollectionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    var db: AppDatabase,
    var settings: SettingsManager
): Worker(appContext, workerParams) {

    companion object {

        const val ARG_SERVICE_ID = "serviceId"
        const val WORKER_TAG = "refreshCollectionsWorker"

        // Collection properties to ask for in a propfind request to the Cal- or CardDAV server
        val DAV_COLLECTION_PROPERTIES = arrayOf(
            ResourceType.NAME,
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            Owner.NAME,
            AddressbookDescription.NAME, SupportedAddressData.NAME,
            CalendarDescription.NAME, CalendarColor.NAME, SupportedCalendarComponentSet.NAME,
            Source.NAME
        )

        // Principal properties to ask the server
        val DAV_PRINCIPAL_PROPERTIES = arrayOf(
            DisplayName.NAME,
            ResourceType.NAME
        )

        /**
         * Uniquely identifies a refresh worker. Useful for stopping work, or querying its state.
         *
         * @param serviceId     what service (CalDAV/CardDAV) the worker is running for
         */
        fun workerName(serviceId: Long): String = "$WORKER_TAG-$serviceId"

        /**
         * Requests immediate refresh of a given service. If not running already. this will enqueue
         * a [RefreshCollectionsWorker].
         *
         * @param serviceId     serviceId which is to be refreshed
         * @return workerName   name of the worker started
         *
         * @throws IllegalArgumentException when there's no service with this ID
         */
        fun enqueue(context: Context, serviceId: Long): String {
            val name = workerName(serviceId)
            val arguments = Data.Builder()
                .putLong(ARG_SERVICE_ID, serviceId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<RefreshCollectionsWorker>()
                .addTag(name)
                .setInputData(arguments)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.KEEP,    // if refresh is already running, just continue that one
                workRequest
            )
            return name
        }

        /**
         * Will tell whether a refresh worker with given service id and state exists
         *
         * @param workerName    name of worker to find
         * @param workState     state of worker to match
         * @return boolean      true if worker with matching state was found
         */
        fun exists(context: Context, workerName: String, workState: WorkInfo.State = WorkInfo.State.RUNNING) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(workerName).map {
                workInfoList -> workInfoList.any { workInfo -> workInfo.state == workState }
            }

    }

    val serviceId: Long = inputData.getLong(ARG_SERVICE_ID, -1)
    val service = db.serviceDao().get(serviceId) ?: throw IllegalArgumentException("Service #$serviceId not found")
    val account = Account(service.accountName, applicationContext.getString(R.string.account_type))

    /** thread which runs the actual refresh code (can be interrupted to stop refreshing) */
    var refreshThread: Thread? = null

    override fun doWork(): Result {
        try {
            Logger.log.info("Refreshing ${service.type} collections of service #$service")

            // cancel previous notification
            NotificationManagerCompat.from(applicationContext)
                .cancel(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS)

            // create authenticating OkHttpClient (credentials taken from account settings)
            refreshThread = Thread.currentThread()
            HttpClient.Builder(applicationContext, AccountSettings(applicationContext, account))
                .setForeground(true)
                .build().use { client ->
                    val httpClient = client.okHttpClient
                    val refresher = Refresher(db, service, settings, httpClient)

                    // refresh home set list (from principal url)
                    service.principal?.let { principalUrl ->
                        Logger.log.fine("Querying principal $principalUrl for home sets")
                        refresher.discoverHomesets(principalUrl)
                    }

                    // refresh home sets and their member collections
                    refresher.refreshHomesetsAndTheirCollections()

                    // also refresh collections without a home set
                    refresher.refreshHomelessCollections()

                    // Lastly, refresh the principals (collection owners)
                    refresher.refreshPrincipals()
                }

        } catch(e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Invalid account", e)
            return Result.failure()
        } catch (e: UnauthorizedException) {
            Logger.log.log(Level.SEVERE, "Not authorized (anymore)", e)
            // notify that we need to re-authenticate in the account settings
            val settingsIntent = Intent(applicationContext, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_ACCOUNT, account)
            notifyRefreshError(
                applicationContext.getString(R.string.sync_error_authentication_failed),
                settingsIntent
            )
            return Result.failure()
        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't refresh collection list", e)

            val debugIntent = DebugInfoActivity.IntentBuilder(applicationContext)
                .withCause(e)
                .withAccount(account)
                .build()
            notifyRefreshError(
                applicationContext.getString(R.string.refresh_collections_worker_refresh_couldnt_refresh),
                debugIntent
            )
            return Result.failure()
        }



        // Success
        return Result.success()
    }

    override fun onStopped() {
        Logger.log.info("Stopping refresh (reason ${if (Build.VERSION.SDK_INT >= 31) stopReason else "n/a"})")
        refreshThread?.interrupt()
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> =
        CallbackToFutureAdapter.getFuture { completer ->
            val notification = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_foreground_notify)
                .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
                .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
                .setStyle(NotificationCompat.BigTextStyle())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            completer.set(ForegroundInfo(NotificationUtils.NOTIFY_SYNC_EXPEDITED, notification))
        }

    private fun notifyRefreshError(contentText: String, contentIntent: Intent) {
        val notify = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(applicationContext.getString(R.string.refresh_collections_worker_refresh_failed))
            .setContentText(contentText)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSubText(account.name)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notifyIfPossible(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS, notify)
    }

    /**
     * Contains the methods, which do the actual refreshing work. Collected here for testability
     */
    class Refresher(
        val db: AppDatabase,
        val service: Service,
        val settings: SettingsManager,
        val httpClient: OkHttpClient
    ) {

        val alreadyQueried = mutableSetOf<HttpUrl>()

        /**
         * Starting at current-user-principal URL, tries to recursively find and save all user relevant home sets.
         *
         *
         * @param principalUrl  URL of principal to query (user-provided principal or current-user-principal)
         * @param level         Current recursion level (limited to 0, 1 or 2):
         *
         * - 0: We assume found home sets belong to the current-user-principal
         * - 1 or 2: We assume found home sets don't directly belong to the current-user-principal
         *
         * @throws java.io.IOException
         * @throws HttpException
         * @throws at.bitfire.dav4jvm.exception.DavException
         */
        internal fun discoverHomesets(principalUrl: HttpUrl, level: Int = 0) {
            Logger.log.fine("Discovering homesets of $principalUrl")
            val relatedResources = mutableSetOf<HttpUrl>()

            // Define homeset class and properties to look for
            val homeSetClass: Class<out HrefListProperty>
            val properties: Array<Property.Name>
            when (service.type) {
                Service.TYPE_CARDDAV -> {
                    homeSetClass = AddressbookHomeSet::class.java
                    properties = arrayOf(DisplayName.NAME, AddressbookHomeSet.NAME, GroupMembership.NAME, ResourceType.NAME)
                }
                Service.TYPE_CALDAV -> {
                    homeSetClass = CalendarHomeSet::class.java
                    properties = arrayOf(DisplayName.NAME, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME, ResourceType.NAME)
                }
                else -> throw IllegalArgumentException()
            }

            // Query the URL
            val principal = DavResource(httpClient, principalUrl)
            val personal = level == 0
            try {
                principal.propfind(0, *properties) { davResponse, _ ->
                    alreadyQueried += davResponse.href

                    // If response holds home sets, save them
                    davResponse[homeSetClass]?.let { homeSets ->
                        for (homeSetHref in homeSets.hrefs)
                            principal.location.resolve(homeSetHref)?.let { homesetUrl ->
                                val resolvedHomeSetUrl = UrlUtils.withTrailingSlash(homesetUrl)
                                // Homeset is considered personal if this is the outer recursion call,
                                // This is because we assume the first call to query the current-user-principal
                                // Note: This is not be be confused with the DAV:owner attribute. Home sets can be owned by
                                // other principals and still be considered "personal" (belonging to the current-user-principal).
                                db.homeSetDao().insertOrUpdateByUrl(
                                    HomeSet(0, service.id, personal, resolvedHomeSetUrl)
                                )
                            }
                    }

                    // Add related principals to be queried afterwards
                    if (personal) {
                        val relatedResourcesTypes = listOf(
                            // current resource is a read/write-proxy for other principals
                            CalendarProxyReadFor::class.java,
                            CalendarProxyWriteFor::class.java,
                            // current resource is a member of a group (principal that can also have proxies)
                            GroupMembership::class.java)
                        for (type in relatedResourcesTypes)
                            davResponse[type]?.let {
                                for (href in it.hrefs)
                                    principal.location.resolve(href)?.let { url ->
                                        relatedResources += url
                                    }
                            }
                    }

                    // If current resource is a calendar-proxy-read/write, it's likely that its parent is a principal, too.
                    davResponse[ResourceType::class.java]?.let { resourceType ->
                        val proxyProperties = arrayOf(
                            ResourceType.CALENDAR_PROXY_READ,
                            ResourceType.CALENDAR_PROXY_WRITE,
                        )
                        if (proxyProperties.any { resourceType.types.contains(it) })
                            relatedResources += davResponse.href.parent()
                    }
                }
            } catch (e: HttpException) {
                if (e.code/100 == 4)
                    Logger.log.log(Level.INFO, "Ignoring Client Error 4xx while looking for ${service.type} home sets", e)
                else
                    throw e
            }

            // query related resources
            if (level <= 1)
                for (resource in relatedResources)
                    if (alreadyQueried.contains(resource))
                        Logger.log.warning("$resource already queried, skipping")
                    else
                        discoverHomesets(resource, level + 1)
        }

        /**
         * Refreshes homesets and their collections.
         *
         * Each stored homeset URL is queried (propfind) and it's collections ([MultiResponseCallback]) either saved, updated
         * or marked as homeless - in case a collection was removed from its homeset.
         *
         * If a homeset URL in fact points to a collection directly, the collection will be saved with this URL,
         * and a null value for it's homeset. Refreshing of collections without homesets is then handled by [refreshHomelessCollections].
         */
        internal fun refreshHomesetsAndTheirCollections() {
            val homesets = db.homeSetDao().getByService(service.id).associateBy { it.url }.toMutableMap()
            for((homeSetUrl, localHomeset) in homesets) {
                Logger.log.fine("Listing home set $homeSetUrl")

                // To find removed collections in this homeset: create a queue from existing collections and remove every collection that
                // is successfully rediscovered. If there are collections left, after processing is done, these are marked homeless.
                val localHomesetCollections = db.collectionDao()
                    .getByServiceAndHomeset(service.id, localHomeset.id)
                    .associateBy { it.url }
                    .toMutableMap()

                try {
                    DavResource(httpClient, homeSetUrl).propfind(1, *DAV_COLLECTION_PROPERTIES) { response, relation ->
                        // Note: This callback may be called multiple times ([MultiResponseCallback])
                        if (!response.isSuccess())
                            return@propfind

                        if (relation == Response.HrefRelation.SELF) {
                            // this response is about the homeset itself
                            localHomeset.displayName = response[DisplayName::class.java]?.displayName
                            localHomeset.privBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind ?: true
                            db.homeSetDao().insertOrUpdateByUrl(localHomeset)
                        }

                        // in any case, check whether the response is about a usable collection
                        val collection = Collection.fromDavResponse(response) ?: return@propfind

                        collection.serviceId = service.id
                        collection.homeSetId = localHomeset.id
                        collection.sync = shouldPreselect(collection, homesets.values)

                        // .. and save the principal url (collection owner)
                        response[Owner::class.java]?.href
                            ?.let { response.href.resolve(it) }
                            ?.let { principalUrl ->
                                val principal = Principal.fromServiceAndUrl(service, principalUrl)
                                val id = db.principalDao().insertOrUpdate(service.id, principal)
                                collection.ownerId = id
                            }

                        Logger.log.log(Level.FINE, "Found collection", collection)

                        // save or update collection if usable (ignore it otherwise)
                        if (isUsableCollection(collection))
                            db.collectionDao().insertOrUpdateByUrlAndRememberFlags(collection)

                        // Remove this collection from queue - because it was found in the home set
                        localHomesetCollections.remove(collection.url)
                    }
                } catch (e: HttpException) {
                    // delete home set locally if it was not accessible (40x)
                    if (e.code in arrayOf(403, 404, 410))
                        db.homeSetDao().delete(localHomeset)
                }

                // Mark leftover (not rediscovered) collections from queue as homeless (remove association)
                for ((_, homelessCollection) in localHomesetCollections) {
                    homelessCollection.homeSetId = null
                    db.collectionDao().insertOrUpdateByUrlAndRememberFlags(homelessCollection)
                }

            }
        }

        /**
         * Refreshes collections which don't have a homeset.
         *
         * It queries each stored collection with a homeSetId of "null" and either updates or deletes (if inaccessible or unusable) them.
         */
        internal fun refreshHomelessCollections() {
            val homelessCollections = db.collectionDao().getByServiceAndHomeset(service.id, null).associateBy { it.url }.toMutableMap()
            for((url, localCollection) in homelessCollections) try {
                DavResource(httpClient, url).propfind(0, *DAV_COLLECTION_PROPERTIES) { response, _ ->
                    if (!response.isSuccess()) {
                        db.collectionDao().delete(localCollection)
                        return@propfind
                    }

                    // Save or update the collection, if usable, otherwise delete it
                    Collection.fromDavResponse(response)?.let { collection ->
                        if (!isUsableCollection(collection))
                            return@let
                        collection.serviceId = localCollection.serviceId       // use same service ID as previous entry

                        // .. and save the principal url (collection owner)
                        response[Owner::class.java]?.href
                            ?.let { response.href.resolve(it) }
                            ?.let { principalUrl ->
                                val principal = Principal.fromServiceAndUrl(service, principalUrl)
                                val principalId = db.principalDao().insertOrUpdate(service.id, principal)
                                collection.ownerId = principalId
                            }

                        db.collectionDao().insertOrUpdateByUrlAndRememberFlags(collection)
                    } ?: db.collectionDao().delete(localCollection)
                }
            } catch (e: HttpException) {
                // delete collection locally if it was not accessible (40x)
                if (e.code in arrayOf(403, 404, 410))
                    db.collectionDao().delete(localCollection)
                else
                    throw e
            }

        }

        /**
         * Refreshes the principals (get their current display names).
         * Also removes principals which do not own any collections anymore.
         */
        internal fun refreshPrincipals() {
            // Refresh principals (collection owner urls)
            val principals = db.principalDao().getByService(service.id)
            for (oldPrincipal in principals) {
                val principalUrl = oldPrincipal.url
                Logger.log.fine("Querying principal $principalUrl")
                try {
                    DavResource(httpClient, principalUrl).propfind(0, *DAV_PRINCIPAL_PROPERTIES) { response, _ ->
                        if (!response.isSuccess())
                            return@propfind
                        Principal.fromDavResponse(service.id, response)?.let { principal ->
                            Logger.log.fine("Got principal: $principal")
                            db.principalDao().insertOrUpdate(service.id, principal)
                        }
                    }
                } catch (e: HttpException) {
                    Logger.log.info("Principal update failed with response code ${e.code}. principalUrl=$principalUrl")
                }
            }

            // Delete principals which don't own any collections
            db.principalDao().getAllWithoutCollections().forEach {principal ->
                db.principalDao().delete(principal)
            }
        }

        /**
         * Finds out whether given collection is usable, by checking that either
         *  - CalDAV/CardDAV: service and collection type match, or
         *  - WebCal: subscription source URL is not empty
         */
        private fun isUsableCollection(collection: Collection) =
            (service.type == Service.TYPE_CARDDAV && collection.type == Collection.TYPE_ADDRESSBOOK) ||
                    (service.type == Service.TYPE_CALDAV && arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(collection.type)) ||
                    (collection.type == Collection.TYPE_WEBCAL && collection.source != null)

        /**
         * Whether to preselect the given collection for synchronisation, according to the
         * settings [Settings.PRESELECT_COLLECTIONS] (see there for allowed values) and
         * [Settings.PRESELECT_COLLECTIONS_EXCLUDED].
         *
         * A collection is considered _personal_ if it is found in one of the current-user-principal's home-sets.
         *
         * Before a collection is pre-selected, we check whether its URL matches the regexp in
         * [Settings.PRESELECT_COLLECTIONS_EXCLUDED], in which case *false* is returned.
         *
         * @param collection the collection to check
         * @param homesets list of home-sets (to check whether collection is in a personal home-set)
         * @return *true* if the collection should be preselected for synchronization; *false* otherwise
         */
        internal fun shouldPreselect(collection: Collection, homesets: Iterable<HomeSet>): Boolean {
            val shouldPreselect = settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS)

            val excluded by lazy {
                val excludedRegex = settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED)
                if (!excludedRegex.isNullOrEmpty())
                    Regex(excludedRegex).containsMatchIn(collection.url.toString())
                else
                    false
            }

            return when (shouldPreselect) {
                Settings.PRESELECT_COLLECTIONS_ALL ->
                    // preselect if collection url is not excluded
                    !excluded

                Settings.PRESELECT_COLLECTIONS_PERSONAL ->
                    // preselect if is personal (in a personal home-set), but not excluded
                    homesets
                        .filter { homeset -> homeset.personal }
                        .map { homeset -> homeset.id }
                        .contains(collection.homeSetId)
                        && !excluded

                else -> // don't preselect
                    false
            }
        }
    }

}
