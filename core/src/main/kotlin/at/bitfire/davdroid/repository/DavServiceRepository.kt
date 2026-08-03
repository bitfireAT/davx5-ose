/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import androidx.annotation.WorkerThread
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DavServiceRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.serviceDao()


    // Read

    suspend fun get(id: Long): Service? = dao.get(id)
    fun getBlocking(id: Long): Service? = dao.getBlocking(id)

    suspend fun getAll(): List<Service> = dao.getAll()

    suspend fun getByAccountAndType(name: String, @ServiceType serviceType: String): Service? =
        dao.getByAccountAndType(name, serviceType)

    suspend fun getByAccountIdAndType(accountId: AccountId, @ServiceType serviceType: String): Service? {
        return dao.getByAccountIdAndType(accountId, serviceType)
    }

    @WorkerThread
    fun getByAccountAndTypeBlocking(name: String, @ServiceType serviceType: String): Service? =
        dao.getByAccountAndTypeBlocking(name, serviceType)

    @WorkerThread
    fun getByAccountIdAndTypeBlocking(accountId: AccountId, @ServiceType serviceType: String): Service? =
        dao.getByAccountIdAndTypeBlocking(accountId, serviceType)

    fun getCalDavServiceFlow(accountName: String) =
        dao.getByAccountAndTypeFlow(accountName, Service.TYPE_CALDAV)

    fun getCalDavServiceFlow(accountId: AccountId) =
        dao.getByAccountAndTypeFlow(accountId, Service.TYPE_CALDAV)

    fun getCardDavServiceFlow(accountId: AccountId): Flow<Service?> {
        return dao.getByAccountAndTypeFlow(accountId, Service.TYPE_CARDDAV)
    }


    // Create & update

    fun insertOrReplaceBlocking(service: Service) =
        dao.insertOrReplaceBlocking(service)

    suspend fun renameAccount(oldName: String, newName: String) =
        dao.renameAccount(oldName, newName)


    // Delete

    fun deleteAllBlocking() = dao.deleteAllBlocking()

    suspend fun deleteByAccount(accountId: AccountId) =
        dao.deleteByAccount(accountId)

}