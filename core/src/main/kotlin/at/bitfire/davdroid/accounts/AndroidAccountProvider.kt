/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import at.bitfire.davdroid.accounts.AndroidAccountProvider.Companion.provideMutex
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.local.AddressBookAccountManager
import at.bitfire.davdroid.resource.local.LocalCalendarStore
import at.bitfire.davdroid.sync.SyncFrameworkSettingsUpdater
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.AccountOperationFailedException
import at.bitfire.davdroid.sync.account.InvalidAccountException
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Provides a ready-to-use Android [Account] for an [AccountId]. For DB accounts, the linked Android account
 * is created when necessary (replacing an orphaned one with the same name) and renamed when the name of the
 * DB account has changed.
 */
class AndroidAccountProvider @Inject constructor(
    private val addressBookAccountManager: AddressBookAccountManager,
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
     * Calls are serialized, so only one account is provided/renamed at a time.
     *
     * @return the Android account (for legacy accounts, the account itself)
     *
     * @throws InvalidAccountException if the DB account doesn't exist, its name is used by an Android account of
     * another DB account, or the Android account can't be created
     * @throws AccountOperationFailedException if the account could not be provided for any other reason
     * (for instance because the linked Android account couldn't be renamed, or its new name is still used by
     * the Android account of another DB account that can't be renamed out of the way)
     */
    suspend fun provide(accountId: AccountId): Account =
        when (accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId -> provideForDbAccount(accountId)
        }

    private suspend fun provideForDbAccount(accountId: DbAccountId): Account = withContext(ioDispatcher) {
        // serialize so that parallel syncs of the same account don't try to create the Android account twice
        provideMutex.withLock {
            try {
                provideForDbAccountLocked(accountId)
            } catch (e: Exception) {
                // only AccountOperationFailedException (and subclasses) may leave this class; cancellation must pass
                when (e) {
                    is CancellationException,
                    is AccountOperationFailedException -> throw e
                    else -> throw AccountOperationFailedException("Couldn't provide Android account for $accountId", e)
                }
            }
        }
    }

    /**
     * Does the actual work of [provide] for DB accounts. Must be called while holding [provideMutex].
     *
     * - If the DB account has a linked Android account, it's renamed if its name differs from the DB account name.
     * - Otherwise, a new linked Android account is created (replacing an orphaned one with the same name).
     *
     * @throws InvalidAccountException see [provide]
     * @throws AccountOperationFailedException see [provide]
     * @throws Exception unexpected exceptions are wrapped by [provideForDbAccount]
     */
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
            if (sameNameLinkedId != null) {
                addressBookAccountManager.deleteByAccountId(DbAccountId(sameNameLinkedId))
            }
            androidAccountManager.removeAccount(sameName)
        }

        logger.info("Creating Android account for $accountId")
        val account = androidAccountManager.createLinkedAccount(dbAccount.name, accountId.id)
            ?: throw InvalidAccountException(accountId)

        // new Android account doesn't have the current sync framework settings yet
        updateSyncFrameworkSettings(accountId)

        return account
    }

    /**
     * Renames the Android account [account] (linked to [accountId]) to [newName] and updates the
     * account name of the local calendars and tasks. Address book accounts reference the DB account by ID,
     * so they don't have to be updated.
     *
     * If the new name is still used by the Android account of another DB account that was renamed in the database
     * but not on the system yet, that account is renamed first. Rename cycles (for instance swapped names) are
     * resolved by moving one account to a temporary name. If the new name is used by an orphaned Android account,
     * that account is removed.
     *
     * Must be called while holding [provideMutex].
     *
     * @param accountId     DB account the Android account is linked to
     * @param account       current Android account
     * @param newName       name the Android account shall get
     * @param inProgress    IDs of the DB accounts whose rename is already in progress further up the call stack
     *                      (used to detect rename cycles)
     *
     * @return the renamed Android account
     *
     * @throws AccountOperationFailedException if the Android account could not be renamed, or the new name is used
     * by an Android account of another DB account whose DB name is the same as its Android account name
     * (so it can't be renamed out of the way)
     *
     * Failures of updating local calendars/tasks and the sync framework settings are logged and ignored.
     */
    private suspend fun rename(
        accountId: DbAccountId,
        account: Account,
        newName: String,
        inProgress: Set<Long> = emptySet()
    ): Account {
        // Find an account with the desired name
        val sameName = androidAccountManager.findAccountByName(newName)
        if (sameName != null) {
            // If it exists, check whether it's linked to another existing db account
            val sameNameLinkedId = androidAccountManager.getLinkedDbAccountId(sameName)
            val otherDbAccount = sameNameLinkedId
                ?.takeIf { it != accountId.id }
                ?.let { dbAccountDao.get(it) }

            if (otherDbAccount != null) {
                // Edge case: DB account names are unique, but if DB accounts were renamed without any provide in
                // the middle, the Android accounts are not renamed yet, so the name may still be in use there.
                if (sameName.name == otherDbAccount.name)
                    throw AccountOperationFailedException("Can't rename $account to $newName because the name is already in use by another DB account.")

                val otherId = DbAccountId(otherDbAccount.id)
                if (otherDbAccount.id in inProgress) {
                    // Rename cycle (for instance two accounts that swapped their names): the other account is
                    // already being renamed further up in the call stack and is waiting for our name.
                    // Move it to a temporary name to break the cycle; the outer call will give it its final name.
                    val tempName = "${otherDbAccount.name}-renaming-${UUID.randomUUID()}"
                    logger.info("Rename cycle detected, temporarily renaming $sameName to $tempName")
                    rename(otherId, sameName, tempName, inProgress + accountId.id)
                } else {
                    // Free up the name by renaming the other account to its current DB name first
                    rename(otherId, sameName, otherDbAccount.name, inProgress + accountId.id)
                }
            } else {
                // If it's not linked to an existing db account, but the system account does actually exist, that means that
                // it has been left orphaned, so we can just remove it, and replace it with our new account.
                logger.info("Removing orphaned Android account $sameName")
                androidAccountManager.removeAccount(sameName)
            }
        }

        // A nested call (cycle breaking) may have renamed our own Android account in the meantime
        val account = androidAccountManager.findLinkedAccount(accountId.id) ?: account

        logger.info("Renaming Android account $account to $newName")
        val newAccount = try {
            // user data (including the link to the DB account) is kept
            // renameAccount already verifies that the name was changed correctly (new account name matches newName)
            androidAccountManager.renameAccount(account, newName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is AccountOperationFailedException) throw e
            throw AccountOperationFailedException("Could not rename Android account: $account", e)
        }

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
        updateSyncFrameworkSettings(accountId)

        return newAccount
    }

    /**
     * Updates the sync framework settings of the Android account of [accountId]. Failures are only logged,
     * because the settings can be derived again later and mustn't make an otherwise finished
     * creation/rename fail.
     */
    private fun updateSyncFrameworkSettings(accountId: DbAccountId) {
        try {
            syncFrameworkSettingsUpdater.get().update(accountId)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't update sync framework settings for $accountId", e)
        }
    }


    companion object {

        private val provideMutex = Mutex()

    }

}
