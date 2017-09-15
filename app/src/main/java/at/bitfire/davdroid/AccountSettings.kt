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
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.*
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidTaskList
import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.logging.Level

class AccountSettings @Throws(InvalidAccountException::class) constructor(
        val context: Context,
        val account: Account
) {

    companion object {

        val CURRENT_VERSION = 7
        val KEY_SETTINGS_VERSION = "version"

        val KEY_USERNAME = "user_name"

        val KEY_WIFI_ONLY = "wifi_only"               // sync on WiFi only (default: false)
        val KEY_WIFI_ONLY_SSIDS = "wifi_only_ssids"   // restrict sync to specific WiFi SSIDs

        /** Time range limitation to the past [in days]
        value = null            default value (DEFAULT_TIME_RANGE_PAST_DAYS)
        < 0 (-1)          no limit
        >= 0              entries more than n days in the past won't be synchronized
         */
        val KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days"
        val DEFAULT_TIME_RANGE_PAST_DAYS = 90

        /* Whether DAVdroid sets the local calendar color to the value from service DB at every sync
           value = null (not existing)     true (default)
                   "0"                     false */
        val KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors"

        /* Whether DAVdroid populates and uses CalendarContract.Colors
           value = null (not existing)     false (default)
                   "1"                     true */
        val KEY_EVENT_COLORS = "event_colors"

        /** Contact group method:
        value = null (not existing)     groups as separate VCards (default)
        "CATEGORIES"            groups are per-contact CATEGORIES
         */
        val KEY_CONTACT_GROUP_METHOD = "contact_group_method"

        @JvmField
        val SYNC_INTERVAL_MANUALLY = -1L

        fun initialUserData(userName: String): Bundle {
            val bundle = Bundle(2)
            bundle.putString(KEY_SETTINGS_VERSION, CURRENT_VERSION.toString())
            bundle.putString(KEY_USERNAME, userName)
            return bundle
        }

    }


    val accountManager: AccountManager = AccountManager.get(context)

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

    fun username(): String? = accountManager.getUserData(account, KEY_USERNAME)
    fun username(userName: String) = accountManager.setUserData(account, KEY_USERNAME, userName)

    fun password(): String? = accountManager.getPassword(account)
    fun password(password: String) = accountManager.setPassword(account, password)


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

    fun getSyncWifiOnly() = accountManager.getUserData(account, KEY_WIFI_ONLY) != null
    fun setSyncWiFiOnly(wiFiOnly: Boolean) =
            accountManager.setUserData(account, KEY_WIFI_ONLY, if (wiFiOnly) "1" else null)

    fun getSyncWifiOnlySSIDs(): List<String>? =
            accountManager.getUserData(account, KEY_WIFI_ONLY_SSIDS)?.split(',')
    fun setSyncWifiOnlySSIDs(ssids: List<String>?) =
            accountManager.setUserData(account, KEY_WIFI_ONLY_SSIDS, StringUtils.trimToNull(ssids?.joinToString(",")))


    // CalDAV settings

    fun getTimeRangePastDays(): Int? {
        val strDays = accountManager.getUserData(account, KEY_TIME_RANGE_PAST_DAYS)
        if (strDays != null) {
            val days = Integer.valueOf(strDays)
            return if (days < 0) null else days
        } else
            return DEFAULT_TIME_RANGE_PAST_DAYS
    }

    fun setTimeRangePastDays(days: Int?) =
            accountManager.setUserData(account, KEY_TIME_RANGE_PAST_DAYS, (days ?: -1).toString())

    fun getManageCalendarColors() = accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null
    fun setManageCalendarColors(manage: Boolean) =
            accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, if (manage) null else "0")

    fun getEventColors() = accountManager.getUserData(account, KEY_EVENT_COLORS) != null
    fun setEventColors(useColors: Boolean) =
            accountManager.setUserData(account, KEY_EVENT_COLORS, if (useColors) "1" else null)

    // CardDAV settings

    fun getGroupMethod(): GroupMethod {
        BuildConfig.settingContactGroupMethod?.let { return it }

        val name = accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD)
        return if (name != null)
            GroupMethod.valueOf(name)
        else
            GroupMethod.GROUP_VCARDS
    }

    fun setGroupMethod(method: GroupMethod) {
        if (BuildConfig.settingContactGroupMethod == null) {
            val name = if (method == GroupMethod.GROUP_VCARDS) null else method.name
            accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, name)
        } else if (BuildConfig.settingContactGroupMethod != method)
            throw UnsupportedOperationException("Group method setting is read-only")
    }


    // update from previous account settings

    private fun update(baseVersion: Int) {
        for (toVersion in baseVersion+1 .. CURRENT_VERSION) {
            val fromVersion = toVersion-1
            Logger.log.info("Updating account ${account.name} from version $fromVersion to $toVersion")
            try {
                val updateProc = this::class.java.getDeclaredMethod("update_${fromVersion}_$toVersion")
                updateProc.invoke(this)
                accountManager.setUserData(account, KEY_SETTINGS_VERSION, toVersion.toString())
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update account settings", e)
            }
        }
    }

    @Suppress("unused")
    private fun update_6_7() {
        // add calendar colors
        context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
            try {
                AndroidCalendar.insertColors(provider, account)
            } finally {
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
                    val params = parcel.readBundle()
                    val url = params.getString("url")
                    if (url == null)
                        Logger.log.info("No address book URL, ignoring account")
                    else {
                        // create new address book
                        val info = CollectionInfo(url)
                        info.type = CollectionInfo.Type.ADDRESS_BOOK
                        info.displayName = account.name
                        Logger.log.log(Level.INFO, "Creating new address book account", url)
                        val addressBookAccount = Account(LocalAddressBook.accountName(account, info), context.getString(R.string.account_type_address_book))
                        if (!accountManager.addAccountExplicitly(addressBookAccount, null, LocalAddressBook.initialUserData(account, info.url)))
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
                    val url = addrBook.getURL()
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

            AndroidTaskList.acquireTaskProvider(context.contentResolver)?.use { provider ->
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
    private fun update_1_2() {
        /* - KEY_ADDRESSBOOK_URL ("addressbook_url"),
           - KEY_ADDRESSBOOK_CTAG ("addressbook_ctag"),
           - KEY_ADDRESSBOOK_VCARD_VERSION ("addressbook_vcard_version") are not used anymore (now stored in ContactsContract.SyncState)
           - KEY_LAST_ANDROID_VERSION ("last_android_version") has been added
        */

        // move previous address book info to ContactsContract.SyncState
        val provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY) ?:
            throw ContactsStorageException("Couldn't access Contacts provider")

        try {
            val addr = LocalAddressBook(context, account, provider)

            // until now, ContactsContract.Settings.UNGROUPED_VISIBLE was not set explicitly
            val values = ContentValues()
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            addr.updateSettings(values)

            val url = accountManager.getUserData(account, "addressbook_url")
            if (!url.isNullOrEmpty())
                addr.setURL(url)
            accountManager.setUserData(account, "addressbook_url", null)

            val cTag = accountManager.getUserData (account, "addressbook_ctag")
            if (!cTag.isNullOrEmpty())
                addr.setCTag(cTag)
            accountManager.setUserData(account, "addressbook_ctag", null)
        } finally {
            if (Build.VERSION.SDK_INT >= 24)
                provider.close()
            else
                @Suppress("deprecation")
                provider.release()
        }
    }


    class AppUpdatedReceiver: BroadcastReceiver() {

        @SuppressLint("UnsafeProtectedBroadcastReceiver,MissingPermission")
        override fun onReceive(context: Context, intent: Intent?) {
            Logger.log.info("DAVdroid was updated, checking for AccountSettings version")

            // peek into AccountSettings to initiate a possible migration
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(context.getString(R.string.account_type)))
                try {
                    Logger.log.info("Checking account ${account.name}")
                    AccountSettings(context, account)
                } catch(e: InvalidAccountException) {
                    Logger.log.log(Level.SEVERE, "Couldn't check for updated account settings", e)
                }
        }

    }

}
