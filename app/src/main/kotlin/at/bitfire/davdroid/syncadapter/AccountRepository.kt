/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.ContentResolver
import android.provider.CalendarContract
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.vcard4android.GroupMethod
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.logging.Level
import javax.inject.Inject

/**
 * Repository for managing CalDAV/CardDAV accounts.
 *
 * *Note:* This class is not related to address book accounts, which are managed by
 * [at.bitfire.davdroid.resource.LocalAddressBook].
 */
class AccountRepository @Inject constructor(
    val context: Application,
    val db: AppDatabase,
    val settingsManager: SettingsManager
) {

    val accountType = context.getString(R.string.account_type)
    val accountManager = AccountManager.get(context)

    /**
     * Creates a new main account with discovered services and enables periodic syncs with
     * default sync interval times.
     *
     * @param accountName   name of the account
     * @param credentials   server credentials
     * @param config        discovered server capabilities for syncable authorities
     * @param groupMethod   whether CardDAV contact groups are separate VCards or as contact categories
     *
     * @return account if account creation was successful; null otherwise (for instance because an account with this name already exists)
     */
    fun create(accountName: String, credentials: Credentials?, config: DavResourceFinder.Configuration, groupMethod: GroupMethod): Account? {
        val account = Account(accountName, context.getString(R.string.account_type))

        // create Android account
        val userData = AccountSettings.initialUserData(credentials)
        Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

        if (!AccountUtils.createAccount(context, account, userData, credentials?.password))
            return null

        // add entries for account to service DB
        Logger.log.log(Level.INFO, "Writing account configuration to database", config)
        try {
            val accountSettings = AccountSettings(context, account)
            val defaultSyncInterval = settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)

            // Configure CardDAV service
            val addrBookAuthority = context.getString(R.string.address_books_authority)
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // initial CardDAV account settings
                accountSettings.setGroupMethod(groupMethod)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)

                // set default sync interval and enable sync regardless of permissions
                ContentResolver.setIsSyncable(account, addrBookAuthority, 1)
                accountSettings.setSyncInterval(addrBookAuthority, defaultSyncInterval)
            } else
                ContentResolver.setIsSyncable(account, addrBookAuthority, 0)

            // Configure CalDAV service
            if (config.calDAV != null) {
                // insert CalDAV service
                val id = insertService(accountName, Service.TYPE_CALDAV, config.calDAV)

                // start CalDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)

                // set default sync interval and enable sync regardless of permissions
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
                accountSettings.setSyncInterval(CalendarContract.AUTHORITY, defaultSyncInterval)

                // if task provider present, set task sync interval and enable sync
                val taskProvider = TaskUtils.currentProvider(context)
                if (taskProvider != null) {
                    ContentResolver.setIsSyncable(account, taskProvider.authority, 1)
                    accountSettings.setSyncInterval(taskProvider.authority, defaultSyncInterval)
                    // further changes will be handled by TasksWatcher on app start or when tasks app is (un)installed
                    Logger.log.info("Tasks provider ${taskProvider.authority} found. Tasks sync enabled.")
                } else
                    Logger.log.info("No tasks provider found. Did not enable tasks sync.")
            } else
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)

        } catch(e: InvalidAccountException) {
            Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return account
    }

    private fun insertService(accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = db.serviceDao().insertOrReplace(service)

        // insert home sets
        val homeSetDao = db.homeSetDao()
        for (homeSet in info.homeSets)
            homeSetDao.insertOrUpdateByUrl(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        val collectionDao = db.collectionDao()
        for (collection in info.collections.values) {
            collection.serviceId = serviceId
            collectionDao.insertOrUpdateByUrl(collection)
        }

        return serviceId
    }


    fun exists(accountName: String): Boolean =
        if (accountName.isEmpty())
            false
        else
            accountManager
                .getAccountsByType(accountType)
                .contains(Account(accountName, accountType))


    fun getAllFlow() = callbackFlow<Set<Account>> {
        val listener = OnAccountsUpdateListener { accounts ->
            trySend(accounts.filter { it.type == accountType }.toSet())
        }
        accountManager.addOnAccountsUpdatedListener(listener, null, true)

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }

}