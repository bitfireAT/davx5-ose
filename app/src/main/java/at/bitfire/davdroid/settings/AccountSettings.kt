/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.ExtendedProperties
import android.provider.ContactsContract
import android.util.Base64
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.closeCompat
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName.OpenTasks
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.property.Url
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.StringUtils
import org.dmfs.tasks.contract.TaskContract
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.logging.Level

/**
 * Manages settings of an account.
 *
 * @param context       Required to access account settings
 * @param argAccount    Account to take settings from. If this account is an address book account,
 * settings will be taken from the corresponding main account instead.
 *
 * @throws InvalidAccountException on construction when the account doesn't exist (anymore)
 * @throws IllegalArgumentException when the account type is not _DAVx5_ or _DAVx5 address book_
 */
@Suppress("FunctionName")
class AccountSettings(
    val context: Context,
    argAccount: Account
) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountSettingsEntryPoint {
        fun appDatabase(): AppDatabase
        fun settingsManager(): SettingsManager
    }

    companion object {

        const val CURRENT_VERSION = 13
        const val KEY_SETTINGS_VERSION = "version"

        const val KEY_SYNC_INTERVAL_ADDRESSBOOKS = "sync_interval_addressbooks"
        const val KEY_SYNC_INTERVAL_CALENDARS = "sync_interval_calendars"

        /** Stores the tasks sync interval (in seconds) so that it can be set again when the provider is switched */
        const val KEY_SYNC_INTERVAL_TASKS = "sync_interval_tasks"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs

        /** Time range limitation to the past [in days]. Values:
         *
         * - null: default value (DEFAULT_TIME_RANGE_PAST_DAYS)
         * - <0 (typically -1): no limit
         * - n>0: entries more than n days in the past won't be synchronized
         */
        const val KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days"
        const val DEFAULT_TIME_RANGE_PAST_DAYS = 90

        /**
         * Whether a default alarm shall be assigned to received events/tasks which don't have an alarm.
         * Value can be null (no default alarm) or an integer (default alarm shall be created this
         * number of minutes before the event/task).
         */
        const val KEY_DEFAULT_ALARM = "default_alarm"

        /** Whether DAVx5 sets the local calendar color to the value from service DB at every sync
         value = *null* (not existing): true (default);
                 "0"                    false */
        const val KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors"

        /** Whether DAVx5 populates and uses CalendarContract.Colors
         value = *null* (not existing)   false (default);
                 "1"                     true */
        const val KEY_EVENT_COLORS = "event_colors"

        /** Contact group method:
         *null (not existing)*     groups as separate vCards (default);
         "CATEGORIES"              groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        /** UI preference: Show only personal collections
         value = *null* (not existing)   show all collections (default);
         "1"                             show only personal collections */
        const val KEY_SHOW_ONLY_PERSONAL = "show_only_personal"

        const val SYNC_INTERVAL_MANUALLY = -1L


        fun initialUserData(credentials: Credentials?): Bundle {
            val bundle = Bundle(2)
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            if (credentials != null) {
                if (credentials.userName != null)
                    bundle.putString(KEY_USERNAME, credentials.userName)
                if (credentials.certificateAlias != null)
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)
            }

            return bundle
        }

        fun repairSyncIntervals(context: Context) {
            val addressBooksAuthority = context.getString(R.string.address_books_authority)
            val taskAuthority = TaskUtils.currentProvider(context)?.authority

            val am = AccountManager.get(context)
            for (account in am.getAccountsByType(context.getString(R.string.account_type)))
                try {
                    val settings = AccountSettings(context, account)

                    // repair address book sync
                    settings.getSavedAddressbooksSyncInterval()?.let { shouldBe ->
                        val current = settings.getSyncInterval(addressBooksAuthority)
                        if (current != shouldBe) {
                            Logger.log.warning("${account.name}: $addressBooksAuthority sync interval should be $shouldBe but is $current -> setting to $current")
                            settings.setSyncInterval(addressBooksAuthority, shouldBe)
                        }
                    }

                    // repair calendar sync
                    settings.getSavedCalendarsSyncInterval()?.let { shouldBe ->
                        val current = settings.getSyncInterval(CalendarContract.AUTHORITY)
                        if (current != shouldBe) {
                            Logger.log.warning("${account.name}: ${CalendarContract.AUTHORITY} sync interval should be $shouldBe but is $current -> setting to $current")
                            settings.setSyncInterval(CalendarContract.AUTHORITY, shouldBe)
                        }
                    }

                    if (taskAuthority != null)
                    // repair calendar sync
                        settings.getSavedTasksSyncInterval()?.let { shouldBe ->
                            val current = settings.getSyncInterval(taskAuthority)
                            if (current != shouldBe) {
                                Logger.log.warning("${account.name}: $taskAuthority sync interval should be $shouldBe but is $current -> setting to $current")
                                settings.setSyncInterval(taskAuthority, shouldBe)
                            }
                        }
                } catch (ignored: InvalidAccountException) {
                    // account doesn't exist (anymore)
                }
        }

    }


    val db = EntryPointAccessors.fromApplication(context, AccountSettingsEntryPoint::class.java).appDatabase()
    val settings = EntryPointAccessors.fromApplication(context, AccountSettingsEntryPoint::class.java).settingsManager()

    val accountManager: AccountManager = AccountManager.get(context)
    val account: Account

    init {
        when (argAccount.type) {
            context.getString(R.string.account_type_address_book) -> {
                /* argAccount is an address book account, which is not a main account. However settings are
                   stored in the main account, so resolve and use the main account instead. */
                account = LocalAddressBook.mainAccount(context, argAccount)
            }
            context.getString(R.string.account_type) ->
                account = argAccount
            else ->
                throw IllegalArgumentException("Account type not supported")
        }

        // synchronize because account migration must only be run one time
        synchronized(AccountSettings::class.java) {
            val versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION) ?: throw InvalidAccountException(account)
            var version = 0
            try {
                version = Integer.parseInt(versionStr)
            } catch (e: NumberFormatException) {
            }
            Logger.log.fine("Account ${account.name} has version $version, current version: $CURRENT_VERSION")

            if (version < CURRENT_VERSION)
                update(version)
        }
    }


    // authentication settings

    fun credentials() = Credentials(
            accountManager.getUserData(account, KEY_USERNAME),
            accountManager.getPassword(account),
            accountManager.getUserData(account, KEY_CERTIFICATE_ALIAS)
    )

    fun credentials(credentials: Credentials) {
        accountManager.setUserData(account, KEY_USERNAME, credentials.userName)
        accountManager.setPassword(account, credentials.password)
        accountManager.setUserData(account, KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)
    }


    // sync. settings

    fun getSyncInterval(authority: String): Long? {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null

        return if (ContentResolver.getSyncAutomatically(account, authority))
            ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period ?: SYNC_INTERVAL_MANUALLY
        else
            SYNC_INTERVAL_MANUALLY
    }

    /**
     * Sets the sync interval and enables/disables automatic sync for the given account and authority.
     * Does *not* call [ContentResolver.setIsSyncable].
     *
     * This method blocks until the settings have arrived in the sync framework, so it should not
     * be called from the UI thread.
     *
     * @param authority sync authority (like [CalendarContract.AUTHORITY])
     * @param seconds if [SYNC_INTERVAL_MANUALLY]: automatic sync will be disabled;
     * otherwise: automatic sync will be enabled and set to the given number of seconds
     *
     * @return whether the sync interval was successfully set
     */
    @WorkerThread
    fun setSyncInterval(authority: String, seconds: Long): Boolean {
        /* Ugly hack: because there is no callback for when the sync status/interval has been
        updated, we need to make this call blocking. */
        val setInterval: () -> Boolean =
                if (seconds == SYNC_INTERVAL_MANUALLY) {
                    {
                        Logger.log.fine("Disabling automatic sync of $account/$authority")
                        ContentResolver.setSyncAutomatically(account, authority, false)

                        /* return */ !ContentResolver.getSyncAutomatically(account, authority)
                    }
                } else {
                    {
                        Logger.log.fine("Setting automatic sync of $account/$authority to $seconds seconds")
                        ContentResolver.setSyncAutomatically(account, authority, true)
                        ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)

                        /* return */ ContentResolver.getSyncAutomatically(account, authority) &&
                                ContentResolver.getPeriodicSyncs(account, authority).firstOrNull()?.period == seconds
                    }
                }

        // try up to 10 times with 100 ms pause
        var success = false
        for (idxTry in 0 until 10) {
            success = setInterval()
            if (success)
                break
            Thread.sleep(100)
        }

        if (!success)
            return false

        // store sync interval in account settings (used when the provider is switched)
        when {
            authority == context.getString(R.string.address_books_authority) ->
                accountManager.setUserData(account, KEY_SYNC_INTERVAL_ADDRESSBOOKS, seconds.toString())

            authority == CalendarContract.AUTHORITY ->
                accountManager.setUserData(account, KEY_SYNC_INTERVAL_CALENDARS, seconds.toString())

            TaskProvider.ProviderName.values().any { it.authority == authority } ->
                accountManager.setUserData(account, KEY_SYNC_INTERVAL_TASKS, seconds.toString())
        }

        return true
    }

    fun getSavedAddressbooksSyncInterval() = accountManager.getUserData(account, KEY_SYNC_INTERVAL_ADDRESSBOOKS)?.toLong()
    fun getSavedCalendarsSyncInterval() = accountManager.getUserData(account, KEY_SYNC_INTERVAL_CALENDARS)?.toLong()
    fun getSavedTasksSyncInterval() = accountManager.getUserData(account, KEY_SYNC_INTERVAL_TASKS)?.toLong()

    fun getSyncWifiOnly() =
            if (settings.containsKey(KEY_WIFI_ONLY))
                settings.getBoolean(KEY_WIFI_ONLY)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY) != null
    fun setSyncWiFiOnly(wiFiOnly: Boolean) =
            accountManager.setUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

    fun getSyncWifiOnlySSIDs(): List<String>? =
            if (getSyncWifiOnly()) {
                val strSsids = if (settings.containsKey(KEY_WIFI_ONLY_SSIDS))
                    settings.getString(KEY_WIFI_ONLY_SSIDS)
                else
                    accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS)
                strSsids?.split(',')
            } else
                null
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
            accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, StringUtils.trimToNull(ssids?.joinToString(",")))


    // CalDAV settings

    fun getTimeRangePastDays(): Int? {
        val strDays = accountManager.getUserData(account, KEY_TIME_RANGE_PAST_DAYS)
        return if (strDays != null) {
            val days = strDays.toInt()
            if (days < 0)
                null
            else
                days
        } else
            DEFAULT_TIME_RANGE_PAST_DAYS
    }

    fun setTimeRangePastDays(days: Int?) =
            accountManager.setUserData(account, KEY_TIME_RANGE_PAST_DAYS, (days ?: -1).toString())

    /**
     * Takes the default alarm setting (in this order) from
     *
     * 1. the local account settings
     * 2. the settings provider (unless the value is -1 there).
     *
     * @return A default reminder shall be created this number of minutes before the start of every
     * non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun getDefaultAlarm() =
            accountManager.getUserData(account, KEY_DEFAULT_ALARM)?.toInt() ?:
            settings.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 }

    /**
     * Sets the default alarm value in the local account settings, if the new value differs
     * from the value of the settings provider. If the new value is the same as the value of
     * the settings provider, the local setting will be deleted, so that the settings provider
     * value applies.
     *
     * @param minBefore The number of minutes a default reminder shall be created before the
     * start of every non-full-day event without reminder. *null*: No default reminders shall be created.
     */
    fun setDefaultAlarm(minBefore: Int?) =
            accountManager.setUserData(account, KEY_DEFAULT_ALARM,
                    if (minBefore == settings.getIntOrNull(KEY_DEFAULT_ALARM)?.takeIf { it != -1 })
                        null
                    else
                        minBefore?.toString())

    fun getManageCalendarColors() = if (settings.containsKey(KEY_MANAGE_CALENDAR_COLORS))
        settings.getBoolean(KEY_MANAGE_CALENDAR_COLORS)
    else
        accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null
    fun setManageCalendarColors(manage: Boolean) =
            accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")

    fun getEventColors() = if (settings.containsKey(KEY_EVENT_COLORS))
            settings.getBoolean(KEY_EVENT_COLORS)
                else
            accountManager.getUserData(account, KEY_EVENT_COLORS) != null
    fun setEventColors(useColors: Boolean) =
            accountManager.setUserData(account, KEY_EVENT_COLORS, if (useColors) "1" else null)

    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        val name = settings.getString(KEY_CONTACT_GROUP_METHOD) ?:
                accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        if (name != null)
            try {
                return GroupMethod.valueOf(name)
            }
            catch (e: IllegalArgumentException) {
            }
        return GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, method.name)
    }


    // UI settings

    /**
     * Whether only personal collections should be shown.
     *
     * @return [Pair] of values:
     *
     *   1. (first) whether only personal collections should be shown
     *   2. (second) whether the user shall be able to change the setting (= setting not locked)
     */
    fun getShowOnlyPersonal(): Pair<Boolean, Boolean> =
            when (settings.getIntOrNull(KEY_SHOW_ONLY_PERSONAL)) {
                0 -> Pair(false, false)
                1 -> Pair(true, false)
                else /* including -1 */ -> Pair(accountManager.getUserData(account, KEY_SHOW_ONLY_PERSONAL) != null, true)
            }

    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) {
        accountManager.setUserData(account, KEY_SHOW_ONLY_PERSONAL, if (showOnlyPersonal) "1" else null)
    }


    // update from previous account settings

    private fun update(baseVersion: Int) {
        for (toVersion in baseVersion+1 ..CURRENT_VERSION) {
            val fromVersion = toVersion-1
            Logger.log.info("Updating account ${account.name} from version $fromVersion to $toVersion")
            try {
                val updateProc = this::class.java.getDeclaredMethod("update_${fromVersion}_$toVersion")
                updateProc.invoke(this)

                Logger.log.info("Account version update successful")
                accountManager.setUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update account settings", e)
            }
        }
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
            val provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            if (provider != null)
                try {
                    // Attention: CalendarProvider does NOT limit the results of the ExtendedProperties query
                    // to the given account! So all extended properties will be processed number-of-accounts times.
                    val extUri = ExtendedProperties.CONTENT_URI.asSyncAdapter(account)

                    provider.query(extUri, arrayOf(
                            ExtendedProperties._ID,     // idx 0
                            ExtendedProperties.NAME,    // idx 1
                            ExtendedProperties.VALUE    // idx 2
                    ), null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val rawValue = cursor.getString(2)

                            val uri by lazy {
                                ContentUris.withAppendedId(ExtendedProperties.CONTENT_URI, id).asSyncAdapter(account)
                            }

                            when (cursor.getString(1)) {
                                UnknownProperty.CONTENT_ITEM_TYPE -> {
                                    // unknown property; check whether it's a URL
                                    try {
                                        val property = UnknownProperty.fromJsonString(rawValue)
                                        if (property is Url) {  // rewrite to MIMETYPE_URL
                                            val newValues = ContentValues(2)
                                            newValues.put(ExtendedProperties.NAME, AndroidEvent.MIMETYPE_URL)
                                            newValues.put(ExtendedProperties.VALUE, property.value)
                                            provider.update(uri, newValues, null, null)
                                        }
                                    } catch (e: Exception) {
                                        Logger.log.log(Level.WARNING, "Couldn't rewrite URL from unknown property to ${AndroidEvent.MIMETYPE_URL}", e)
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
                                                newValues.put(ExtendedProperties.NAME, UnknownProperty.CONTENT_ITEM_TYPE)
                                                newValues.put(ExtendedProperties.VALUE, UnknownProperty.toJsonString(property))
                                                provider.update(uri, newValues, null, null)
                                            }
                                        }
                                    } catch(e: Exception) {
                                        Logger.log.log(Level.WARNING, "Couldn't rewrite deprecated unknown property to current format", e)
                                    }
                                }
                                "unknown-property.v2" -> {
                                    // unknown property (deprecated MIME type); rewrite to current MIME type
                                    val newValues = ContentValues(1)
                                    newValues.put(ExtendedProperties.NAME, UnknownProperty.CONTENT_ITEM_TYPE)
                                    provider.update(uri, newValues, null, null)
                                }
                            }
                        }
                    }
                } finally {
                    provider.closeCompat()
                }
        }
    }

    @Suppress("unused","FunctionName")
    /**
     * The tasks sync interval should be stored in account settings. It's used to set the sync interval
     * again when the tasks provider is switched.
     */
    private fun update_10_11() {
        TaskUtils.currentProvider(context)?.let { provider ->
            val interval = getSyncInterval(provider.authority)
            if (interval != null)
                accountManager.setUserData(account, KEY_SYNC_INTERVAL_TASKS, interval.toString())
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
        TaskProvider.acquire(context, OpenTasks)?.use { provider ->
            val tasksUri = provider.tasksUri().asSyncAdapter(account)
            val emptyETag = ContentValues(1)
            emptyETag.putNull(LocalTask.COLUMN_ETAG)
            provider.client.update(tasksUri, emptyETag, "${TaskContract.Tasks._DIRTY}=0 AND ${TaskContract.Tasks._DELETED}=0", null)
        }

        @SuppressLint("Recycle")
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED)
            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
                provider.update(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(account),
                        AndroidCalendar.calendarBaseValues, null, null)
                provider.closeCompat()
            }
    }

    @Suppress("unused","FunctionName")
    /**
     * It seems that somehow some non-CalDAV accounts got OpenTasks syncable, which caused battery problems.
     * Disable it on those accounts for the future.
     */
    private fun update_8_9() {
        val hasCalDAV = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV) != null
        if (!hasCalDAV && ContentResolver.getIsSyncable(account, OpenTasks.authority) != 0) {
            Logger.log.info("Disabling OpenTasks sync for $account")
            ContentResolver.setIsSyncable(account, OpenTasks.authority, 0)
        }
    }

    @Suppress("unused","FunctionName")
    @SuppressLint("Recycle")
    /**
     * There is a mistake in this method. [TaskContract.Tasks.SYNC_VERSION] is used to store the
     * SEQUENCE and should not be used for the eTag.
     */
    private fun update_7_8() {
        TaskProvider.acquire(context, OpenTasks)?.use { provider ->
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
                    Logger.log.log(Level.FINER, "Updating task $id", values)
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
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
            try {
                AndroidCalendar.insertColors(provider, account)
            } finally {
                provider.closeCompat()
            }
        }

        // update allowed WiFi settings key
        val onlySSID = accountManager.getUserData(account, "wifi_only_ssid")
        accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, onlySSID)
        accountManager.setUserData(account, "wifi_only_ssid", null)
    }

    @Suppress("unused")
    @SuppressLint("Recycle", "ParcelClassLoader")
    private fun update_5_6() {
        context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { provider ->
            val parcel = Parcel.obtain()
            try {
                // don't run syncs during the migration
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)
                ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 0)
                ContentResolver.cancelSync(account, null)

                // get previous address book settings (including URL)
                val raw = ContactsContract.SyncState.get(provider, account)
                if (raw == null)
                    Logger.log.info("No contacts sync state, ignoring account")
                else {
                    parcel.unmarshall(raw, 0, raw.size)
                    parcel.setDataPosition(0)
                    val params = parcel.readBundle()!!
                    val url = params.getString("url")?.toHttpUrlOrNull()
                    if (url == null)
                        Logger.log.info("No address book URL, ignoring account")
                    else {
                        // create new address book
                        val info = Collection(url = url, type = Collection.TYPE_ADDRESSBOOK, displayName = account.name)
                        Logger.log.log(Level.INFO, "Creating new address book account", url)
                        val addressBookAccount = Account(LocalAddressBook.accountName(account, info), context.getString(R.string.account_type_address_book))
                        if (!accountManager.addAccountExplicitly(addressBookAccount, null, LocalAddressBook.initialUserData(account, info.url.toString())))
                            throw ContactsStorageException("Couldn't create address book account")

                        // move contacts to new address book
                        Logger.log.info("Moving contacts from $account to $addressBookAccount")
                        val newAccount = ContentValues(2)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_NAME, addressBookAccount.name)
                        newAccount.put(ContactsContract.RawContacts.ACCOUNT_TYPE, addressBookAccount.type)
                        val affected = provider.update(ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
                                newAccount,
                                "${ContactsContract.RawContacts.ACCOUNT_NAME}=? AND ${ContactsContract.RawContacts.ACCOUNT_TYPE}=?",
                                arrayOf(account.name, account.type))
                        Logger.log.info("$affected contacts moved to new address book")
                    }

                    ContactsContract.SyncState.set(provider, account, null)
                }
            } catch(e: RemoteException) {
                throw ContactsStorageException("Couldn't migrate contacts to new address book", e)
            } finally {
                parcel.recycle()
                provider.closeCompat()
            }
        }

        // update version number so that further syncs don't repeat the migration
        accountManager.setUserData(account, KEY_SETTINGS_VERSION, "6")

        // request sync of new address book account
        ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 1)
        setSyncInterval(context.getString(R.string.address_books_authority), 4*3600)
    }

    /* Android 7.1.1 OpenTasks fix */
    @Suppress("unused")
    private fun update_4_5() {
        // call PackageChangedReceiver which then enables/disables OpenTasks sync when it's (not) available
        SyncUtils.updateTaskSync(context)
    }

    @Suppress("unused")
    private fun update_3_4() {
        setGroupMethod(GroupMethod.CATEGORIES)
    }

    // updates from AccountSettings version 2 and below are not supported anymore

}
