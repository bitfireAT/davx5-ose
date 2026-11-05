/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.DavResourceFinder.Configuration.ServiceInfo
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.vcard.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

internal class LegacyAccountRepository @Inject constructor(
    private val accountManager: AccountManager,
    private val accountSettingsFactory: AccountSettingsFactory,
    private val androidAccountManager: AndroidAccountManager,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    @ApplicationContext private val context: Context,
    private val collectionRepository: Lazy<DavCollectionRepository>,
    private val homeSetRepository: DavHomeSetRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
) {

    private val legacyAccountType = context.getString(R.string.account_type)

    fun getAccountName(accountId: LegacyAccount): String {
        return accountId.androidAccount.name
    }

    fun getAllBlocking(): List<AccountId> {
        return accountManager.getAccountsByType(legacyAccountType)
            .map { androidAccount -> LegacyAccount(androidAccount) }
    }

    fun getAllFlow() = callbackFlow {
        val listener = OnAccountsUpdateListener { accounts ->
            val accountIds = accounts
                .filter { it.type == legacyAccountType }
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

    fun createBlocking(
        accountName: String,
        credentials: Credentials?,
        config: DavResourceFinder.Configuration,
        groupMethod: GroupMethod,
        preconfigurationUrl: String?,
    ): LegacyAccount? {
        val account = Account(accountName, legacyAccountType)
        val accountId = LegacyAccount(account)

        // create Android account
        val userData = AccountSettings.initialUserData(credentials, preconfigurationUrl)
        logger.log(Level.INFO, "Creating Android account {0} with initial config {1}", arrayOf(account, userData))

        if (!androidAccountManager.createAccount(account, userData, credentials?.password))
            return null

        // add entries for account to database
        logger.log(Level.INFO, "Writing account configuration to database: {0}", arrayOf(config))
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = insertService(accountName, Service.TYPE_CARDDAV, config.cardDAV)

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                val accountSettings = accountSettingsFactory.create(accountId)
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

    suspend fun delete(accountId: LegacyAccount) {
        val androidAccount = accountId.androidAccount

        try {
            // remove account directly (bypassing the authenticator, which is our own)
            accountManager.removeAccountExplicitly(androidAccount)

            // delete service from database
            serviceRepository.deleteByAccount(accountId)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't remove system account $accountId", e)
        }
    }

    suspend fun exists(accountName: String): Boolean {
        return accountName.isNotEmpty() && withContext(ioDispatcher) {
            accountManager.getAccountsByType(legacyAccountType)
                .any { it.name == accountName }
        }
    }

    private fun insertService(
        accountName: String,
        @ServiceType type: String,
        info: ServiceInfo
    ): Long {
        // insert service
        val service = Service(0, accountName, null, type, info.principal)
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
}
