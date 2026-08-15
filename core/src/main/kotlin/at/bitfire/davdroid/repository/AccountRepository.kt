/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.accounts.toAccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.synctools.util.AndroidAccountUtils
import at.bitfire.synctools.vcard.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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
    private val accountManager: AccountManager,
    private val accountSettingsFactory: AccountSettingsFactory,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    @ApplicationContext private val context: Context,
    private val collectionRepository: Lazy<DavCollectionRepository>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val homeSetRepository: DavHomeSetRepository,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: Lazy<SyncWorkerManager>,
    private val tasksAppManager: Lazy<TasksAppManager>,
    private val db: AppDatabase
) {

    private val accountType = context.getString(R.string.account_type)

    private val accountRenameFlow = MutableSharedFlow<AccountRename>()
    
    fun getAccountNameFlow(accountId: AccountId): Flow<String> {
        return flow {
            var currentName = getAccountName(accountId)
            emit(currentName)
            
            accountRenameFlow.collect { accountRename -> 
                if (accountRename.oldName == currentName) {
                    currentName = accountRename.newName
                    emit(currentName)
                }
            }
        }
    }

    suspend fun getAccountName(accountId: AccountId): String {
        // For now getAccountNameBlocking() isn't really blocking, so simply call through to it.
        return getAccountNameBlocking(accountId)
    }

    @WorkerThread
    fun getAccountNameBlocking(accountId: AccountId): String {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount.name
            is DbAccountId -> TODO("It's not possible yet to get the name of DbAccounts")
        }
    }
    
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
    @WorkerThread
    fun createBlocking(
        accountName: String,
        credentials: Credentials?,
        config: DavResourceFinder.Configuration,
        groupMethod: GroupMethod,
        preconfigurationUrl: String?,
    ): AccountId? {
        val account = fromName(accountName)
        val accountId = LegacyAccount(account)

        // create Android account
        val userData = AccountSettings.initialUserData(credentials, preconfigurationUrl)
        logger.log(Level.INFO, "Creating Android account {0} with initial config {1}", arrayOf(account, userData))

        if (!AndroidAccountUtils.createAccount(context, account, userData, credentials?.password))
            return null

        // add entries for account to database
        logger.log(Level.INFO, "Writing account configuration to database: {0}", arrayOf(config))
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                val accountSettings = accountSettingsFactory.create(account.toAccountId())
                accountSettings.setGroupMethod(groupMethod)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                val id = insertService(accountName, Service.TYPE_CALDAV, config.calDAV)

                // start CalDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            // set up automatic sync (processes inserted services)
            automaticSyncManager.get().updateAutomaticSync(accountId)

        } catch (e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return LegacyAccount(account)
    }

    suspend fun delete(accountId: AccountId): Boolean {
        require(accountId is LegacyAccount) { "Only LegacyAccount is supported right now" }
        val account = accountId.androidAccount
        // remove account directly (bypassing the authenticator, which is our own)
        return try {
            accountManager.removeAccountExplicitly(account)

            // delete address books (= address book accounts)
            serviceRepository.getByAccountIdAndType(accountId, Service.TYPE_CARDDAV)?.let { service ->
                collectionRepository.get().getByService(service.id).forEach { collection ->
                    localAddressBookStore.get().deleteByCollectionId(collection.id)
                }
            }

            // delete from database
            serviceRepository.deleteByAccount(accountId)

            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't remove account $accountId", e)
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

    /**
     * Returns the [AccountId] for a given account name.
     *
     * TODO: Remove this once [Service] references accounts by database ID instead of name.
     */
    @Deprecated("Only use this method when resolving which account a `at.bitfire.davdroid.db.Service` instance belongs to.")
    suspend fun getAccountIdFromName(accountName: String): AccountId {
        // Note: In the future this will have to perform a database lookup
        return LegacyAccount(fromName(accountName))
    }

    suspend fun getAll(): List<AccountId> {
        return withContext(ioDispatcher) {
            getAllBlocking()
        }
    }

    fun getAllBlocking(): List<AccountId> {
        return accountManager.getAccountsByType(accountType)
            .map { account -> LegacyAccount(account) }
    }

    fun getAllFlow() = callbackFlow<Set<AccountId>> {
        val listener = OnAccountsUpdateListener { accounts ->
            val accountIds = accounts
                .filter { it.type == accountType }
                .map { LegacyAccount(it) }
                .toSet()

            trySend(accountIds)
        }
        withContext(ioDispatcher) {  // causes disk I/O
            accountManager.addOnAccountsUpdatedListener(listener, null, true)
        }

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }.distinctUntilChanged()

    /**
     * Renames an account.
     *
     * **Note**: It is highly advised to re-sync the account after renaming in order to restore
     * a consistent state.
     *
     * @param oldName current name of the account
     * @param newName new name the account shall be re named to
     *
     * @return [LegacyAccount] wrapping the new Android account.
     *
     * @throws InvalidAccountException if the account does not exist
     * @throws IllegalArgumentException if the new account name already exists
     * @throws Exception (or sub-classes) on other errors
     */
    suspend fun rename(oldName: String, newName: String): LegacyAccount = withContext(ioDispatcher) {
        val oldAccount = fromName(oldName)
        val oldAccountId = LegacyAccount(oldAccount)
        val newAccount = fromName(newName)
        val newAccountId = LegacyAccount(newAccount)

        // check whether new account name already exists
        if (accountManager.getAccountsByType(context.getString(R.string.account_type)).contains(newAccount))
            throw IllegalArgumentException("Account with name \"$newName\" already exists")

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

            // rename account (also moves AccountSettings)
            val future = accountManager.renameAccount(oldAccount, newName, null, null)

            // wait for operation to complete (blocks calling thread)
            val newNameFromApi: Account = future.result
            if (newNameFromApi.name != newName)
                throw IllegalStateException("renameAccount returned ${newNameFromApi.name} instead of $newName")

            accountRenameFlow.emit(AccountRename(oldAccount.name, newName))
            
            // account renamed, cancel maybe running synchronization of old account
            syncWorkerManager.get().cancelAllWork(oldAccountId)

            // disable periodic syncs for old account
            for (dataType in SyncDataType.entries)
                syncWorkerManager.get().disablePeriodic(oldAccountId, dataType)

            // update account name references in database
            serviceRepository.renameAccount(oldName, newName)

            try {
                // update address books
                localAddressBookStore.get().updateAccount(oldAccount, newAccount, null)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change address books to renamed account", e)
            }

            try {
                // update calendar events
                val store = localCalendarStore.get()
                store.acquireContentProvider(true)?.use { client ->
                    store.updateAccount(oldAccount, newAccount, client)
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change calendars to renamed account", e)
            }

            try {
                // update account_name of local tasks
                val store = tasksAppManager.get().getDataStore()
                store?.acquireContentProvider(true)?.use { client ->
                    store.updateAccount(oldAccount, newAccount, client)
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change task lists to renamed account", e)
            }

            // update automatic sync
            automaticSyncManager.get().updateAutomaticSync(newAccountId)
        } finally {
            // release AccountsCleanupWorker mutex at the end of this async coroutine
            AccountsCleanupWorker.unlockAccountsCleanup()
        }

        newAccountId
    }

    suspend fun rename(accountId: AccountId, newName: String): AccountId {
        return when (accountId) {
            is LegacyAccount -> rename(accountId.androidAccount.name, newName)
            is DbAccountId -> TODO("It's not possible yet to rename DbAccounts")
        }
    }


    // helpers

    private fun insertService(
        accountName: String,
        @ServiceType type: String,
        info: DavResourceFinder.Configuration.ServiceInfo
    ): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = serviceRepository.insertOrReplaceBlocking(service)

        // insert home sets
        for (homeSet in info.homeSets)
            homeSetRepository.insertOrUpdateByUrlBlocking(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        for (collection in info.collections.values) {
            collectionRepository.get().insertOrUpdateByUrl(collection.copy(serviceId = serviceId))
        }

        return serviceId
    }

    private data class AccountRename(val oldName: String, val newName: String)
}