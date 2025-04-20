/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.db.ServiceType
import javax.inject.Inject

class DavServiceRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.serviceDao()


    // Read

    fun get(id: Long): Service? = dao.get(id)

    suspend fun getAll(): List<Service> = dao.getAll()

    fun getByAccountAndType(name: String, @ServiceType serviceType: String): Service? =
        dao.getByAccountAndType(name, serviceType)

    fun getCalDavServiceFlow(accountName: String) =
        dao.getByAccountAndTypeFlow(accountName, Service.TYPE_CALDAV)

    fun getCardDavServiceFlow(accountName: String) =
        dao.getByAccountAndTypeFlow(accountName, Service.TYPE_CARDDAV)


    // Create & update

    fun insertOrReplace(service: Service) =
        dao.insertOrReplace(service)

    suspend fun renameAccount(oldName: String, newName: String) =
        dao.renameAccount(oldName, newName)


    // Delete

    fun deleteAll() = dao.deleteAll()

    suspend fun deleteByAccount(accountName: String) =
        dao.deleteByAccount(accountName)

}