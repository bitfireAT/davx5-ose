package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Calendars
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.AndroidCalendar
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration

@HiltWorker
class WebcalSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    val db: AppDatabase
): CoroutineWorker(appContext, workerParams) {

    companion object {

        const val NAME = "webcal-sync"

        fun updateWorker(context: Context, db: AppDatabase) {
            val workManager = WorkManager.getInstance(context)

            if (db.webcalSubscriptionDao().getCount() > 0) {
                Logger.log.info("There are Webcal subscriptions, updating sync worker")
                val syncInterval = Duration.ofDays(1)
                workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<WebcalSyncWorker>(syncInterval)
                        .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                        .build()
                )

            } else {
                Logger.log.info("No Webcal subscriptions, cancelling sync worker")
                workManager.cancelUniqueWork(NAME)
            }
        }

    }

    override suspend fun doWork(): Result {
        if (!PermissionUtils.havePermissions(applicationContext, PermissionUtils.CALENDAR_PERMISSIONS))
            return Result.failure()

        applicationContext.contentResolver.acquireContentProviderClient(Calendars.CONTENT_URI)?.use { providerClient ->
            updateLocalCalendars(providerClient)
        }

        return Result.success()
    }

    private suspend fun updateLocalCalendars(providerClient: ContentProviderClient) {
        val subscriptions = db.webcalSubscriptionDao().getAllAsync()
        for (subscription in subscriptions) {
            Logger.log.info("Subscription $subscription")

            // TODO extra account for Webcal subscriptions?
            val account: Account? = subscription.collectionId?.let { collectionId ->
                db.collectionDao().get(collectionId)?.serviceId?.let { serviceId ->
                    db.serviceDao().get(serviceId)?.let { service ->
                        Account(service.accountName, applicationContext.getString(R.string.account_type))
                    }
                }
            }
            if (account != null) {
                val calendar =
                    subscription.calendarId?.let { calendarId ->
                        AndroidCalendar.findByID(account, providerClient, LocalCalendar.Factory, calendarId)
                    } ?: run {
                        val uri = LocalCalendar.create(account, providerClient, subscription)
                        val id = ContentUris.parseId(uri)
                        subscription.calendarId = id
                        db.webcalSubscriptionDao().update(subscription)
                        AndroidCalendar.findByID(account, providerClient, LocalCalendar.Factory, id)
                    }

                Logger.log.info("Calendar $calendar")

            } else {
                Logger.log.warning("Standalone Webcal subscriptions not supported yet")
            }
        }
    }

}