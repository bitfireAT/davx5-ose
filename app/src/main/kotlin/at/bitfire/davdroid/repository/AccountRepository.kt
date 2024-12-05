/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.vcard4android.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Repository for managing CalDAV/CardDAV accounts.
 *
 * *Note:* This class is not related to address book accounts, which are managed by
 * [at.bitfire.davdroid.resource.LocalAddressBook].
 */
class AccountRepository @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val automaticSyncManager: AutomaticSyncManager,
    @ApplicationContext val context: Context,
    private val collectionRepository: DavCollectionRepository,
    private val homeSetRepository: DavHomeSetRepository,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val logger: Logger,
    private val settingsManager: SettingsManager,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager
) {

    private val accountType = context.getString(R.string.account_type)
    private val accountManager = AccountManager.get(context)

    /**
     * Creates a new account with discovered services and enables periodic syncs with
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
        val account = fromName(accountName)

        // create Android account
        val userData = AccountSettings.initialUserData(credentials)
        logger.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

        if (!SystemAccountUtils.createAccount(context, account, userData, credentials?.password))
            return null

        // add entries for account to service DB
        logger.log(Level.INFO, "Writing account configuration to database", config)
        try {
            val accountSettings = accountSettingsFactory.create(account)
            val defaultSyncInterval = (settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)/60).toInt()

            // Configure CardDAV service
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // initial CardDAV account settings and sync intervals (sets up automatic sync)
                accountSettings.setGroupMethod(groupMethod)
                accountSettings.setSyncInterval(SyncDataType.CONTACTS, defaultSyncInterval)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            } else
                automaticSyncManager.disable(account, SyncDataType.CONTACTS)

            // Configure CalDAV service
            if (config.calDAV != null) {
                // insert CalDAV service
                val id = insertService(accountName, Service.TYPE_CALDAV, config.calDAV)

                // set default sync interval (sets up automatic sync)
                accountSettings.setSyncInterval(SyncDataType.EVENTS, defaultSyncInterval)
                accountSettings.setSyncInterval(SyncDataType.TASKS, defaultSyncInterval)

                // start CalDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            } else {
                automaticSyncManager.disable(account, SyncDataType.EVENTS)
                automaticSyncManager.disable(account, SyncDataType.TASKS)
            }

        } catch(e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return account
    }

    suspend fun delete(accountName: String): Boolean {
        val account = fromName(accountName)
        // remove account directly (bypassing the authenticator, which is our own)
        return try {
            accountManager.removeAccountExplicitly(account)

            // delete address books (= address book accounts)
            serviceRepository.getByAccountAndType(accountName, Service.TYPE_CARDDAV)?.let { service ->
                collectionRepository.getByService(service.id).forEach { collection ->
                    localAddressBookStore.get().deleteByCollectionId(collection.id)
                }
            }

            // delete from database
            serviceRepository.deleteByAccount(accountName)

            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't remove account $accountName", e)
            false
        }
    }

    fun exists(accountName: String): Boolean =
        if (accountName.isEmpty())
            false
        else
            accountManager
                .getAccountsByType(accountType)
                .any { it.name == accountName }

    fun fromName(accountName: String) =
        Account(accountName, accountType)

    fun getAll(): Array<Account> = accountManager.getAccountsByType(accountType)

    fun getAllFlow() = callbackFlow<Set<Account>> {
        val listener = OnAccountsUpdateListener { accounts ->
            trySend(accounts.filter { it.type == accountType }.toSet())
        }
        withContext(Dispatchers.Default) {  // causes disk I/O
            accountManager.addOnAccountsUpdatedListener(listener, null, true)
        }

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }

    /**
     * Renames an account.
     *
     * **Not**: It is highly advised to re-sync the account after renaming in order to restore
     * a consistent state.
     *
     * @param oldName current name of the account
     * @param newName new name the account shall be re named to
     *
     * @throws InvalidAccountException if the account does not exist
     * @throws IllegalArgumentException if the new account name already exists
     * @throws Exception (or sub-classes) on other errors
     */
    suspend fun rename(oldName: String, newName: String) {
        val oldAccount = fromName(oldName)
        val newAccount = fromName(newName)

        // check whether new account name already exists
        if (accountManager.getAccountsByType(context.getString(R.string.account_type)).contains(newAccount))
            throw IllegalArgumentException("Account with name \"$newName\" already exists")

        // remember sync intervals
        val oldSettings = accountSettingsFactory.create(oldAccount)
        val syncIntervals = SyncDataType.entries.map { Pair(it, oldSettings.getSyncInterval(it)) }

        // rename account
        try {
            /* https://github.com/bitfireAT/davx5/issues/135
            Lock accounts cleanup so that the AccountsCleanupWorker doesn't run while we rename the account
            because this can cause problems when:
            1. The account is renamed.
            2. The AccountsCleanupWorker is called BEFORE the services table is updated.
               → AccountsCleanupWorker removes the "orphaned" services because they belong to the old account which doesn't exist anymore
            3. Now the services would be renamed, but they're not here anymore. */
            AccountsCleanupWorker.lockAccountsCleanup()

            // rename account
            val future = accountManager.renameAccount(oldAccount, newName, null, null)

            // wait for operation to complete
            withContext(Dispatchers.Default) {
                // blocks calling thread
                val newNameFromApi: Account = future.result
                if (newNameFromApi.name != newName)
                    throw IllegalStateException("renameAccount returned ${newNameFromApi.name} instead of $newName")
            }

            // account renamed, cancel maybe running synchronization of old account
            syncWorkerManager.cancelAllWork(oldAccount)

            // update account name references in database
            serviceRepository.renameAccount(oldName, newName)

            // calendar provider doesn't allow changing account_name of Events
            // (all events will have to be downloaded again at next sync)

            // update account_name of local tasks
            try {
                LocalTaskList.onRenameAccount(context, oldAccount.name, newName)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't propagate new account name to tasks provider", e)
                // Couldn't update task lists, but this is not a fatal error (will be fixed at next sync)
            }

            // restore sync intervals
            val newSettings = accountSettingsFactory.create(newAccount)
            for ((dataType, minutes) in syncIntervals)
                newSettings.setSyncInterval(dataType, minutes)

        } finally {
            // release AccountsCleanupWorker mutex at the end of this async coroutine
            AccountsCleanupWorker.unlockAccountsCleanup()
        }
    }


    // helpers

    private fun insertService(accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = serviceRepository.insertOrReplace(service)

        // insert home sets
        for (homeSet in info.homeSets)
            homeSetRepository.insertOrUpdateByUrl(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        for (collection in info.collections.values) {
            collection.serviceId = serviceId
            collectionRepository.insertOrUpdateByUrl(collection)
        }

        return serviceId
    }

}