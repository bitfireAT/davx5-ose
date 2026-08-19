/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.AccountSetting
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.local.LocalAddressBookStore
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SETTINGS_VERSION
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
import kotlinx.coroutines.flow.combine
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
    db: AppDatabase
) {

    private val accountType = context.getString(R.string.account_type)

    private val accountRenameFlow = MutableSharedFlow<AccountRename>()

    private val dbAccountDao = db.dbAccountDao()

    private val accountSettingDao = db.accountSettingDao()
    
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

    /**
     * Returns the account name for a given [AccountId].
     * @throws NoSuchElementException if the account does not exist (only for [DbAccountId])
     */
    suspend fun getAccountName(accountId: AccountId): String {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount.name
            is DbAccountId -> dbAccountDao.get(accountId.id)?.name ?: throw NoSuchElementException("No account found with id ${accountId.id}")
        }
    }

    /**
     * Returns the account name for a given [AccountId].
     * @throws NoSuchElementException if the account does not exist (only for [DbAccountId])
     */
    @WorkerThread
    fun getAccountNameBlocking(accountId: AccountId): String {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount.name
            is DbAccountId -> dbAccountDao.getBlocking(accountId.id)?.name ?: throw NoSuchElementException("No account found with id ${accountId.id}")
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
    suspend fun create(
        accountName: String,
        credentials: Credentials?,
        config: DavResourceFinder.Configuration,
        groupMethod: GroupMethod,
        preconfigurationUrl: String?,
    ): AccountId? {
        // create the account into the database
        val accountIdNumber = try {
            dbAccountDao.insert(DbAccount(name = accountName))
        } catch (e: SQLiteConstraintException) {
            // account with this name already exists, show a warning and return null
            logger.log(Level.WARNING, "Account with name $accountName already exists", e)
            return null
        }
        val accountId = DbAccountId(accountIdNumber)

        // Insert the account settings version into the database.
        // Migration runs when initializing the account settings, so it's required to determine that no migration is required to proceed.
        accountSettingDao.insertBlocking(
            AccountSetting(accountId = accountIdNumber, key = KEY_SETTINGS_VERSION, value = AccountSettings.CURRENT_VERSION.toString())
        )

        // insert the initial account settings into the database
        val accountSettings = accountSettingsFactory.create(accountId)
        accountSettings.putInitialSettings(credentials, preconfigurationUrl)

        val account = fromName(accountName)

        // create Android account - extract the initial settings from the database and pass them to the Android account creation API
        val userData = AccountSettings.initialUserData(credentials, preconfigurationUrl)
        logger.log(Level.INFO, "Creating Android account {0} with initial config {1}", arrayOf(account, userData))

        if (!AndroidAccountUtils.createAccount(context, account, userData, credentials?.password)) {
            logger.log(Level.WARNING, "Failed to create Android account {0}", arrayOf(account))

            // If the system account creation fails, we need to clean up the database account that was just created.
            dbAccountDao.delete(accountIdNumber)

            return null
        }

        // add entries for account to database
        logger.log(Level.INFO, "Writing account configuration to database: {0}", arrayOf(config))
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountId, accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                accountSettings.setGroupMethod(groupMethod)

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                val id = insertService(accountId, accountName, Service.TYPE_CALDAV, config.calDAV)

                // start CalDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            // set up automatic sync (processes inserted services)
            automaticSyncManager.get().updateAutomaticSync(accountId)

        } catch (e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return accountId
    }

    suspend fun delete(accountId: AccountId): Boolean {
        val account = when(accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId -> {
                val dbAccount = dbAccountDao.get(accountId.id) ?: return false
                fromName(dbAccount.name)
            }
        }
        // remove account directly (bypassing the authenticator, which is our own)
        return try {
            accountManager.removeAccountExplicitly(account)

            // delete address books (= address book accounts)
            serviceRepository.getByAccountIdAndType(accountId, Service.TYPE_CARDDAV)?.let { service ->
                collectionRepository.get().getByService(service.id).forEach { collection ->
                    localAddressBookStore.get().deleteByCollectionId(collection.id)
                }
            }

            // delete service from database
            serviceRepository.deleteByAccount(accountId)

            if (accountId is DbAccountId) {
                // delete account from database if it is a DbAccountId
                dbAccountDao.delete(accountId.id)
            }

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
        // Try to find the account in the database first, if it's not there, we assume it was created before the change
        // to database account creation
        val dbAccount = dbAccountDao.getFromName(accountName)?.let { DbAccountId(it.id) }
        return dbAccount ?: LegacyAccount(fromName(accountName))
    }

    suspend fun getAll(): List<AccountId> {
        return withContext(ioDispatcher) {
            getAllBlocking()
        }
    }

    fun getAllBlocking(): List<AccountId> {
        val dbAccounts = dbAccountDao.getAllBlocking()
        val systemAccounts = accountManager.getAccountsByType(accountType).map { LegacyAccount(it) }
        val dbAccountNames = dbAccounts.map { it.name }.toSet()
        return systemAccounts.filter { it.androidAccount.name !in dbAccountNames } + dbAccounts.map { DbAccountId(it.id) }
    }

    fun getAllLegacyAccountFlow() = callbackFlow {
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

    fun getAllDbAccountFlow() = dbAccountDao.getAllFlow()

    /**
     * Returns a flow of all accounts, both legacy and database accounts.
     */
    fun getAllFlow() = combine(getAllLegacyAccountFlow(), getAllDbAccountFlow()) { legacyAccounts, dbAccounts ->
        val dbAccountsNames = dbAccounts.map { it.name }.toSet()
        legacyAccounts.filter { it.androidAccount.name !in dbAccountsNames } + dbAccounts.map { DbAccountId(it.id) }
    }

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
    suspend fun rename(oldName: String, oldAccountId: AccountId, newName: String): AccountId = withContext(ioDispatcher) {
        val oldAccount = fromName(oldName)
        val newAccount = fromName(newName)
        val newAccountId: AccountId = when (oldAccountId) {
            is LegacyAccount -> LegacyAccount(newAccount)
            // DbAccountId only contains an id and not the name, so we can just return the oldAccountId since the id does not change
            is DbAccountId -> oldAccountId
        }

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

            // rename account (also moves AccountSettings if the account is a LegacyAccount)
            val future = accountManager.renameAccount(oldAccount, newName, null, null)

            // wait for operation to complete (blocks calling thread)
            val newNameFromApi: Account = future.result
            if (newNameFromApi.name != newName)
                throw IllegalStateException("renameAccount returned ${newNameFromApi.name} instead of $newName")

            if (oldAccountId is DbAccountId) {
                // update account name in database after renaming the Android account
                dbAccountDao.rename(oldAccountId.id, newName)
            }

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
            is LegacyAccount -> rename(accountId.androidAccount.name, accountId, newName)
            is DbAccountId -> {
                val dbAccount = dbAccountDao.get(accountId.id) ?: throw NoSuchElementException("No account found with id ${accountId.id}")
                rename(dbAccount.name, accountId, newName)
            }
        }
    }


    // helpers

    private fun insertService(
        accountId: AccountId,
        accountName: String,
        @ServiceType type: String,
        info: DavResourceFinder.Configuration.ServiceInfo
    ): Long {
        // insert service
        val service = when(accountId) {
            is LegacyAccount -> Service(0, accountName, null, type, info.principal)
            is DbAccountId -> Service(0, accountName, accountId.id, type, info.principal)
        }
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