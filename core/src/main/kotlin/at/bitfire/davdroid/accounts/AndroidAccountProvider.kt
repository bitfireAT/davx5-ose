/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.sync.SyncFrameworkSettingsUpdater
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Provides a ready-to-use Android [Account] for an [AccountId]. For DB accounts, the linked Android account
 * is created when necessary (replacing an orphaned one with the same name) and renamed when the name of the
 * DB account has changed.
 */
class AndroidAccountProvider @Inject constructor(
    private val accountManager: AccountManager,
    private val androidAccountManager: AndroidAccountManager,
    db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val logger: Logger,
    private val syncFrameworkSettingsUpdater: Lazy<SyncFrameworkSettingsUpdater>,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    private val dbAccountDao = db.dbAccountDao()

    /**
     * Returns the Android account for [accountId], creating/linking it for DB accounts if needed.
     *
     * @throws InvalidAccountException if the DB account doesn't exist or no Android account can be provided
     */
    suspend fun provide(accountId: AccountId): Account =
        when (accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId -> provideForDbAccount(accountId)
        }

    private suspend fun provideForDbAccount(accountId: DbAccountId): Account = withContext(ioDispatcher) {
        // serialize so that parallel syncs of the same account don't try to create the Android account twice
        provideMutex.withLock {
            provideForDbAccountLocked(accountId)
        }
    }

    private suspend fun provideForDbAccountLocked(accountId: DbAccountId): Account {
        val dbAccount = dbAccountDao.get(accountId.id) ?: throw InvalidAccountException(accountId)

        androidAccountManager.findLinkedAccount(accountId.id)?.let { linked ->
            return if (linked.name != dbAccount.name)
                rename(accountId, linked, dbAccount.name)
            else
                linked
        }

        val sameName = androidAccountManager.findAccountByName(dbAccount.name)
        if (sameName != null) {
            val sameNameLinkedId = androidAccountManager.getLinkedDbAccountId(sameName)
            if (sameNameLinkedId != null && dbAccountDao.get(sameNameLinkedId) != null) {
                // name is taken by an Android account that belongs to another (existing) DB account
                logger.warning("Android account $sameName is linked to another DB account")
                throw InvalidAccountException(accountId)
            }

            // orphaned (not linked or linked to a DB account that doesn't exist anymore), may have unrelated local data
            logger.info("Removing orphaned Android account $sameName")
            androidAccountManager.removeAccount(sameName)
        }

        logger.info("Creating Android account for $accountId")
        val account = androidAccountManager.createLinkedAccount(dbAccount.name, accountId.id)
            ?: throw InvalidAccountException(accountId)

        // new Android account doesn't have the current sync framework settings yet
        syncFrameworkSettingsUpdater.get().update(accountId)

        return account
    }

    /**
     * Renames the Android account [account] (linked to [accountId]) to [newName] and updates the
     * account name of the local calendars and tasks. Address book accounts reference the DB account by ID,
     * so they don't have to be updated.
     *
     * @throws InvalidAccountException if the new name is taken by an Android account of another (existing) DB account
     */
    private suspend fun rename(accountId: DbAccountId, account: Account, newName: String): Account {
        val sameName = androidAccountManager.findAccountByName(newName)
        if (sameName != null) {
            val sameNameLinkedId = androidAccountManager.getLinkedDbAccountId(sameName)
            if (sameNameLinkedId != null && sameNameLinkedId != accountId.id && dbAccountDao.get(sameNameLinkedId) != null) {
                logger.warning("Can't rename $account to $newName: name is used by Android account linked to another DB account")
                throw InvalidAccountException(accountId)
            }

            // orphaned (not linked or linked to a DB account that doesn't exist anymore)
            logger.info("Removing orphaned Android account $sameName")
            androidAccountManager.removeAccount(sameName)
        }

        logger.info("Renaming Android account $account to $newName")
        val newAccount = try {
            // user data (including the link to the DB account) is kept
            accountManager.renameAccount(account, newName, null, null).result
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't rename Android account $account", e)
            throw InvalidAccountException(accountId)
        }
        // Make sure the account was renamed correctly
        if (newAccount.name != newName)
            throw InvalidAccountException(accountId)

        try {
            val store = localCalendarStore.get()
            store.acquireLocalStorageClient(true)?.use { client ->
                store.updateAccount(account, newAccount, client)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't change calendars to renamed account", e)
        }

        try {
            val store = tasksAppManager.get().getDataStore()
            store?.acquireLocalStorageClient(true)?.use { client ->
                store.updateAccount(account, newAccount, client)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't change task lists to renamed account", e)
        }

        // renamed Android account doesn't have the current sync framework settings anymore
        syncFrameworkSettingsUpdater.get().update(accountId)

        return newAccount
    }


    companion object {

        private val provideMutex = Mutex()

    }

}
