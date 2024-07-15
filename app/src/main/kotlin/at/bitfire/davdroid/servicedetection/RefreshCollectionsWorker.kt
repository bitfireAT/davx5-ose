/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.Source
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.push.PushTransports
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.Owner
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker.Companion.ARG_SERVICE_ID
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.account.AccountSettingsActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import java.util.logging.Level

/**
 * Refreshes list of home sets and their respective collections of a service type (CardDAV or CalDAV).
 * Called from UI, when user wants to refresh all collections of a service.
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
 * Expedited: yes (always initiated by user)
 *
 * Long-running: no
 *
 * @throws IllegalArgumentException when there's no service with the given service ID
 */
@HiltWorker
class RefreshCollectionsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val accountSettingsFactory: AccountSettings.Factory,
    serviceRepository: DavServiceRepository,
    private val collectionListRefresherFactory: CollectionListRefresher.Factory
): CoroutineWorker(appContext, workerParams) {

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
            Source.NAME,
            // WebDAV Push
            PushTransports.NAME,
            Topic.NAME
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
         * @return Pair with
         *
         * 1. worker name,
         * 2. operation of [WorkManager.enqueueUniqueWork] (can be used to wait for completion)
         *
         * @throws IllegalArgumentException when there's no service with this ID
         */
        fun enqueue(context: Context, serviceId: Long): Pair<String, Operation> {
            val name = workerName(serviceId)
            val arguments = Data.Builder()
                .putLong(ARG_SERVICE_ID, serviceId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<RefreshCollectionsWorker>()
                .addTag(name)
                .setInputData(arguments)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            return Pair(
                name,
                WorkManager.getInstance(context).enqueueUniqueWork(
                    name,
                    ExistingWorkPolicy.KEEP,    // if refresh is already running, just continue that one
                    workRequest
                )
            )
        }

        /**
         * Observes whether a refresh worker with given service id and state exists.
         *
         * @param workerName    name of worker to find
         * @param workState     state of worker to match
         *
         * @return flow that emits `true` if worker with matching state was found (otherwise `false`)
         */
        fun existsFlow(context: Context, workerName: String, workState: WorkInfo.State = WorkInfo.State.RUNNING) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workerName).map { workInfoList ->
                workInfoList.any { workInfo -> workInfo.state == workState }
            }

    }

    val serviceId: Long = inputData.getLong(ARG_SERVICE_ID, -1)
    val service = serviceRepository.get(serviceId)
    val account = service?.let { service ->
        Account(service.accountName, applicationContext.getString(R.string.account_type))
    }

    override suspend fun doWork(): Result {
        if (service == null || account == null) {
            Logger.log.warning("Missing service or account with service ID: $serviceId")
            return Result.failure()
        }

        try {
            Logger.log.info("Refreshing ${service.type} collections of service #$service")

            // cancel previous notification
            NotificationManagerCompat.from(applicationContext)
                .cancel(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS)

            // create authenticating OkHttpClient (credentials taken from account settings)
            runInterruptible {
                HttpClient.Builder(applicationContext, accountSettingsFactory.forAccount(account))
                    .setForeground(true)
                    .build().use { client ->
                        val httpClient = client.okHttpClient
                        val refresher = collectionListRefresherFactory.create(service, httpClient)

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
            }

        } catch(e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Invalid account", e)
            return Result.failure()
        } catch (e: UnauthorizedException) {
            Logger.log.log(Level.SEVERE, "Not authorized (anymore)", e)
            // notify that we need to re-authenticate in the account settings
            val settingsIntent = Intent(applicationContext, AccountSettingsActivity::class.java)
                .putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
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

    /**
     * Used by WorkManager to show a foreground service notification for expedited jobs on Android <12.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_foreground_notify)
            .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
            .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
            .setStyle(NotificationCompat.BigTextStyle())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NotificationUtils.NOTIFY_SYNC_EXPEDITED, notification)
    }

    private fun notifyRefreshError(contentText: String, contentIntent: Intent) {
        val notify = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_sync_problem_notify)
            .setContentTitle(applicationContext.getString(R.string.refresh_collections_worker_refresh_failed))
            .setContentText(contentText)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setSubText(account?.name)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notifyIfPossible(serviceId.toString(), NotificationUtils.NOTIFY_REFRESH_COLLECTIONS, notify)
    }

}