/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import javax.inject.Inject

class DavServiceRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.serviceDao()

    fun get(id: Long): Service? = dao.get(id)

    fun deleteAll() = dao.deleteAll()

    suspend fun deleteByAccount(accountName: String) {
        dao.deleteByAccount(accountName)
    }

    fun getCalDavServiceFlow(accountName: String) =
        dao.getByAccountAndTypeFlow(accountName, Service.TYPE_CALDAV)

    fun getCardDavServiceFlow(accountName: String) =
        dao.getByAccountAndTypeFlow(accountName, Service.TYPE_CARDDAV)

    fun insertOrReplace(service: Service) =
        dao.insertOrReplace(service)

    suspend fun renameAccount(oldName: String, newName: String) {
        dao.renameAccount(oldName, newName)
    }

    fun getByAccountAndType(name: String, serviceType: String): Service? =
        dao.getByAccountAndType(name, serviceType)

}