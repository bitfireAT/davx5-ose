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
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.vcard4android.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import at.bitfire.davdroid.db.Account as DbAccount

/**
 * Repository for managing CalDAV/CardDAV accounts.
 *
 * Currently the authoritative data source is the Android account manager. The accounts will however be mirrored into the database.
 * In future, the authoritative data source should be the database, and the Android account manager should be updated accordingly.
 *
 * To map an account name to a system [Account], other classes should use [fromName] and not instantiate [Account] themselves.
 *
 * *Note:* This class is in no way related to local address books (address book accounts), which are managed by
 * [at.bitfire.davdroid.resource.LocalAddressBook].
 */
class AccountRepository @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val automaticSyncManager: AutomaticSyncManager,
    @ApplicationContext private val context: Context,
    private val collectionRepository: Lazy<DavCollectionRepository>,
    db: AppDatabase,
    private val homeSetRepository: Lazy<DavHomeSetRepository>,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val logger: Logger,
    private val serviceRepository: Lazy<DavServiceRepository>,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    private val accountType = context.getString(R.string.account_type)
    private val accountManager = AccountManager.get(context)

    private val dao = db.accountDao()

    /**
     * Creates a new account with discovered services and enables periodic syncs with
     * default sync interval times.
     *
     * Creates both the system account and a corresponding entry in the database.
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

        // create account in database
        mirrorToDb(account)

        // add entries for account to database
        logger.log(Level.INFO, "Writing account configuration to database", config)
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                val accountSettings = accountSettingsFactory.create(account)
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
            automaticSyncManager.updateAutomaticSync(account)

        } catch(e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return account
    }

    /**
     * Deletes an account from both the system accounts and the database.
     */
    suspend fun delete(accountName: String): Boolean {
        val account = fromName(accountName)
        // remove account directly (bypassing the authenticator, which is our own)
        return try {
            accountManager.removeAccountExplicitly(account)

            // delete address books (= address book accounts)
            serviceRepository.get().getByAccountAndType(accountName, Service.TYPE_CARDDAV)?.let { service ->
                collectionRepository.get().getByService(service.id).forEach { collection ->
                    localAddressBookStore.get().deleteByCollectionId(collection.id)
                }
            }

            // delete from database
            dao.deleteByName(accountName)
            serviceRepository.get().deleteByAccount(accountName)

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

    /**
     * Gets the account for the given collection.
     *
     * @param collection        the collection to get the account for
     *
     * @throws kotlin.IllegalArgumentException  if the collection, the service or the account can't be found
     */
    fun fromCollection(collection: Collection): Account =
        serviceRepository.get().get(collection.serviceId)?.let { service ->
            fromName(service.accountName)
        } ?: throw IllegalArgumentException("Couldn't fetch DB service or account from collection")

    /**
     * Returns the system account for the given account name.
     *
     * Also makes sure that the account is present in the database.
     */
    fun fromName(accountName: String) =
        mirrorToDb(Account(accountName, accountType))

    fun getAll(): Set<Account> = accountManager
        .getAccountsByType(accountType)
        .map { mirrorToDb(it) }
        .toSet()

    fun getAllFlow() =
        callbackFlow<List<Account>> {
            val listener = OnAccountsUpdateListener { accounts ->
                trySend(accounts.filter { it.type == accountType })
            }

            accountManager.addOnAccountsUpdatedListener(listener, null, true)

            awaitClose {
                accountManager.removeOnAccountsUpdatedListener(listener)
            }
        }.map { list ->
            // mirror accounts to DB
            for (account in list)
                mirrorToDb(account)
            list
        }.flowOn(Dispatchers.IO)

    /**
     * Deletes accounts in the database which don't have a corresponding system account.
     */
    fun removeOrphanedInDb() {
        val accounts = accountManager.getAccountsByType(accountType)
        dao.deleteExceptNames(accounts.map { it.name })
    }

    /**
     * Renames an account.
     *
     * **Not**: It is highly advised to re-sync the account after renaming in order to restore
     * a consistent state.
     *
     * @param oldName current name of the account
     * @param newName new name the account shall be re named to
     * @return the renamed account
     *
     * @throws InvalidAccountException if the account does not exist
     * @throws IllegalArgumentException if the new account name already exists
     * @throws Exception (or sub-classes) on other errors
     */
    suspend fun rename(oldName: String, newName: String): Account {
        val oldAccount = fromName(oldName)
        val newAccount = Account(newName, accountType)

        // check whether new account name already exists
        if (accountManager.getAccountsByType(accountType).contains(newAccount))
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

            // wait for operation to complete
            withContext(Dispatchers.Default) {
                // blocks calling thread
                val newNameFromApi: Account = future.result
                if (newNameFromApi.name != newName)
                    throw IllegalStateException("renameAccount returned ${newNameFromApi.name} instead of $newName")
            }

            // account renamed, cancel maybe running synchronization of old account
            syncWorkerManager.cancelAllWork(oldAccount)

            // disable periodic syncs for old account
            for (dataType in SyncDataType.entries)
                syncWorkerManager.disablePeriodic(oldAccount, dataType)

            // update account in DB (propagates account name to services over foreign key)
            dao.rename(oldName, newName)

            try {
                // update address books
                localAddressBookStore.get().updateAccount(oldAccount, newAccount)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change address books to renamed account", e)
            }

            try {
                // update calendar events
                localCalendarStore.get().updateAccount(oldAccount, newAccount)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change calendars to renamed account", e)
            }

            try {
                // update account_name of local tasks
                val dataStore = tasksAppManager.get().getDataStore()
                dataStore?.updateAccount(oldAccount, newAccount)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't change task lists to renamed account", e)
            }

            // update automatic sync
            automaticSyncManager.updateAutomaticSync(newAccount)
        } finally {
            // release AccountsCleanupWorker mutex at the end of this async coroutine
            AccountsCleanupWorker.unlockAccountsCleanup()
        }

        return newAccount
    }


    // helpers

    private fun insertService(accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
        // insert service
        val service = Service(0, accountName, type, info.principal)
        val serviceId = serviceRepository.get().insertOrReplace(service)

        // insert home sets
        for (homeSet in info.homeSets)
            homeSetRepository.get().insertOrUpdateByUrl(HomeSet(0, serviceId, true, homeSet))

        // insert collections
        for (collection in info.collections.values) {
            collection.serviceId = serviceId
            collectionRepository.get().insertOrUpdateByUrl(collection)
        }

        return serviceId
    }

    private fun mirrorToDb(account: Account): Account {
        dao.insertOrIgnore(DbAccount(name = account.name))
        return account
    }

}