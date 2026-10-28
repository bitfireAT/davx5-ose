/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Provides a ready-to-use Android [Account] for an [AccountId]. For DB accounts, the linked Android account
 * is created (or an orphaned one with the same name is adopted) when necessary.
 */
class AndroidAccountProvider @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
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

        androidAccountManager.findLinkedAccount(accountId.id)?.let { return it }

        val sameName = androidAccountManager.findAccountByName(dbAccount.name)
        val sameNameLinkedId = sameName?.let { androidAccountManager.getLinkedDbAccountId(it) }
        val account = when {
            sameName == null -> {
                logger.info("Creating Android account for $accountId")
                androidAccountManager.createLinkedAccount(dbAccount.name, accountId.id)
                    ?: throw InvalidAccountException(accountId)
            }

            // name is taken by an Android account that belongs to another (existing) DB account
            sameNameLinkedId != null && dbAccountDao.get(sameNameLinkedId) != null -> {
                logger.warning("Android account $sameName is linked to another DB account")
                throw InvalidAccountException(accountId)
            }

            // orphaned (not linked or linked to a DB account that doesn't exist anymore) → adopt
            else -> {
                logger.info("Linking orphaned Android account $sameName to $accountId")
                androidAccountManager.linkAccount(sameName, accountId.id)
                sameName
            }
        }

        // new/adopted Android account doesn't have the current sync framework settings yet
        automaticSyncManager.get().updateAutomaticSync(accountId)

        return account
    }


    companion object {

        private val provideMutex = Mutex()

    }

}
