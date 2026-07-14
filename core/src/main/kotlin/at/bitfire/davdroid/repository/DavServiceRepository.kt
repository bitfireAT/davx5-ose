/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

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

    fun getBlocking(id: Long): Service? = dao.get(id)
    suspend fun get(id: Long): Service? = dao.getAsync(id)

    suspend fun getAll(): List<Service> = dao.getAll()

    suspend fun getByAccountAndType(name: String, @ServiceType serviceType: String): Service? =
        dao.getByAccountAndType(name, serviceType)

    suspend fun getByAccountAndType(accountId: AccountId, @ServiceType serviceType: String): Service? {
        return dao.getByAccountAndType(accountId, serviceType)
    }

    fun getCalDavServiceFlow(accountId: AccountId) =
        dao.getByAccountAndTypeFlow(accountId, Service.TYPE_CALDAV)

    fun getCardDavServiceFlow(accountId: AccountId): Flow<Service?> {
        return dao.getByAccountAndTypeFlow(accountId, Service.TYPE_CARDDAV)
    }


    // Create & update

    fun insertOrReplaceBlocking(service: Service) =
        dao.insertOrReplace(service)

    suspend fun renameAccount(oldName: String, newName: String) =
        dao.renameAccount(oldName, newName)


    // Delete

    fun deleteAllBlocking() = dao.deleteAll()

    suspend fun deleteByAccount(accountId: AccountId) =
        dao.deleteByAccount(accountId)

}