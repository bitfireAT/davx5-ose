/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.*
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.TaskProvider.ProviderName.OpenTasks
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import org.dmfs.tasks.contract.TaskContract
import java.util.*
import java.util.logging.Level

/**
 * Manages settings of an account.
 *
 * @throws InvalidAccountException on construction when the account doesn't exist (anymore)
 */
class AccountSettings(
        val context: Context,
        val account: Account
) {

    companion object {

        const val CURRENT_VERSION = 9
        const val KEY_SETTINGS_VERSION = "version"

        const val KEY_USERNAME = "user_name"
        const val KEY_CERTIFICATE_ALIAS = "certificate_alias"

        const val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        const val WIFI_ONLY_DEFAULT = false
        const val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs

        /** Time range limitation to the past [in days]
        value = null            default value (DEFAULT_TIME_RANGE_PAST_DAYS)
        < 0 (-1)          no limit
        >= 0              entries more than n days in the past won't be synchronized
         */
        const val KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days"
        const val DEFAULT_TIME_RANGE_PAST_DAYS = 90

        /* Whether DAVx5 sets the local calendar color to the value from service DB at every sync
           value = null (not existing)     true (default)
                   "0"                     false */
        const val KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors"

        /* Whether DAVx5 populates and uses CalendarContract.Colors
           value = null (not existing)     false (default)
                   "1"                     true */
        const val KEY_EVENT_COLORS = "event_colors"

        /** Contact group method:
        value = null (not existing)     groups as separate VCards (default)
        "CATEGORIES"            groups are per-contact CATEGORIES
         */
        const val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        const val SYNC_INTERVAL_MANUALLY = -1L

        fun initialUserData(credentials: Credentials): Bundle {
            val bundle = Bundle(2)
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())

            when (credentials.type) {
                Credentials.Type.UsernamePassword ->
                    bundle.putString(KEY_USERNAME, credentials.userName)
                Credentials.Type.ClientCertificate ->
                    bundle.putString(KEY_CERTIFICATE_ALIAS, credentials.certificateAlias)
            }

            return bundle
        }

    }
    
    
    val accountManager: AccountManager = AccountManager.get(context)
    val settings = Settings.getInstance(context)

    init {
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

    fun setSyncInterval(authority: String, seconds: Long) {
        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false)
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true)
            ContentResolver.addPeriodicSync(account, authority, Bundle(), seconds)
        }
    }

    fun getSyncWifiOnly() = if (settings.has(KEY_WIFI_ONLY))
            settings.getBoolean(KEY_WIFI_ONLY) ?: WIFI_ONLY_DEFAULT
                else
            accountManager.getUserData(account, KEY_WIFI_ONLY) != null
    fun setSyncWiFiOnly(wiFiOnly: Boolean) =
            accountManager.setUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

    fun getSyncWifiOnlySSIDs(): List<String>? = (if (settings.has(KEY_WIFI_ONLY_SSIDS))
                settings.getString(KEY_WIFI_ONLY_SSIDS)
            else
                accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS))?.split(',')
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
            accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, StringUtils.trimToNull(ssids?.joinToString(",")))


    // CalDAV settings

    fun getTimeRangePastDays(): Int? {
        val strDays = accountManager.getUserData(account, KEY_TIME_RANGE_PAST_DAYS)
        return if (strDays != null) {
            val days = Integer.valueOf(strDays)
            if (days < 0) null else days
        } else
            DEFAULT_TIME_RANGE_PAST_DAYS
    }

    fun setTimeRangePastDays(days: Int?) =
            accountManager.setUserData(account, KEY_TIME_RANGE_PAST_DAYS, (days ?: -1).toString())

    fun getManageCalendarColors() = if (settings.has(KEY_MANAGE_CALENDAR_COLORS))
        settings.getBoolean(KEY_MANAGE_CALENDAR_COLORS) ?: false
    else
        accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null
    fun setManageCalendarColors(manage: Boolean) =
            accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")

    fun getEventColors() = if (settings.has(KEY_EVENT_COLORS))
            settings.getBoolean(KEY_EVENT_COLORS) ?: false
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


    @Suppress("unused")
    @SuppressLint("Recycle")
    /**
     * It seems that somehow some non-CalDAV accounts got OpenTasks syncable, which caused battery problems.
     * Disable it on those accounts for the future.
     */
    private fun update_8_9() {
        ServiceDB.OpenHelper(context).use { dbHelper ->
            val db = dbHelper.readableDatabase
            db.query(ServiceDB.Services._TABLE, null, "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                    arrayOf(account.name, ServiceDB.Services.SERVICE_CALDAV), null, null, null).use { result ->
                val hasCalDAV = result.count >= 1
                if (!hasCalDAV && ContentResolver.getIsSyncable(account, OpenTasks.authority) != 0) {
                    Logger.log.info("Disabling OpenTasks sync for $account")
                    ContentResolver.setIsSyncable(account, OpenTasks.authority, 0)
                }
            }
        }
    }

    @Suppress("unused")
    @SuppressLint("Recycle")
    /**
     * There is a mistake in this method. [TaskContract.Tasks.SYNC_VERSION] is used to store the
     * SEQUENCE and should not be used for the eTag.
     */
    private fun update_7_8() {
        TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)?.let { provider ->
            // ETag is now in sync_version instead of sync1
            // UID  is now in _uid         instead of sync2
            provider.client.query(TaskProvider.syncAdapterUri(provider.tasksUri(), account),
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
                            TaskProvider.syncAdapterUri(ContentUris.withAppendedId(provider.tasksUri(), id), account),
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
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 24)
                    provider.close()
                else
                    provider.release()
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
                    val url = params.getString("url")?.let { HttpUrl.parse(it) }
                    if (url == null)
                        Logger.log.info("No address book URL, ignoring account")
                    else {
                        // create new address book
                        val info = CollectionInfo(url)
                        info.type = CollectionInfo.Type.ADDRESS_BOOK
                        info.displayName = account.name
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
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= 24)
                    provider.close()
                else
                    provider.release()
            }
        }

        // update version number so that further syncs don't repeat the migration
        accountManager.setUserData(account, KEY_SETTINGS_VERSION, "6")

        // request sync of new address book account
        ContentResolver.setIsSyncable(account, context.getString(R.string.address_books_authority), 1)
        setSyncInterval(context.getString(R.string.address_books_authority), Constants.DEFAULT_SYNC_INTERVAL)
    }

    /* Android 7.1.1 OpenTasks fix */
    @Suppress("unused")
    private fun update_4_5() {
        // call PackageChangedReceiver which then enables/disables OpenTasks sync when it's (not) available
        PackageChangedReceiver.updateTaskSync(context)
    }

    @Suppress("unused")
    private fun update_3_4() {
        setGroupMethod(GroupMethod.CATEGORIES)
    }

    @Suppress("unused")
    @SuppressLint("Recycle")
    private fun update_2_3() {
        // Don't show a warning for Android updates anymore
        accountManager.setUserData(account, "last_android_version", null)

        var serviceCardDAV: Long? = null
        var serviceCalDAV: Long? = null

        ServiceDB.OpenHelper(context).use { dbHelper ->
            val db = dbHelper.writableDatabase
            // we have to create the WebDAV Service database only from the old address book, calendar and task list URLs

            // CardDAV: migrate address books
            context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.let { client ->
                try {
                    val addrBook = LocalAddressBook(context, account, client)
                    val url = addrBook.url
                    Logger.log.fine("Migrating address book $url")

                    // insert CardDAV service
                    val values = ContentValues(3)
                    values.put(Services.ACCOUNT_NAME, account.name)
                    values.put(Services.SERVICE, Services.SERVICE_CARDDAV)
                    serviceCardDAV = db.insert(Services._TABLE, null, values)

                    // insert address book
                    values.clear()
                    values.put(Collections.SERVICE_ID, serviceCardDAV)
                    values.put(Collections.URL, url)
                    values.put(Collections.SYNC, 1)
                    db.insert(Collections._TABLE, null, values)

                    // insert home set
                    HttpUrl.parse(url)?.let {
                        val homeSet = it.resolve("../")
                        values.clear()
                        values.put(HomeSets.SERVICE_ID, serviceCardDAV)
                        values.put(HomeSets.URL, homeSet.toString())
                        db.insert(HomeSets._TABLE, null, values)
                    }
                } catch (e: ContactsStorageException) {
                    Logger.log.log(Level.SEVERE, "Couldn't migrate address book", e)
                } finally {
                    if (Build.VERSION.SDK_INT >= 24)
                        client.close()
                    else
                        @Suppress("deprecation")
                        client.release()
                }
            }

            // CalDAV: migrate calendars + task lists
            val collections = HashSet<String>()
            val homeSets = HashSet<HttpUrl>()

            context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { client ->
                try {
                    val calendars = AndroidCalendar.find(account, client, LocalCalendar.Factory, null, null)
                    for (calendar in calendars)
                        calendar.name?.let { url ->
                            Logger.log.fine("Migrating calendar $url")
                            collections.add(url)
                            HttpUrl.parse(url)?.resolve("../")?.let { homeSets.add(it) }
                        }
                } catch (e: CalendarStorageException) {
                    Logger.log.log(Level.SEVERE, "Couldn't migrate calendars", e)
                } finally {
                    if (Build.VERSION.SDK_INT >= 24)
                        client.close()
                    else
                        @Suppress("deprecation")
                        client.release()
                }
            }

            AndroidTaskList.acquireTaskProvider(context)?.use { provider ->
                try {
                    val taskLists = AndroidTaskList.find(account, provider, LocalTaskList.Factory, null, null)
                    for (taskList in taskLists)
                        taskList.syncId?.let { url ->
                            Logger.log.fine("Migrating task list $url")
                            collections.add(url)
                            HttpUrl.parse(url)?.resolve("../")?.let { homeSets.add(it) }
                        }
                } catch (e: CalendarStorageException) {
                    Logger.log.log(Level.SEVERE, "Couldn't migrate task lists", e)
                }
            }

            if (!collections.isEmpty()) {
                // insert CalDAV service
                val values = ContentValues(3)
                values.put(Services.ACCOUNT_NAME, account.name)
                values.put(Services.SERVICE, Services.SERVICE_CALDAV)
                serviceCalDAV = db.insert(Services._TABLE, null, values)

                // insert collections
                for (url in collections) {
                    values.clear()
                    values.put(Collections.SERVICE_ID, serviceCalDAV)
                    values.put(Collections.URL, url)
                    values.put(Collections.SYNC, 1)
                    db.insert(Collections._TABLE, null, values)
                }

                // insert home sets
                for (homeSet in homeSets) {
                    values.clear()
                    values.put(HomeSets.SERVICE_ID, serviceCalDAV)
                    values.put(HomeSets.URL, homeSet.toString())
                    db.insert(HomeSets._TABLE, null, values)
                }
            }
        }

        // initiate service detection (refresh) to get display names, colors etc.
        val refresh = Intent(context, DavService::class.java)
        refresh.action = DavService.ACTION_REFRESH_COLLECTIONS
        serviceCardDAV?.let {
            refresh.putExtra(DavService.EXTRA_DAV_SERVICE_ID, it)
            context.startService(refresh)
        }
        serviceCalDAV?.let {
            refresh.putExtra(DavService.EXTRA_DAV_SERVICE_ID, it)
            context.startService(refresh)
        }
    }

    @Suppress("unused")
    @SuppressLint("Recycle")
    private fun update_1_2() {
        /* - KEY_ADDRESSBOOK_URL ("addressbook_url"),
           - KEY_ADDRESSBOOK_CTAG ("addressbook_ctag"),
           - KEY_ADDRESSBOOK_VCARD_VERSION ("addressbook_vcard_version") are not used anymore (now stored in ContactsContract.SyncState)
           - KEY_LAST_ANDROID_VERSION ("last_android_version") has been added
        */

        // move previous address book model to ContactsContract.SyncState
        val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY) ?:
            throw ContactsStorageException("Couldn't access Contacts provider")

        try {
            val addr = LocalAddressBook(context, account, provider)

            // until now, ContactsContract.settings.UNGROUPED_VISIBLE was not set explicitly
            val values = ContentValues()
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            addr.settings = values

            val url = accountManager.getUserData(account, "addressbook_url")
            if (!url.isNullOrEmpty())
                addr.url = url
            accountManager.setUserData(account, "addressbook_url", null)

            val cTag = accountManager.getUserData (account, "addressbook_ctag")
            if (!cTag.isNullOrEmpty())
                addr.lastSyncState = SyncState(SyncState.Type.CTAG, cTag)
            accountManager.setUserData(account, "addressbook_ctag", null)
        } finally {
            if (Build.VERSION.SDK_INT >= 24)
                provider.close()
            else
                @Suppress("deprecation")
                provider.release()
        }
    }

}
