/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.db.AccountSetting
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.AccountSettings.Companion.KEY_SETTINGS_VERSION
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.AutomaticSyncManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.vcard.GroupMethod
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

internal class DbAccountRepository @Inject constructor(
    private val accountSettingsFactory: AccountSettingsFactory,
    private val automaticSyncManager: Lazy<AutomaticSyncManager>,
    @ApplicationContext private val context: Context,
    private val collectionRepository: Lazy<DavCollectionRepository>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val homeSetRepository: DavHomeSetRepository,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    db: AppDatabase
) {

    private val dbAccountDao = db.dbAccountDao()
    private val accountSettingDao = db.accountSettingDao()

    suspend fun getAccountName(accountId: DbAccountId): String {
        return dbAccountDao.get(accountId.id)?.name
            ?: throw NoSuchElementException("No account found with id ${accountId.id}")
    }

    fun getAccountNameBlocking(accountId: DbAccountId): String {
        return dbAccountDao.getBlocking(accountId.id)?.name
            ?: throw NoSuchElementException("No account found with id ${accountId.id}")
    }

    fun getAllBlocking(): List<AccountId> {
        return dbAccountDao.getAllBlocking().map { dbAccount -> DbAccountId(dbAccount.id) }
    }

    fun getAllFlow(): Flow<List<DbAccountId>> {
        return dbAccountDao.getAllFlow().map { dbAccounts ->
            dbAccounts.map { dbAccount -> DbAccountId(dbAccount.id) }
        }
    }

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
        // Migration runs when initializing the account settings, so it's required to determine that no migration is
        // required to proceed.
        accountSettingDao.insert(
            AccountSetting(
                accountId = accountIdNumber,
                key = KEY_SETTINGS_VERSION,
                value = AccountSettings.CURRENT_VERSION.toString()
            )
        )

        // insert the initial account settings into the database
        val accountSettings = withContext(ioDispatcher) {
            accountSettingsFactory.create(accountId).also {
                it.putInitialSettings(credentials, preconfigurationUrl)
            }
        }

        // add entries for account to database
        logger.log(Level.INFO, "Writing account configuration to database: {0}", arrayOf(config))
        try {
            if (config.cardDAV != null) {
                // insert CardDAV service
                val id = withContext(ioDispatcher) {
                    insertService(accountId, Service.TYPE_CARDDAV, config.cardDAV)
                }

                // set initial CardDAV account settings and set sync intervals (enables automatic sync)
                withContext(ioDispatcher) {
                    accountSettings.setGroupMethod(groupMethod)
                }

                // start CardDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                val id = withContext(ioDispatcher) {
                    insertService(accountId, Service.TYPE_CALDAV, config.calDAV)
                }

                // start CalDAV service detection (refresh collections)
                RefreshCollectionsWorker.enqueue(context, id)
            }

            // set up automatic sync (processes inserted services)
            withContext(ioDispatcher) {
                automaticSyncManager.get().updateAutomaticSync(accountId)
            }

        } catch (e: InvalidAccountException) {
            logger.log(Level.SEVERE, "Couldn't access account settings", e)
            return null
        }
        return accountId
    }

    suspend fun delete(accountId: DbAccountId) {
        try {
            dbAccountDao.delete(accountId.id)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't remove database account $accountId", e)
        }
    }

    suspend fun exists(accountName: String): Boolean {
        return accountName.isNotEmpty() && dbAccountDao.getFromName(accountName) != null
    }

    private fun insertService(
        accountId: DbAccountId,
        @ServiceType type: String,
        info: DavResourceFinder.Configuration.ServiceInfo
    ): Long {
        // insert service
        val service = Service(0, null, accountId.id, type, info.principal)
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
