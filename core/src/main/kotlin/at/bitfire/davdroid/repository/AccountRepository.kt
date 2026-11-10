/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.content.Context
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.FeatureFlags
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.local.LocalAddressBookStore
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.synctools.vcard.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing CalDAV/CardDAV accounts.
 *
 * *Note:* This class is not related to address book accounts, which are managed by
 * [at.bitfire.davdroid.resource.local.LocalAddressBook].
 */
@Singleton
class AccountRepository @Inject internal constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    @ApplicationContext private val context: Context,
    private val dbAccountRepository: DbAccountRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val legacyAccountRepository: LegacyAccountRepository,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val logger: Logger,
    private val onBeforeDeleteListeners: Set<@JvmSuppressWildcards OnBeforeDeleteListener>,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: Lazy<SyncWorkerManager>,
    private val tasksAppManager: Lazy<TasksAppManager>,
    db: AppDatabase
) {

    private val legacyAccountType = context.getString(R.string.account_type)

    private val accountRenameFlow = MutableSharedFlow<AccountRename>()

    private val dbAccountDao = db.dbAccountDao()

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
            is LegacyAccount -> legacyAccountRepository.getAccountName(accountId)
            is DbAccountId -> dbAccountRepository.getAccountName(accountId)
        }
    }

    /**
     * Returns the account name for a given [AccountId].
     * @throws NoSuchElementException if the account does not exist (only for [DbAccountId])
     */
    @WorkerThread
    fun getAccountNameBlocking(accountId: AccountId): String {
        return when (accountId) {
            is LegacyAccount -> legacyAccountRepository.getAccountName(accountId)
            is DbAccountId -> dbAccountRepository.getAccountNameBlocking(accountId)
        }
    }

    /**
     * Returns the account name for the account associated with [service].
     * @return The account name associated with the service, or `null` if not found.
     */
    @WorkerThread
    fun getAccountNameBlocking(service: Service): String? {
        return service.accountId?.let(dbAccountDao::getBlocking)?.name ?: service.accountName
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
    suspend fun create(
        accountName: String,
        credentials: Credentials?,
        config: DavResourceFinder.Configuration,
        groupMethod: GroupMethod,
        preconfigurationUrl: String?,
    ): AccountId? {
        if (exists(accountName)) {
            logger.log(Level.WARNING, "Account with name $accountName already exists")
            return null
        }

        return if (FeatureFlags.useNewAccountSystem) {
            dbAccountRepository.create(accountName, credentials, config, groupMethod, preconfigurationUrl)
        } else {
            withContext(ioDispatcher) {
                legacyAccountRepository.createBlocking(accountName, credentials, config, groupMethod, preconfigurationUrl)
            }
        }
    }

    suspend fun delete(accountId: AccountId) {
        syncWorkerManager.get().cancelAllWork(accountId)

        withContext(ioDispatcher) {
            notifyOnBeforeDeleteListeners(accountId)
        }

        when (accountId) {
            is LegacyAccount -> legacyAccountRepository.delete(accountId)
            is DbAccountId -> dbAccountRepository.delete(accountId)
        }
    }

    private fun notifyOnBeforeDeleteListeners(accountId: AccountId) {
        for (listener in onBeforeDeleteListeners) {
            try {
                listener.onBeforeDelete(accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.log(
                    Level.SEVERE,
                    "Exception in OnBeforeDeleteListener implementation: ${listener::class.qualifiedName}",
                    e
                )
            }
        }
    }

    /**
     * Checks whether an account with the given name exists.
     * For legacy accounts, this checks the Android account manager, for database accounts, this checks the database.
     *
     * @param accountName the name of the account to check
     *
     * @return true if the account exists, false otherwise
     */
    suspend fun exists(accountName: String): Boolean {
        return legacyAccountRepository.exists(accountName) || dbAccountRepository.exists(accountName)
    }

    /**
     * Returns a legacy Android [Account] for a given account name.
     * Use only in tests, as this method does not check whether the named account is a legacy account or a database account.
     */
    @TestOnly
    fun legacyAccountFromName(accountName: String) = Account(accountName, legacyAccountType)

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
        return dbAccount ?: LegacyAccount(Account(accountName, legacyAccountType))
    }

    /**
     * Returns the [AccountId] for the account associated with [service].
     */
    suspend fun getAccountIdFromService(service: Service): AccountId {
        return service.accountId?.let { DbAccountId(it) } ?: getAccountIdFromName(service.accountName!!)
    }

    suspend fun getAll(): List<AccountId> {
        return withContext(ioDispatcher) {
            getAllBlocking()
        }
    }

    fun getAllBlocking(): List<AccountId> {
        return legacyAccountRepository.getAllBlocking() + dbAccountRepository.getAllBlocking()
    }

    /**
     * Returns a flow of all accounts, both legacy and database accounts.
     */
    fun getAllFlow(): Flow<Set<AccountId>> {
        return combine(
            legacyAccountRepository.getAllFlow(),
            dbAccountRepository.getAllFlow()
        ) { legacyAccounts, dbAccounts ->
            legacyAccounts + dbAccounts
        }
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
    private suspend fun renameLegacyAccount(oldName: String, oldAccountId: LegacyAccount, newName: String): AccountId = withContext(ioDispatcher) {
        val oldAccount = Account(oldName, legacyAccountType)
        val newAccount = Account(newName, legacyAccountType)
        val newAccountId: AccountId = LegacyAccount(newAccount)

        // check whether new account name already exists
        if (exists(newName))
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
            androidAccountManager.renameAccount(oldAccount, newName)

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
                store.acquireLocalStorageClient(true)?.use { client ->
                    store.updateAccount(oldAccount, newAccount, client)
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change calendars to renamed account", e)
            }

            try {
                // update account_name of local tasks
                val store = tasksAppManager.get().getDataStore()
                store?.acquireLocalStorageClient(true)?.use { client ->
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

    /**
     * Renames a database account. Only the name in the database is updated; the linked Android account
     * (and its local data) is renamed by [at.bitfire.davdroid.accounts.AndroidAccountProvider] the next
     * time it provides the account (usually during the next sync).
     *
     * @throws NoSuchElementException if the account does not exist
     * @throws IllegalArgumentException if the new account name is blank or already exists
     */
    private suspend fun renameDbAccount(accountId: DbAccountId, newName: String): AccountId = withContext(ioDispatcher) {
        val dbAccount = dbAccountDao.get(accountId.id) ?: throw NoSuchElementException("No account found with id ${accountId.id}")

        // Android can't use blank account names
        require(newName.isNotBlank()) { "Account name must not be blank" }

        // Make sure an account does not already exist with that name
        require(!exists(newName)) { "Account with name \"$newName\" already exists" }

        dbAccountDao.rename(accountId.id, newName)
        accountRenameFlow.emit(AccountRename(dbAccount.name, newName))

        accountId
    }

    suspend fun rename(accountId: AccountId, newName: String): AccountId {
        return when (accountId) {
            is LegacyAccount -> renameLegacyAccount(accountId.androidAccount.name, accountId, newName)
            is DbAccountId -> renameDbAccount(accountId, newName)
        }
    }


    // helpers

    private data class AccountRename(val oldName: String, val newName: String)
}
