/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.sync.SyncUtils
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.util.setAndVerifyUserData
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.UnknownProperty
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.property.Url
import org.dmfs.tasks.contract.TaskContract
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.logging.Level
import java.util.logging.Logger

class AccountSettingsMigrations @AssistedInject constructor(
    @Assisted val account: Account,
    @Assisted val accountSettings: AccountSettings,
    @ApplicationContext val context: Context,
    private val db: AppDatabase,
    private val logger: Logger,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, accountSettings: AccountSettings): AccountSettingsMigrations
    }


    val accountManager: AccountManager = AccountManager.get(context)


    /**
     * Between DAVx5 4.4.1-beta.1 and 4.4.1-rc.1 (both v15), the periodic sync workers were renamed (moved to another
     * package) and thus automatic synchronization stopped (because the enqueued workers rely on the full class
     * name and no new workers were enqueued). Here we enqueue all periodic sync workers again with the correct class name.
     */
    @Suppress("unused","FunctionName")
    fun update_15_16() {
        for (authority in SyncUtils.syncAuthorities(context)) {
            logger.info("Re-enqueuing periodic sync workers for $account/$authority, if necessary")

            /* A maybe existing periodic worker references the old class name (even if it failed and/or is not active). So
            we need to explicitly disable and prune all workers. Just updating the worker is not enough – WorkManager will update
            the work details, but not the class name. */
            val disableOp = syncWorkerManager.disablePeriodic(account, authority)
            disableOp.result.get()  // block until worker with old name is disabled

            val pruneOp = WorkManager.getInstance(context).pruneWork()
            pruneOp.result.get()    // block until worker with old name is removed from DB

            val interval = accountSettings.getSyncInterval(authority)
            if (interval != null && interval != AccountSettings.SYNC_INTERVAL_MANUALLY) {
                // There's a sync interval for this account/authority; a periodic sync worker should be there, too.
                val onlyWifi = accountSettings.getSyncWifiOnly()
                syncWorkerManager.enablePeriodic(account, authority, interval, onlyWifi)
            }
        }
    }


    /**
     * Updates the periodic sync workers by re-setting the same sync interval.
     *
     * The goal is to add the [BaseSyncWorker.commonTag] to all existing periodic sync workers so that they can be detected by
     * the new [BaseSyncWorker.exists] and [at.bitfire.davdroid.ui.AccountsActivity.Model].
     */
    @Suppress("unused","FunctionName")
    fun update_14_15() {
        for (authority in SyncUtils.syncAuthorities(context)) {
            val interval = accountSettings.getSyncInterval(authority)
            accountSettings.setSyncInterval(authority, interval ?: AccountSettings.SYNC_INTERVAL_MANUALLY)
        }
    }

    /**
     * Disables all sync adapter periodic syncs for every authority. Then enables
     * corresponding PeriodicSyncWorkers
     */
    @Suppress("unused","FunctionName")
    fun update_13_14() {
        // Cancel any potentially running syncs for this account (sync framework)
        ContentResolver.cancelSync(account, null)

        val authorities = listOf(
            context.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY,
            TaskProvider.ProviderName.JtxBoard.authority,
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority
        )

        for (authority in authorities) {
            // Enable PeriodicSyncWorker (WorkManager), with known intervals
            v14_enableWorkManager(authority)
            // Disable periodic syncs (sync adapter framework)
            v14_disableSyncFramework(authority)
        }
    }
    private fun v14_enableWorkManager(authority: String) {
        val enabled = accountSettings.getSyncInterval(authority)?.let { syncInterval ->
            accountSettings.setSyncInterval(authority, syncInterval)
        } ?: false
        logger.info("PeriodicSyncWorker for $account/$authority enabled=$enabled")
    }
    private fun v14_disableSyncFramework(authority: String) {
        // Disable periodic syncs (sync adapter framework)
        val disable: () -> Boolean = {
            /* Ugly hack: because there is no callback for when the sync status/interval has been
            updated, we need to make this call blocking. */
            for (sync in ContentResolver.getPeriodicSyncs(account, authority))
                ContentResolver.removePeriodicSync(sync.account, sync.authority, sync.extras)

            // check whether syncs are really disabled
            var result = true
            for (sync in ContentResolver.getPeriodicSyncs(account, authority)) {
                logger.info("Sync framework still has a periodic sync for $account/$authority: $sync")
                result = false
            }
            result
        }
        // try up to 10 times with 100 ms pause
        var success = false
        for (idxTry in 0 until 10) {
            success = disable()
            if (success)
                break
            Thread.sleep(200)
        }
        logger.info("Sync framework periodic syncs for $account/$authority disabled=$success")
    }

    @Suppress("unused","FunctionName")
    /**
     * Not a per-account migration, but not a database migration, too, so it fits best there.
     * Best future solution would be that SettingsManager manages versions and migrations.
     *
     * Updates proxy settings from override_proxy_* to proxy_type, proxy_host, proxy_port.
     */
    private fun update_12_13() {
        // proxy settings are managed by SharedPreferencesProvider
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // old setting names
        val overrideProxy = "override_proxy"
        val overrideProxyHost = "override_proxy_host"
        val overrideProxyPort = "override_proxy_port"

        val edit = preferences.edit()
        if (preferences.contains(overrideProxy)) {
            if (preferences.getBoolean(overrideProxy, false))
            // override_proxy set, migrate to proxy_type = HTTP
                edit.putInt(Settings.PROXY_TYPE, Settings.PROXY_TYPE_HTTP)
            edit.remove(overrideProxy)
        }
        if (preferences.contains(overrideProxyHost)) {
            preferences.getString(overrideProxyHost, null)?.let { host ->
                edit.putString(Settings.PROXY_HOST, host)
            }
            edit.remove(overrideProxyHost)
        }
        if (preferences.contains(overrideProxyPort)) {
            val port = preferences.getInt(overrideProxyPort, 0)
            if (port != 0)
                edit.putInt(Settings.PROXY_PORT, port)
            edit.remove(overrideProxyPort)
        }
        edit.apply()
    }


    @Suppress("unused","FunctionName")
    /**
     * Store event URLs as URL (extended property) instead of unknown property. At the same time,
     * convert legacy unknown properties to the current format.
     */
    private fun update_11_12() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { provider ->
                // Attention: CalendarProvider does NOT limit the results of the ExtendedProperties query
                // to the given account! So all extended properties will be processed number-of-accounts times.
                val extUri = CalendarContract.ExtendedProperties.CONTENT_URI.asSyncAdapter(account)

                provider.query(
                    extUri, arrayOf(
                        CalendarContract.ExtendedProperties._ID,     // idx 0
                        CalendarContract.ExtendedProperties.NAME,    // idx 1
                        CalendarContract.ExtendedProperties.VALUE    // idx 2
                    ), null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val rawValue = cursor.getString(2)

                        val uri by lazy {
                            ContentUris.withAppendedId(CalendarContract.ExtendedProperties.CONTENT_URI, id).asSyncAdapter(account)
                        }

                        when (cursor.getString(1)) {
                            UnknownProperty.CONTENT_ITEM_TYPE -> {
                                // unknown property; check whether it's a URL
                                try {
                                    val property = UnknownProperty.fromJsonString(rawValue)
                                    if (property is Url) {  // rewrite to MIMETYPE_URL
                                        val newValues = ContentValues(2)
                                        newValues.put(CalendarContract.ExtendedProperties.NAME, AndroidEvent.EXTNAME_URL)
                                        newValues.put(CalendarContract.ExtendedProperties.VALUE, property.value)
                                        provider.update(uri, newValues, null, null)
                                    }
                                } catch (e: Exception) {
                                    logger.log(
                                        Level.WARNING,
                                        "Couldn't rewrite URL from unknown property to ${AndroidEvent.EXTNAME_URL}",
                                        e
                                    )
                                }
                            }

                            "unknown-property" -> {
                                // unknown property (deprecated format); convert to current format
                                try {
                                    val stream = ByteArrayInputStream(Base64.decode(rawValue, Base64.NO_WRAP))
                                    ObjectInputStream(stream).use {
                                        (it.readObject() as? Property)?.let { property ->
                                            // rewrite to current format
                                            val newValues = ContentValues(2)
                                            newValues.put(CalendarContract.ExtendedProperties.NAME, UnknownProperty.CONTENT_ITEM_TYPE)
                                            newValues.put(CalendarContract.ExtendedProperties.VALUE, UnknownProperty.toJsonString(property))
                                            provider.update(uri, newValues, null, null)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.log(Level.WARNING, "Couldn't rewrite deprecated unknown property to current format", e)
                                }
                            }

                            "unknown-property.v2" -> {
                                // unknown property (deprecated MIME type); rewrite to current MIME type
                                val newValues = ContentValues(1)
                                newValues.put(CalendarContract.ExtendedProperties.NAME, UnknownProperty.CONTENT_ITEM_TYPE)
                                provider.update(uri, newValues, null, null)
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("unused","FunctionName")
    /**
     * The tasks sync interval should be stored in account settings. It's used to set the sync interval
     * again when the tasks provider is switched.
     */
    private fun update_10_11() {
        tasksAppManager.get().currentProvider()?.let { provider ->
            val interval = accountSettings.getSyncInterval(provider.authority)
            if (interval != null)
                accountManager.setAndVerifyUserData(account,
                    AccountSettings.KEY_SYNC_INTERVAL_TASKS, interval.toString())
        }
    }

    @Suppress("unused","FunctionName")
    /**
     * Task synchronization now handles alarms, categories, relations and unknown properties.
     * Setting task ETags to null will cause them to be downloaded (and parsed) again.
     *
     * Also update the allowed reminder types for calendars.
     **/
    private fun update_9_10() {
        TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.use { provider ->
            val tasksUri = provider.tasksUri().asSyncAdapter(account)
            val emptyETag = ContentValues(1)
            emptyETag.putNull(LocalTask.COLUMN_ETAG)
            provider.client.update(tasksUri, emptyETag, "${TaskContract.Tasks._DIRTY}=0 AND ${TaskContract.Tasks._DELETED}=0", null)
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { provider ->
                provider.update(
                    CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(account),
                    AndroidCalendar.calendarBaseValues, null, null)
            }
    }

    @Suppress("unused","FunctionName")
    /**
     * It seems that somehow some non-CalDAV accounts got OpenTasks syncable, which caused battery problems.
     * Disable it on those accounts for the future.
     */
    private fun update_8_9() {
        val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
        if (!hasCalDAV && ContentResolver.getIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority) != 0) {
            logger.info("Disabling OpenTasks sync for $account")
            ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0)
        }
    }

    @Suppress("unused","FunctionName")
    @SuppressLint("Recycle")
    /**
     * There is a mistake in this method. [TaskContract.Tasks.SYNC_VERSION] is used to store the
     * SEQUENCE and should not be used for the eTag.
     */
    private fun update_7_8() {
        TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.use { provider ->
            // ETag is now in sync_version instead of sync1
            // UID  is now in _uid         instead of sync2
            provider.client.query(provider.tasksUri().asSyncAdapter(account),
                arrayOf(TaskContract.Tasks._ID, TaskContract.Tasks.SYNC1, TaskContract.Tasks.SYNC2),
                "${TaskContract.Tasks.ACCOUNT_TYPE}=? AND ${TaskContract.Tasks.ACCOUNT_NAME}=?",
                arrayOf(account.type, account.name), null)!!.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val eTag = cursor.getString(1)
                    val uid = cursor.getString(2)
                    val values = ContentValues(4)
                    values.put(TaskContract.Tasks._UID, uid)
                    values.put(TaskContract.Tasks.SYNC_VERSION, eTag)
                    values.putNull(TaskContract.Tasks.SYNC1)
                    values.putNull(TaskContract.Tasks.SYNC2)
                    logger.log(Level.FINER, "Updating task $id", values)
                    provider.client.update(
                        ContentUris.withAppendedId(provider.tasksUri(), id).asSyncAdapter(account),
                        values, null, null)
                }
            }
        }
    }

    @Suppress("unused")
    @SuppressLint("Recycle")
    private fun update_6_7() {
        // add calendar colors
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.use { provider ->
            AndroidCalendar.insertColors(provider, account)
        }

        // update allowed WiFi settings key
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setAndVerifyUserData(account, AccountSettings.KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setAndVerifyUserData(account, "wifi_only_ssid", null)
    }

    // updates from AccountSettings version 5 and below are not supported anymore

}